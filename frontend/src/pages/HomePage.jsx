import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { ApiError } from '../api/client.js'
import { useApiClient } from '../api/useApiClient.js'
import Notice from '../components/Notice.jsx'
import Poster from '../components/Poster.jsx'
import Icon from '../components/Icon.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { useProfile } from '../context/ProfileContext.jsx'
import { TYPE_LABEL, episodeLabel, formatClock, ratingLabel } from '../lib/format.js'
import { watchPath } from './player/watchPath.js'

export default function HomePage() {
  const { isClient, isAdmin } = useAuth()
  const { profile } = useProfile()

  if (isAdmin) return <Navigate to="/admin/content" replace />
  if (!isClient) return <Welcome />
  if (!profile) return <Navigate to="/select-profile" replace />
  return <ProfileHome profile={profile} />
}

function Welcome() {
  return (
    <section className="auth-layout">
      <div className="auth-copy">
        <h1>Cine que se queda contigo.</h1>
        <p className="lede">
          Películas, series y documentales seleccionados, con perfiles para cada persona de la casa y la
          clasificación que tú decides para cada uno.
        </p>
        <div className="title-actions">
          <Link to="/register" className="button large">
            Crear cuenta
          </Link>
          <Link to="/plans" className="button secondary large">
            Ver planes
          </Link>
        </div>
      </div>
      <div className="card">
        <h2>¿Ya tienes cuenta?</h2>
        <p className="muted">Entra, elige quién está viendo y sigue donde lo dejaste.</p>
        <Link to="/login" className="button">
          Entrar
        </Link>
      </div>
    </section>
  )
}

function ProfileHome({ profile }) {
  const api = useApiClient()
  const [rows, setRows] = useState({ loading: true })

  useEffect(() => {
    let active = true
    Promise.allSettled([
      api.get('/api/continue-watching'),
      api.get('/api/my-list'),
      api.get('/api/catalog', { query: { size: 12 } }),
    ]).then(([continuing, myList, latest]) => {
      if (!active) return
      setRows({
        loading: false,
        continuing: continuing.status === 'fulfilled' ? continuing.value : [],
        myList: myList.status === 'fulfilled' ? myList.value : [],
        latest: latest.status === 'fulfilled' ? latest.value.items : [],
        // The catalog is the one that needs a current subscription: surface why it failed
        catalogError: latest.status === 'rejected' ? latest.reason : null,
      })
    })
    return () => {
      active = false
    }
  }, [api])

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Hola, {profile.name}</h1>
          <p>
            Este perfil ve contenidos hasta <strong>{ratingLabel(profile.maxAgeRating) || 'su clasificación'}</strong>.
          </p>
        </div>
        <Link to="/catalog" className="button">
          <Icon name="search" /> Explorar el catálogo
        </Link>
      </div>

      {rows.catalogError && <CatalogBlocked error={rows.catalogError} />}

      <section className="section" aria-labelledby="continue-title">
        <div className="section-head">
          <h2 id="continue-title">Continuar viendo</h2>
        </div>
        {rows.loading ? (
          <RailSkeleton />
        ) : rows.continuing.length === 0 ? (
          <p className="muted">
            Cuando empieces algo y lo dejes a medias, aparecerá aquí con el minuto exacto en el que te quedaste.
          </p>
        ) : (
          <div className="rail fade-in">
            {rows.continuing.map((item) => (
              <Link key={item.contentId} to={watchPath(item.contentId, item.episodeId, item.positionSeconds)} className="title-card">
                <Poster id={item.contentId} title={item.title} type={item.type} coverUrl={item.coverUrl} />
                <h3>{item.title}</h3>
                <span className="meta-line">
                  {item.episodeId ? episodeLabel(item.episodeId) : TYPE_LABEL[item.type]}
                  <span>
                    {item.positionSeconds > 0 ? `Desde ${formatClock(item.positionSeconds)}` : 'Desde el inicio'}
                  </span>
                </span>
              </Link>
            ))}
          </div>
        )}
      </section>

      <section className="section" aria-labelledby="list-title">
        <div className="section-head">
          <h2 id="list-title">Mi lista</h2>
          <Link to="/my-list">Ver toda</Link>
        </div>
        {rows.loading ? (
          <RailSkeleton />
        ) : rows.myList.length === 0 ? (
          <p className="muted">Guarda títulos desde su ficha con «Añadir a Mi lista» para verlos más tarde.</p>
        ) : (
          <div className="rail fade-in">
            {rows.myList.map((item) => (
              <Link key={item.contentId} to={`/title/${item.contentId}`} className="title-card">
                <Poster id={item.contentId} title={item.title} type={item.type} coverUrl={item.coverUrl} unavailable={!item.available}>
                  {!item.available && <span className="badge withdrawn poster-flag">No disponible</span>}
                </Poster>
                <h3>{item.title}</h3>
              </Link>
            ))}
          </div>
        )}
      </section>

      {!rows.catalogError && (
        <section className="section" aria-labelledby="latest-title">
          <div className="section-head">
            <h2 id="latest-title">Estrenos recientes</h2>
            <Link to="/catalog">Todo el catálogo</Link>
          </div>
          {rows.loading ? (
            <RailSkeleton />
          ) : (
            <div className="rail fade-in">
              {rows.latest.map((item) => (
                <Link key={item.id} to={`/title/${item.id}`} className="title-card">
                  <Poster id={item.id} title={item.title} type={item.type} year={item.releaseYear} coverUrl={item.coverUrl} />
                  <h3>{item.title}</h3>
                  <span className="meta-line">
                    {TYPE_LABEL[item.type]} <span>{item.releaseYear}</span>
                  </span>
                </Link>
              ))}
            </div>
          )}
        </section>
      )}
    </>
  )
}

/** RF32/RF40: without a current subscription the catalog and playback are refused. */
export function CatalogBlocked({ error }) {
  const needsSubscription = error instanceof ApiError && error.status === 422
  return (
    <Notice
      tone="warning"
      title={needsSubscription ? 'Tu suscripción no está vigente' : 'No se pudo cargar el catálogo'}
      actions={
        needsSubscription && (
          <Link to="/plans" className="button small">
            Ver planes
          </Link>
        )
      }
    >
      {needsSubscription
        ? 'Para explorar y reproducir el catálogo necesitas una suscripción activa. Tu historial, tus perfiles y Mi lista se conservan.'
        : error?.message}
    </Notice>
  )
}

function RailSkeleton() {
  return (
    <div className="rail" aria-hidden="true">
      {Array.from({ length: 6 }, (_, i) => (
        <div key={i}>
          <div className="poster skeleton" />
          <div className="skeleton line" style={{ width: '80%' }} />
        </div>
      ))}
    </div>
  )
}
