import { useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { ApiError } from '../../api/client.js'
import { useApiClient } from '../../api/useApiClient.js'
import { useAuth } from '../../context/AuthContext.jsx'
import { useProfile } from '../../context/ProfileContext.jsx'

export default function ProfileSelectPage() {
  const { isClient } = useAuth()
  const { selectProfile } = useProfile()
  const api = useApiClient()
  const navigate = useNavigate()

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
    selectProfile({ profileId: p.profileId, name: p.name })
    navigate('/')
  }

  if (loading) return <p>Cargando perfiles…</p>
  if (error) return <p className="error-text">{error}</p>

  return (
    <section>
      <h1>¿Quién está viendo?</h1>
      {profiles.length === 0 ? (
        <p>
          No tienes perfiles activos. <Link to="/profiles">Crear uno</Link>.
        </p>
      ) : (
        <div className="grid">
          {profiles.map((p) => (
            <button
              key={p.profileId}
              type="button"
              className="card profile-card"
              onClick={() => handleSelect(p)}
            >
              <h2>{p.name}</h2>
            </button>
          ))}
        </div>
      )}
      <p>
        <Link to="/profiles">Administrar perfiles</Link>
      </p>
    </section>
  )
}
