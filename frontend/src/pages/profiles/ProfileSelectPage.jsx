import { useEffect, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../../api/client.js'
import { useApiClient } from '../../api/useApiClient.js'
import Notice from '../../components/Notice.jsx'
import { useAuth } from '../../context/AuthContext.jsx'
import { useProfile } from '../../context/ProfileContext.jsx'
import { initials, ratingLabel } from '../../lib/format.js'

export default function ProfileSelectPage() {
  const { isClient } = useAuth()
  const { profile: current, selectProfile } = useProfile()
  const api = useApiClient()
  const navigate = useNavigate()
  const location = useLocation()

  const [profiles, setProfiles] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!isClient) return
    api
      .get('/api/profiles')
      .then((data) => setProfiles(data.filter((p) => p.active))) // RF12: only active profiles
      .catch((err) => setError(err instanceof ApiError ? err.message : 'No se pudieron cargar los perfiles'))
      .finally(() => setLoading(false))
  }, [api, isClient])

  if (!isClient) {
    return <Navigate to="/login" replace />
  }

  function handleSelect(p) {
    // maxAgeRating is kept only to explain refusals; the server re-checks it on every request
    selectProfile({ profileId: p.profileId, name: p.name, maxAgeRating: p.maxAgeRating })
    navigate(location.state?.from?.pathname ?? '/')
  }

  return (
    <section style={{ maxWidth: '56rem', margin: '0 auto', textAlign: 'center' }}>
      <h1>¿Quién está viendo?</h1>
      <p className="muted">Cada perfil tiene su propia lista, su progreso y su clasificación máxima.</p>

      {error && <Notice tone="error">{error}</Notice>}

      {loading ? (
        <p className="muted">Cargando perfiles…</p>
      ) : profiles.length === 0 ? (
        <div className="empty" style={{ marginTop: 'var(--s-6)' }}>
          <h2>Aún no hay perfiles activos</h2>
          <p>Crea el primero. Para tener perfiles necesitas una suscripción vigente.</p>
          <Link to="/profiles" className="button">
            Crear un perfil
          </Link>
        </div>
      ) : (
        <div className="grid fade-in" style={{ marginTop: 'var(--s-6)', gridTemplateColumns: 'repeat(auto-fit, minmax(10rem, 12rem))', justifyContent: 'center' }}>
          {profiles.map((p) => (
            <button
              key={p.profileId}
              type="button"
              className="profile-card"
              aria-current={current?.profileId === p.profileId ? 'true' : undefined}
              onClick={() => handleSelect(p)}
            >
              <span className="avatar" aria-hidden="true">
                {initials(p.name)}
              </span>
              <h2>{p.name}</h2>
              <span className="badge rating">Hasta {ratingLabel(p.maxAgeRating)}</span>
            </button>
          ))}
        </div>
      )}
      <p style={{ marginTop: 'var(--s-6)' }}>
        <Link to="/profiles">Administrar perfiles</Link>
      </p>
    </section>
  )
}
