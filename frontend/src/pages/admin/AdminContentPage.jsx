import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useApiClient } from '../../api/useApiClient.js'
import Icon from '../../components/Icon.jsx'
import Notice from '../../components/Notice.jsx'
import { STATUS_LABEL, TYPE_LABEL, ratingLabel } from '../../lib/format.js'

const PAGE_SIZE = 20

/** RF24, RF60-RF63: every content in any status, filtered and paginated by the server. */
export default function AdminContentPage() {
  const api = useApiClient()
  const [params, setParams] = useSearchParams()
  const [state, setState] = useState({ loading: true })
  const [busyId, setBusyId] = useState(null)
  const [message, setMessage] = useState(null)
  const [reload, setReload] = useState(0)

  const status = params.get('status') ?? ''
  const type = params.get('type') ?? ''
  const page = Number(params.get('page') ?? 0)

  useEffect(() => {
    let active = true
    setState((s) => ({ ...s, loading: true }))
    api
      .get('/api/admin/content', { query: { status, type, page, size: PAGE_SIZE } })
      .then((data) => active && setState({ loading: false, data }))
      .catch((error) => active && setState({ loading: false, error }))
    return () => {
      active = false
    }
  }, [api, status, type, page, reload])

  function update(changes) {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([key, value]) => (value === '' ? next.delete(key) : next.set(key, String(value))))
    if (!('page' in changes)) next.delete('page')
    setParams(next)
  }

  async function changeStatus(item, action) {
    setBusyId(item.id)
    setMessage(null)
    try {
      await api.post(`/api/admin/content/${item.id}/${action}`)
      setMessage({
        tone: 'success',
        text:
          action === 'publish'
            ? `«${item.title}» está publicado: ya aparece en el catálogo de los clientes.`
            : `«${item.title}» fue retirado: no admite reproducciones nuevas, sigue en Mi lista como no disponible y conserva su historial.`,
      })
      setReload((n) => n + 1)
    } catch (error) {
      setMessage({ tone: 'error', text: error.message })
    } finally {
      setBusyId(null)
    }
  }

  const totalPages = state.data ? Math.max(1, Math.ceil(state.data.totalItems / state.data.size)) : 1

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Catálogo</h1>
          <p>Nada se borra: un contenido retirado conserva su id y sus referencias, y puede volver a publicarse.</p>
        </div>
        <Link to="/admin/content/new" className="button">
          <Icon name="plus" /> Nuevo contenido
        </Link>
      </div>

      <div className="toolbar">
        <label className="field">
          <span className="hint">Estado</span>
          <select value={status} onChange={(e) => update({ status: e.target.value })}>
            <option value="">Todos</option>
            {Object.entries(STATUS_LABEL).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span className="hint">Tipo</span>
          <select value={type} onChange={(e) => update({ type: e.target.value })}>
            <option value="">Todos</option>
            {Object.entries(TYPE_LABEL).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <span className="spacer" />
        {state.data && (
          <span className="muted" aria-live="polite">
            {state.data.totalItems} contenidos
          </span>
        )}
      </div>

      {message && (
        <div style={{ marginBottom: 'var(--s-4)' }}>
          <Notice tone={message.tone}>{message.text}</Notice>
        </div>
      )}

      {state.error ? (
        <Notice tone="error">{state.error.message}</Notice>
      ) : (
        <div className="table-wrap" style={{ opacity: state.loading ? 0.6 : 1 }}>
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Id</th>
                <th scope="col">Título</th>
                <th scope="col">Tipo</th>
                <th scope="col" className="num">
                  Año
                </th>
                <th scope="col">Clasif.</th>
                <th scope="col">Estado</th>
                <th scope="col">
                  <span className="visually-hidden">Acciones</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {state.data?.items.map((item) => (
                <tr key={item.id}>
                  <td className="mono">{item.id}</td>
                  <td className="title-cell">
                    <Link to={`/admin/content/${item.id}`} style={{ color: 'var(--ink)', fontWeight: 600 }}>
                      {item.title}
                    </Link>
                    <div className="muted" style={{ fontSize: 'var(--text-xs)' }}>
                      {item.genres?.join(' · ')}
                    </div>
                  </td>
                  <td>{TYPE_LABEL[item.type]}</td>
                  <td className="num">{item.releaseYear}</td>
                  <td>
                    <span className="badge rating">{ratingLabel(item.ageRating)}</span>
                  </td>
                  <td>
                    <span className={`badge ${item.status.toLowerCase()}`}>{STATUS_LABEL[item.status]}</span>
                  </td>
                  <td>
                    <div className="row-actions">
                      <Link to={`/admin/content/${item.id}`} className="button ghost small" aria-label={`Editar ${item.title}`}>
                        <Icon name="edit" /> Editar
                      </Link>
                      {item.status === 'PUBLISHED' ? (
                        <button
                          type="button"
                          className="button danger small"
                          disabled={busyId === item.id}
                          onClick={() => changeStatus(item, 'withdraw')}
                        >
                          Retirar
                        </button>
                      ) : (
                        <button
                          type="button"
                          className="button secondary small"
                          disabled={busyId === item.id}
                          onClick={() => changeStatus(item, 'publish')}
                        >
                          {item.status === 'WITHDRAWN' ? 'Volver a publicar' : 'Publicar'}
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
              {state.data?.items.length === 0 && (
                <tr>
                  <td colSpan={7} className="muted" style={{ textAlign: 'center', padding: 'var(--s-6)' }}>
                    No hay contenidos con estos filtros.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {totalPages > 1 && (
        <nav className="pagination" aria-label="Páginas">
          <button type="button" className="button secondary" disabled={page <= 0} onClick={() => update({ page: page - 1 })}>
            <Icon name="back" /> Anterior
          </button>
          <span>
            Página {page + 1} de {totalPages}
          </span>
          <button
            type="button"
            className="button secondary"
            disabled={page + 1 >= totalPages}
            onClick={() => update({ page: page + 1 })}
          >
            Siguiente <Icon name="next" />
          </button>
        </nav>
      )}
    </>
  )
}
