import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useApiClient } from '../../api/useApiClient.js'
import Icon from '../../components/Icon.jsx'
import Notice from '../../components/Notice.jsx'
import Poster from '../../components/Poster.jsx'
import { useProfile } from '../../context/ProfileContext.jsx'
import { GENRES, LANGUAGES, TYPE_LABEL, TYPE_LABEL_PLURAL, ratingLabel } from '../../lib/format.js'
import { CatalogBlocked } from '../HomePage.jsx'

const PAGE_SIZE = 24
const TYPES = ['MOVIE', 'SERIES', 'DOCUMENTARY']

/**
 * RF32-RF34: search by title, filter by type, genre, language and year, paginated. The server
 * only returns PUBLISHED content within the profile's rating; the filters live in the URL so
 * back/forward and sharing a search work.
 */
export default function CatalogPage() {
  const api = useApiClient()
  const { profile } = useProfile()
  const [params, setParams] = useSearchParams()
  const [state, setState] = useState({ loading: true })

  const filters = {
    title: params.get('title') ?? '',
    type: params.get('type') ?? '',
    genre: params.get('genre') ?? '',
    language: params.get('language') ?? '',
    year: params.get('year') ?? '',
    page: Number(params.get('page') ?? 0),
  }
  const [draftTitle, setDraftTitle] = useState(filters.title)
  const [draftYear, setDraftYear] = useState(filters.year)
  const queryKey = params.toString()

  useEffect(() => {
    let active = true
    setState((s) => ({ ...s, loading: true }))
    const query = Object.fromEntries(new URLSearchParams(queryKey))
    api
      .get('/api/catalog', { query: { ...query, size: PAGE_SIZE } })
      .then((data) => active && setState({ loading: false, data }))
      .catch((error) => active && setState({ loading: false, error }))
    return () => {
      active = false
    }
  }, [api, queryKey])

  function update(changes) {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([key, value]) => {
      if (value === '' || value === null || value === undefined) next.delete(key)
      else next.set(key, String(value))
    })
    if (!('page' in changes)) next.delete('page') // a new filter starts again at page 1
    setParams(next)
  }

  function submitSearch(event) {
    event.preventDefault()
    update({ title: draftTitle.trim(), year: /^\d{4}$/.test(draftYear) ? draftYear : '' })
  }

  function clearFilters() {
    setDraftTitle('')
    setDraftYear('')
    setParams(new URLSearchParams())
  }

  const hasFilters = Boolean(filters.title || filters.type || filters.genre || filters.language || filters.year)
  const totalPages = state.data ? Math.max(1, Math.ceil(state.data.totalItems / state.data.size)) : 1

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Catálogo</h1>
          <p>
            Lo que <strong>{profile.name}</strong> puede ver: contenidos publicados hasta {ratingLabel(profile.maxAgeRating)}.
          </p>
        </div>
        <div className="segmented" role="group" aria-label="Tipo de contenido">
          <button type="button" aria-pressed={filters.type === ''} onClick={() => update({ type: '' })}>
            Todo
          </button>
          {TYPES.map((type) => (
            <button key={type} type="button" aria-pressed={filters.type === type} onClick={() => update({ type })}>
              {TYPE_LABEL_PLURAL[type]}
            </button>
          ))}
        </div>
      </div>

      <form className="filters" role="search" onSubmit={submitSearch}>
        <label className="field search">
          Título
          <input
            type="search"
            value={draftTitle}
            placeholder="Busca por palabras del título, p. ej. «noche»"
            onChange={(event) => setDraftTitle(event.target.value)}
          />
        </label>
        <label className="field">
          Género
          <select value={filters.genre} onChange={(event) => update({ genre: event.target.value })}>
            <option value="">Cualquiera</option>
            {GENRES.map((genre) => (
              <option key={genre} value={genre}>
                {genre}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Idioma de audio
          <select value={filters.language} onChange={(event) => update({ language: event.target.value })}>
            <option value="">Cualquiera</option>
            {Object.entries(LANGUAGES).map(([code, label]) => (
              <option key={code} value={code}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Año
          <input
            type="number"
            inputMode="numeric"
            min="1888"
            max="2100"
            value={draftYear}
            placeholder="Cualquiera"
            onChange={(event) => setDraftYear(event.target.value)}
          />
        </label>
        <button type="submit" className="button">
          <Icon name="search" /> Buscar
        </button>
      </form>

      <div className="result-bar">
        <p className="muted" aria-live="polite" style={{ margin: 0 }}>
          {state.loading
            ? 'Buscando…'
            : state.data
              ? `${state.data.totalItems} ${state.data.totalItems === 1 ? 'título' : 'títulos'}${filters.title ? ` para «${filters.title}»` : ''}`
              : ''}
        </p>
        {hasFilters && (
          <button
            type="button"
            className="button ghost small"
            onClick={clearFilters}
          >
            <Icon name="close" /> Quitar filtros
          </button>
        )}
      </div>

      {state.error ? (
        <CatalogBlocked error={state.error} />
      ) : state.loading && !state.data ? (
        <GridSkeleton />
      ) : state.data.items.length === 0 ? (
        <div className="empty">
          <h2>Nada por aquí</h2>
          <p>
            Ningún título publicado coincide con estos filtros dentro de la clasificación de {profile.name}. Prueba con
            menos filtros o con otra palabra.
          </p>
          {hasFilters && (
            <button
              type="button"
              className="button secondary"
              onClick={clearFilters}
            >
              Ver todo el catálogo
            </button>
          )}
        </div>
      ) : (
        <>
          <div className="title-grid fade-in" key={queryKey} style={{ opacity: state.loading ? 0.6 : 1 }}>
            {state.data.items.map((item) => (
              <Link key={item.id} to={`/title/${item.id}`} className="title-card">
                <Poster id={item.id} title={item.title} type={item.type} year={item.releaseYear} coverUrl={item.coverUrl} />
                <h3>{item.title}</h3>
                <span className="meta-line">
                  <span>{TYPE_LABEL[item.type]}</span>
                  <span>{item.releaseYear}</span>
                  <span className="badge rating">{ratingLabel(item.ageRating)}</span>
                </span>
              </Link>
            ))}
          </div>

          {totalPages > 1 && (
            <nav className="pagination" aria-label="Páginas de resultados">
              <button
                type="button"
                className="button secondary"
                disabled={filters.page <= 0}
                onClick={() => update({ page: filters.page - 1 })}
              >
                <Icon name="back" /> Anterior
              </button>
              <span>
                Página {filters.page + 1} de {totalPages}
              </span>
              <button
                type="button"
                className="button secondary"
                disabled={filters.page + 1 >= totalPages}
                onClick={() => update({ page: filters.page + 1 })}
              >
                Siguiente <Icon name="next" />
              </button>
            </nav>
          )}
        </>
      )}

      {state.error?.status === 403 && (
        <Notice tone="error">El perfil seleccionado ya no es válido. Elige otro perfil.</Notice>
      )}
    </>
  )
}

function GridSkeleton() {
  return (
    <div className="title-grid" aria-hidden="true">
      {Array.from({ length: 12 }, (_, i) => (
        <div key={i}>
          <div className="poster skeleton" />
          <div className="skeleton line" style={{ width: '85%' }} />
          <div className="skeleton line" style={{ width: '50%' }} />
        </div>
      ))}
    </div>
  )
}
