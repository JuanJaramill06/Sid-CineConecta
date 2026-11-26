import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client.js'
import { useApiClient } from '../../api/useApiClient.js'
import Icon from '../../components/Icon.jsx'
import Notice from '../../components/Notice.jsx'
import Poster from '../../components/Poster.jsx'
import { useProfile } from '../../context/ProfileContext.jsx'
import { TYPE_LABEL, formatDuration, languageLabel, ratingLabel } from '../../lib/format.js'
import { CatalogBlocked } from '../HomePage.jsx'
import { watchPath } from '../player/watchPath.js'

/** RF35: a series opens with its seasons and episodes in one response. */
export default function TitlePage() {
  const { id } = useParams()
  const api = useApiClient()
  const { profile } = useProfile()
  const navigate = useNavigate()

  const [state, setState] = useState({ loading: true })
  const [inList, setInList] = useState(false)
  const [listBusy, setListBusy] = useState(false)
  const [listMessage, setListMessage] = useState(null)
  const [season, setSeason] = useState(0)

  useEffect(() => {
    let active = true
    setState({ loading: true })
    setSeason(0)
    Promise.all([api.get(`/api/catalog/${id}`), api.get('/api/my-list').catch(() => [])])
      .then(([content, list]) => {
        if (!active) return
        setState({ loading: false, content })
        setInList(list.some((item) => item.contentId === id))
      })
      .catch((error) => active && setState({ loading: false, error }))
    return () => {
      active = false
    }
  }, [api, id])

  async function toggleList() {
    setListBusy(true)
    setListMessage(null)
    try {
      if (inList) {
        await api.delete(`/api/my-list/${id}`)
        setInList(false)
      } else {
        await api.post('/api/my-list', { contentId: id })
        setInList(true)
      }
    } catch (error) {
      // 409: it was already there (another tab added it); the list is in sync anyway
      if (error instanceof ApiError && error.status === 409) setInList(true)
      else setListMessage(error.message)
    } finally {
      setListBusy(false)
    }
  }

  if (state.loading) return <DetailSkeleton />

  if (state.error) {
    if (state.error instanceof ApiError && state.error.status === 403) {
      return (
        <Notice
          tone="warning"
          title="Este título no está disponible para este perfil"
          actions={
            <Link to="/catalog" className="button small secondary">
              Volver al catálogo
            </Link>
          }
        >
          {`No está publicado o su clasificación supera la de «${profile.name}» (hasta ${ratingLabel(profile.maxAgeRating)}). Si es un perfil infantil, cambia a un perfil con una clasificación mayor.`}
        </Notice>
      )
    }
    return <CatalogBlocked error={state.error} />
  }

  const { content } = state
  const details = content.movie ?? content.documentary
  const seasons = content.series?.seasons ?? []
  const firstEpisode = seasons[0]?.episodes?.[0]
  const current = seasons[season]

  return (
    <article className="fade-in">
      <button type="button" className="button ghost small" onClick={() => navigate(-1)} style={{ marginBottom: 'var(--s-4)' }}>
        <Icon name="back" /> Volver
      </button>

      <div className="title-hero">
        <Poster id={content.id} title={content.title} type={content.type} year={content.releaseYear} coverUrl={content.coverUrl} />

        <div>
          <p className="meta-line" style={{ marginBottom: 'var(--s-2)' }}>
            <span>{TYPE_LABEL[content.type]}</span>
            <span>{content.releaseYear}</span>
            <span className="badge rating" title="Clasificación por edad">
              {ratingLabel(content.ageRating)}
            </span>
            {details && <span>{formatDuration(details.durationSeconds)}</span>}
            {seasons.length > 0 && (
              <span>
                {seasons.length} {seasons.length === 1 ? 'temporada' : 'temporadas'}
              </span>
            )}
          </p>
          <h1>{content.title}</h1>
          <p className="meta-line">{content.genres.join(' · ')}</p>

          <p className="synopsis">{content.synopsis}</p>

          <div className="title-actions">
            {content.type === 'SERIES' ? (
              firstEpisode && (
                <Link to={watchPath(content.id, firstEpisode.id)} className="button large">
                  <Icon name="play" /> Ver T{seasons[0].seasonNumber} · E{firstEpisode.number}
                </Link>
              )
            ) : (
              <Link to={watchPath(content.id)} className="button large">
                <Icon name="play" /> Reproducir
              </Link>
            )}
            <button type="button" className="button secondary large" onClick={toggleList} disabled={listBusy} aria-pressed={inList}>
              <Icon name={inList ? 'check' : 'plus'} />
              {inList ? 'En Mi lista' : 'Añadir a Mi lista'}
            </button>
          </div>
          {listMessage && <p className="error-text">{listMessage}</p>}

          <dl className="facts">
            {details?.directors?.length > 0 && (
              <>
                <dt>Dirección</dt>
                <dd>{details.directors.join(', ')}</dd>
              </>
            )}
            {content.movie?.cast?.length > 0 && (
              <>
                <dt>Reparto</dt>
                <dd>
                  {content.movie.cast
                    .map((member) => (member.character ? `${member.actor} (${member.character})` : member.actor))
                    .join(', ')}
                </dd>
              </>
            )}
            {content.documentary && (
              <>
                <dt>Temas</dt>
                <dd>{content.documentary.topics.join(', ')}</dd>
                <dt>Entrevistas</dt>
                <dd>{content.documentary.interviewees.join(', ')}</dd>
                {content.documentary.sources?.length > 0 && (
                  <>
                    <dt>Fuentes</dt>
                    <dd>{content.documentary.sources.join(', ')}</dd>
                  </>
                )}
              </>
            )}
            <dt>Producción</dt>
            <dd>{content.productionCountries.join(', ')}</dd>
            <dt>Audio</dt>
            <dd>{content.audioLanguages.map(languageLabel).join(', ')}</dd>
            <dt>Subtítulos</dt>
            <dd>{content.subtitleLanguages.length > 0 ? content.subtitleLanguages.map(languageLabel).join(', ') : 'Sin subtítulos'}</dd>
          </dl>
        </div>
      </div>

      {seasons.length > 0 && (
        <section className="section" aria-labelledby="episodes-title">
          <div className="section-head">
            <h2 id="episodes-title">Episodios</h2>
          </div>
          <div className="season-tabs" role="tablist" aria-label="Temporadas">
            {seasons.map((s, index) => (
              <button
                key={s.seasonNumber}
                type="button"
                role="tab"
                id={`season-tab-${index}`}
                aria-selected={index === season}
                aria-controls="season-panel"
                onClick={() => setSeason(index)}
              >
                Temporada {s.seasonNumber}
              </button>
            ))}
          </div>
          <div id="season-panel" role="tabpanel" aria-labelledby={`season-tab-${season}`}>
            {current.description && <p className="muted">{current.description}</p>}
            <ol className="episode-list">
              {current.episodes.map((episode) => (
                <li key={episode.id} className="episode">
                  <span className="episode-number">{episode.number}</span>
                  <div>
                    <h3>{episode.title}</h3>
                    <p>{episode.synopsis}</p>
                  </div>
                  <div className="row-actions">
                    <span className="muted" style={{ fontSize: 'var(--text-sm)' }}>
                      {formatDuration(episode.durationSeconds)}
                    </span>
                    <Link to={watchPath(content.id, episode.id)} className="button secondary small" aria-label={`Reproducir episodio ${episode.number}: ${episode.title}`}>
                      <Icon name="play" /> Ver
                    </Link>
                  </div>
                </li>
              ))}
            </ol>
          </div>
        </section>
      )}
    </article>
  )
}

function DetailSkeleton() {
  return (
    <div className="title-hero" aria-hidden="true">
      <div className="poster skeleton" />
      <div>
        <div className="skeleton line" style={{ width: '30%' }} />
        <div className="skeleton" style={{ height: '3rem', width: '70%', marginTop: 'var(--s-4)' }} />
        <div className="skeleton line" style={{ width: '90%', marginTop: 'var(--s-6)' }} />
        <div className="skeleton line" style={{ width: '80%' }} />
        <div className="skeleton line" style={{ width: '60%' }} />
      </div>
    </div>
  )
}
