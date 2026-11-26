import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { ApiError } from '../../api/client.js'
import { useApiClient } from '../../api/useApiClient.js'
import { useAuth } from '../../context/AuthContext.jsx'
import { useProfile } from '../../context/ProfileContext.jsx'

const emptyForm = { name: '', maxAgeRating: 18 }

export default function ProfilesPage() {
  const { isClient } = useAuth()
  const { profile: activeProfile, selectProfile } = useProfile()
  const api = useApiClient()

  const [profiles, setProfiles] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(null)

  // null = no form open; 'new' = creating; a profileId = editing that profile
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState(emptyForm)
  const [formError, setFormError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  async function load() {
    setLoading(true)
    try {
      setProfiles(await api.get('/api/profiles'))
    } catch (err) {
      setLoadError(err instanceof ApiError ? err.message : 'No se pudieron cargar los perfiles')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (isClient) load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isClient])

  if (!isClient) {
    return <Navigate to="/login" replace />
  }

  function startCreate() {
    setForm(emptyForm)
    setFormError(null)
    setEditingId('new')
  }

  function startEdit(p) {
    setForm({ name: p.name, maxAgeRating: p.maxAgeRating })
    setFormError(null)
    setEditingId(p.profileId)
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setSubmitting(true)
    setFormError(null)
    try {
      const payload = { name: form.name, maxAgeRating: Number(form.maxAgeRating) }
      if (editingId === 'new') {
        await api.post('/api/profiles', payload)
      } else {
        await api.put(`/api/profiles/${editingId}`, payload)
      }
      setEditingId(null)
      await load()
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : 'No se pudo guardar el perfil')
    } finally {
      setSubmitting(false)
    }
  }

  // RF11: deactivating never deletes; RF9/RF10 (free slot) are enforced by the backend.
  async function toggleActive(p) {
    setLoadError(null)
    try {
      const action = p.active ? 'deactivate' : 'activate'
      const updated = await api.post(`/api/profiles/${p.profileId}/${action}`)
      setProfiles((current) => current.map((item) => (item.profileId === updated.profileId ? updated : item)))
      if (!updated.active && activeProfile?.profileId === updated.profileId) {
        selectProfile(null)
      }
    } catch (err) {
      setLoadError(err instanceof ApiError ? err.message : 'No se pudo cambiar el estado del perfil')
    }
  }

  if (loading) return <p>Cargando perfiles…</p>

  return (
    <section>
      <h1>Mis perfiles</h1>
      {loadError && <p className="error-text">{loadError}</p>}

      <div className="grid">
        {profiles.map((p) => (
          <article key={p.profileId} className="card">
            <h2>{p.name}</h2>
            <p>Clasificación máxima: {p.maxAgeRating}</p>
            <p>{p.active ? 'Activo' : 'Desactivado'}</p>
            <button type="button" className="button secondary" onClick={() => startEdit(p)}>
              Editar
            </button>{' '}
            <button type="button" className={p.active ? 'button danger' : 'button'} onClick={() => toggleActive(p)}>
              {p.active ? 'Desactivar' : 'Activar'}
            </button>
          </article>
        ))}

        {editingId === null && (
          <article className="card">
            <button type="button" className="button" onClick={startCreate}>
              + Nuevo perfil
            </button>
          </article>
        )}
      </div>

      {editingId !== null && (
        <form className="form" onSubmit={handleSubmit}>
          <h2>{editingId === 'new' ? 'Nuevo perfil' : 'Editar perfil'}</h2>
          <label>
            Nombre
            <input
              type="text"
              value={form.name}
              onChange={(event) => setForm((f) => ({ ...f, name: event.target.value }))}
              maxLength={60}
              required
            />
          </label>
          <label>
            Clasificación máxima (0-18)
            <input
              type="number"
              min={0}
              max={18}
              value={form.maxAgeRating}
              onChange={(event) => setForm((f) => ({ ...f, maxAgeRating: event.target.value }))}
              required
            />
          </label>
          {formError && <p className="error-text">{formError}</p>}
          <button type="submit" className="button" disabled={submitting}>
            {submitting ? 'Guardando…' : 'Guardar'}
          </button>{' '}
          <button type="button" className="button secondary" onClick={() => setEditingId(null)}>
            Cancelar
          </button>
        </form>
      )}
    </section>
  )
}
