import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useApiClient } from '../../api/useApiClient.js'
import Icon from '../../components/Icon.jsx'
import Notice from '../../components/Notice.jsx'
import Poster from '../../components/Poster.jsx'
import { useProfile } from '../../context/ProfileContext.jsx'
import { TYPE_LABEL, formatDate } from '../../lib/format.js'

/** RF36-RF39: kept between sessions; a withdrawn title stays, marked as no longer available. */
export default function MyListPage() {
  const api = useApiClient()
  const { profile } = useProfile()
  const [state, setState] = useState({ loading: true, items: [] })
  const [removing, setRemoving] = useState(null)

  useEffect(() => {
    let active = true
    api
      .get('/api/my-list')
      .then((items) => active && setState({ loading: false, items }))
      .catch((error) => active && setState({ loading: false, items: [], error }))
    return () => {
      active = false
    }
  }, [api])

  async function remove(contentId) {
    setRemoving(contentId)
    try {
      await api.delete(`/api/my-list/${contentId}`)
      setState((s) => ({ ...s, items: s.items.filter((item) => item.contentId !== contentId) }))
    } catch (error) {
      setState((s) => ({ ...s, error }))
    } finally {
      setRemoving(null)
    }
  }

  const unavailable = state.items.filter((item) => !item.available).length

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Mi lista</h1>
          <p>
            Lo que {profile.name} guardó para ver después.
            {unavailable > 0 && ` ${unavailable} ${unavailable === 1 ? 'título ya no está' : 'títulos ya no están'} en el catálogo.`}
          </p>
        </div>
      </div>

      {state.error && <Notice tone="error">{state.error.message}</Notice>}

      {state.loading ? (
        <p className="muted">Cargando tu lista…</p>
      ) : state.items.length === 0 ? (
        <div className="empty">
          <h2>Tu lista está vacía</h2>
          <p>Desde la ficha de cualquier título, pulsa «Añadir a Mi lista». Se guarda para este perfil, aunque cierres sesión.</p>
          <Link to="/catalog" className="button">
            Explorar el catálogo
          </Link>
        </div>
      ) : (
        <ul style={{ listStyle: 'none', margin: 0, padding: 0 }} className="fade-in">
          {state.items.map((item) => (
            <li key={item.contentId} className="list-item">
              <Poster id={item.contentId} title={item.title} type={item.type} coverUrl={item.coverUrl} unavailable={!item.available} />
              <div>
                <h3>
                  {item.available ? <Link to={`/title/${item.contentId}`}>{item.title}</Link> : <span>{item.title}</span>}
                </h3>
                <p className="meta-line" style={{ margin: 0 }}>
                  <span>{TYPE_LABEL[item.type]}</span>
                  <span>Añadido el {formatDate(item.addedAt)}</span>
                  {!item.available && <span className="badge withdrawn">Ya no está disponible</span>}
                </p>
              </div>
              <button
                type="button"
                className="button ghost small"
                onClick={() => remove(item.contentId)}
                disabled={removing === item.contentId}
                aria-label={`Quitar ${item.title} de Mi lista`}
              >
                <Icon name="close" /> Quitar
              </button>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
