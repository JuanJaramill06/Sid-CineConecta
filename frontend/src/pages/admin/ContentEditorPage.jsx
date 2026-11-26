import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client.js'
import { useApiClient } from '../../api/useApiClient.js'
import Icon from '../../components/Icon.jsx'
import Notice from '../../components/Notice.jsx'
import { STATUS_LABEL, TYPE_LABEL } from '../../lib/format.js'

const TYPES = ['MOVIE', 'SERIES', 'DOCUMENTARY']
const RATINGS = [0, 7, 12, 16, 18]

const emptyEpisode = (number) => ({ number, title: '', synopsis: '', minutes: '' })
const emptySeason = (seasonNumber) => ({ seasonNumber, description: '', episodes: [emptyEpisode(1)] })

function emptyForm() {
  return {
    type: 'MOVIE',
    title: '',
    synopsis: '',
    releaseYear: new Date().getFullYear(),
    genres: '',
    productionCountries: 'Colombia',
    audioLanguages: 'es',
    subtitleLanguages: '',
    ageRating: 12,
    coverUrl: '',
    movie: { minutes: '', directors: '', cast: [{ actor: '', character: '' }] },
    documentary: { minutes: '', directors: '', topics: '', interviewees: '', sources: '' },
    seasons: [emptySeason(1)],
  }
}

const list = (text) =>
  text
    .split(',')
    .map((part) => part.trim())
    .filter(Boolean)
const text = (values) => (values ?? []).join(', ')
const minutes = (seconds) => (seconds ? String(Math.round(seconds / 60)) : '')
const seconds = (mins) => Math.round(Number(mins) * 60)

/** ContentDetailResponse -> form */
function toForm(content) {
  const form = emptyForm()
  return {
    ...form,
    type: content.type,
    title: content.title,
    synopsis: content.synopsis,
    releaseYear: content.releaseYear,
    genres: text(content.genres),
    productionCountries: text(content.productionCountries),
    audioLanguages: text(content.audioLanguages),
    subtitleLanguages: text(content.subtitleLanguages),
    ageRating: content.ageRating,
    coverUrl: content.coverUrl,
    movie: content.movie
      ? {
          minutes: minutes(content.movie.durationSeconds),
          directors: text(content.movie.directors),
          cast: content.movie.cast.length ? content.movie.cast.map((c) => ({ actor: c.actor, character: c.character ?? '' })) : form.movie.cast,
        }
      : form.movie,
    documentary: content.documentary
      ? {
          minutes: minutes(content.documentary.durationSeconds),
          directors: text(content.documentary.directors),
          topics: text(content.documentary.topics),
          interviewees: text(content.documentary.interviewees),
          sources: text(content.documentary.sources),
        }
      : form.documentary,
    seasons: content.series
      ? content.series.seasons.map((s) => ({
          seasonNumber: s.seasonNumber,
          description: s.description,
          stored: true,
          episodes: s.episodes.map((e) => ({ number: e.number, title: e.title, synopsis: e.synopsis, minutes: minutes(e.durationSeconds), stored: true })),
        }))
      : form.seasons,
  }
}

/** form -> ContentRequest: only the subdocument of the chosen type is sent (the validator's oneOf). */
function toRequest(form) {
  return {
    type: form.type,
    title: form.title.trim(),
    synopsis: form.synopsis.trim(),
    releaseYear: Number(form.releaseYear),
    genres: list(form.genres),
    productionCountries: list(form.productionCountries),
    audioLanguages: list(form.audioLanguages),
    subtitleLanguages: list(form.subtitleLanguages),
    ageRating: Number(form.ageRating),
    coverUrl: form.coverUrl.trim(),
    movie:
      form.type === 'MOVIE'
        ? {
            durationSeconds: seconds(form.movie.minutes),
            directors: list(form.movie.directors),
            cast: form.movie.cast
              .filter((c) => c.actor.trim())
              .map((c) => ({ actor: c.actor.trim(), character: c.character.trim() || null })),
          }
        : null,
    documentary:
      form.type === 'DOCUMENTARY'
        ? {
            durationSeconds: seconds(form.documentary.minutes),
            directors: list(form.documentary.directors),
            topics: list(form.documentary.topics),
            interviewees: list(form.documentary.interviewees),
            sources: list(form.documentary.sources),
          }
        : null,
    series:
      form.type === 'SERIES'
        ? {
            seasons: form.seasons.map((s) => ({
              seasonNumber: Number(s.seasonNumber),
              description: s.description.trim(),
              episodes: s.episodes.map((e) => ({
                number: Number(e.number),
                title: e.title.trim(),
                synopsis: e.synopsis.trim(),
                durationSeconds: seconds(e.minutes),
              })),
            })),
          }
        : null,
  }
}

/** RF24-RF31: one form, with the block that matches the type (movie, series, documentary). */
export default function ContentEditorPage() {
  const { id } = useParams()
  const isNew = !id
  const api = useApiClient()
  const navigate = useNavigate()
  const location = useLocation()

  const [form, setForm] = useState(emptyForm)
  const [meta, setMeta] = useState(null) // { id, status }
  const [loading, setLoading] = useState(!isNew)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(location.state?.notice ?? null)

  useEffect(() => {
    if (isNew) return
    let active = true
    setLoading(true)
    api
      .get(`/api/admin/content/${id}`)
      .then((content) => {
        if (!active) return
        setForm(toForm(content))
        setMeta({ id: content.id, status: content.status })
      })
      .catch((err) => active && setError(err))
      .finally(() => active && setLoading(false))
    return () => {
      active = false
    }
  }, [api, id, isNew])

  const set = (key, value) => setForm((f) => ({ ...f, [key]: value }))
  const setIn = (group, key, value) => setForm((f) => ({ ...f, [group]: { ...f[group], [key]: value } }))
  const setSeasons = (updater) => setForm((f) => ({ ...f, seasons: updater(f.seasons) }))

  async function handleSubmit(event) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    setNotice(null)
    try {
      const body = toRequest(form)
      if (isNew) {
        const created = await api.post('/api/admin/content', body)
        navigate(`/admin/content/${created.id}`, {
          replace: true,
          state: { notice: `Creado como borrador con el id ${created.id}. Revísalo y publícalo cuando esté listo.` },
        })
      } else {
        const saved = await api.put(`/api/admin/content/${id}`, body)
        setForm(toForm(saved))
        setMeta({ id: saved.id, status: saved.status })
        setNotice('Cambios guardados.')
      }
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  async function changeStatus(action) {
    setSaving(true)
    setError(null)
    try {
      const saved = await api.post(`/api/admin/content/${id}/${action}`)
      setMeta({ id: saved.id, status: saved.status })
      setNotice(action === 'publish' ? 'Publicado: ya es visible en el catálogo.' : 'Retirado del catálogo. Su historial se conserva.')
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <p className="muted">Cargando contenido…</p>
  if (!isNew && !meta && error) return <Notice tone="error">{error.message}</Notice>

  const publishedSeries = !isNew && form.type === 'SERIES' && meta?.status !== 'DRAFT'

  return (
    <form className="editor" onSubmit={handleSubmit}>
      <div className="page-head" style={{ marginBottom: 0 }}>
        <div>
          <Link to="/admin/content" className="button ghost small" style={{ marginBottom: 'var(--s-2)' }}>
            <Icon name="back" /> Catálogo
          </Link>
          <h1>{isNew ? 'Nuevo contenido' : form.title || meta?.id}</h1>
          {meta && (
            <p className="meta-line">
              <span className="mono">{meta.id}</span>
              <span className={`badge ${meta.status.toLowerCase()}`}>{STATUS_LABEL[meta.status]}</span>
            </p>
          )}
        </div>
        {meta && (
          <div className="title-actions" style={{ margin: 0 }}>
            {meta.status === 'PUBLISHED' ? (
              <button type="button" className="button danger" onClick={() => changeStatus('withdraw')} disabled={saving}>
                Retirar
              </button>
            ) : (
              <button type="button" className="button secondary" onClick={() => changeStatus('publish')} disabled={saving}>
                {meta.status === 'WITHDRAWN' ? 'Volver a publicar' : 'Publicar'}
              </button>
            )}
          </div>
        )}
      </div>

      {notice && <Notice tone="success">{notice}</Notice>}
      {error && <ServerError error={error} />}

      <fieldset>
        <legend>Tipo</legend>
        <div className="segmented" role="group" aria-label="Tipo de contenido">
          {TYPES.map((type) => (
            <button
              key={type}
              type="button"
              aria-pressed={form.type === type}
              disabled={!isNew && form.type !== type}
              onClick={() => set('type', type)}
            >
              {TYPE_LABEL[type]}
            </button>
          ))}
        </div>
        {!isNew && <p className="hint">El tipo de un contenido no se puede cambiar después de crearlo.</p>}
      </fieldset>

      <fieldset>
        <legend>Datos generales</legend>
        <label className="field">
          Título
          <input value={form.title} onChange={(e) => set('title', e.target.value)} required />
        </label>
        <label className="field">
          Sinopsis
          <textarea value={form.synopsis} onChange={(e) => set('synopsis', e.target.value)} required />
        </label>
        <div className="field-row">
          <label className="field">
            Año de estreno
            <input type="number" min="1888" max="2100" value={form.releaseYear} onChange={(e) => set('releaseYear', e.target.value)} required />
          </label>
          <label className="field">
            Clasificación por edad
            <select value={form.ageRating} onChange={(e) => set('ageRating', e.target.value)}>
              {RATINGS.map((r) => (
                <option key={r} value={r}>
                  {r === 0 ? 'Todo público' : `+${r}`}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            URL de la portada
            <input type="url" value={form.coverUrl} onChange={(e) => set('coverUrl', e.target.value)} placeholder="https://…" required />
          </label>
        </div>
        <div className="field-row">
          <ListField label="Géneros" value={form.genres} onChange={(v) => set('genres', v)} placeholder="Drama, Crimen" required />
          <ListField label="Países de producción" value={form.productionCountries} onChange={(v) => set('productionCountries', v)} required />
          <ListField label="Idiomas de audio" value={form.audioLanguages} onChange={(v) => set('audioLanguages', v)} placeholder="es, en" required />
          <ListField label="Subtítulos" value={form.subtitleLanguages} onChange={(v) => set('subtitleLanguages', v)} placeholder="Ninguno" />
        </div>
      </fieldset>

      {form.type === 'MOVIE' && (
        <fieldset>
          <legend>Película</legend>
          <div className="field-row">
            <label className="field">
              Duración (minutos)
              <input type="number" min="1" value={form.movie.minutes} onChange={(e) => setIn('movie', 'minutes', e.target.value)} required />
            </label>
            <ListField label="Dirección" value={form.movie.directors} onChange={(v) => setIn('movie', 'directors', v)} required />
          </div>
          <div className="field">
            Reparto
            <span className="hint">El personaje es opcional: déjalo vacío si no se conoce.</span>
          </div>
          {form.movie.cast.map((member, index) => (
            <div key={index} className="cast-row">
              <label className="field">
                <span className="visually-hidden">Actor o actriz {index + 1}</span>
                <input
                  value={member.actor}
                  placeholder="Actor o actriz"
                  onChange={(e) => setIn('movie', 'cast', form.movie.cast.map((c, i) => (i === index ? { ...c, actor: e.target.value } : c)))}
                />
              </label>
              <label className="field">
                <span className="visually-hidden">Personaje {index + 1}</span>
                <input
                  value={member.character}
                  placeholder="Personaje (opcional)"
                  onChange={(e) => setIn('movie', 'cast', form.movie.cast.map((c, i) => (i === index ? { ...c, character: e.target.value } : c)))}
                />
              </label>
              <button
                type="button"
                className="button ghost small"
                aria-label={`Quitar fila ${index + 1} del reparto`}
                onClick={() => setIn('movie', 'cast', form.movie.cast.filter((_, i) => i !== index))}
              >
                <Icon name="trash" />
              </button>
            </div>
          ))}
          <div>
            <button type="button" className="button secondary small" onClick={() => setIn('movie', 'cast', [...form.movie.cast, { actor: '', character: '' }])}>
              <Icon name="plus" /> Añadir al reparto
            </button>
          </div>
        </fieldset>
      )}

      {form.type === 'DOCUMENTARY' && (
        <fieldset>
          <legend>Documental</legend>
          <div className="field-row">
            <label className="field">
              Duración (minutos)
              <input type="number" min="1" value={form.documentary.minutes} onChange={(e) => setIn('documentary', 'minutes', e.target.value)} required />
            </label>
            <ListField label="Dirección" value={form.documentary.directors} onChange={(v) => setIn('documentary', 'directors', v)} required />
          </div>
          <div className="field-row">
            <ListField label="Temas abordados" value={form.documentary.topics} onChange={(v) => setIn('documentary', 'topics', v)} required />
            <ListField label="Personas entrevistadas" value={form.documentary.interviewees} onChange={(v) => setIn('documentary', 'interviewees', v)} required />
            <ListField label="Fuentes o instituciones" value={form.documentary.sources} onChange={(v) => setIn('documentary', 'sources', v)} placeholder="Opcional" />
          </div>
        </fieldset>
      )}

      {form.type === 'SERIES' && (
        <fieldset>
          <legend>Temporadas y episodios</legend>
          <p className="hint">
            Los números no pueden repetirse dentro de la serie (temporadas) ni dentro de cada temporada (episodios). El id de cada
            episodio se genera a partir de la serie, la temporada y el número.
          </p>
          {publishedSeries && (
            <Notice tone="warning" title="Serie ya publicada">
              Puedes añadir episodios y editar sus datos, pero no quitar ni renumerar los existentes: sus ids no se reutilizan.
            </Notice>
          )}
          {form.seasons.map((season, si) => (
            <section key={si} className="season-block" aria-label={`Temporada ${season.seasonNumber}`}>
              <header>
                <div className="field-row" style={{ flex: 1 }}>
                  <label className="field">
                    Número de temporada
                    <input
                      type="number"
                      min="1"
                      value={season.seasonNumber}
                      readOnly={publishedSeries && season.stored}
                      onChange={(e) => setSeasons((ss) => ss.map((s, i) => (i === si ? { ...s, seasonNumber: e.target.value } : s)))}
                      required
                    />
                  </label>
                  <label className="field" style={{ gridColumn: 'span 2' }}>
                    Descripción
                    <input
                      value={season.description}
                      onChange={(e) => setSeasons((ss) => ss.map((s, i) => (i === si ? { ...s, description: e.target.value } : s)))}
                      required
                    />
                  </label>
                </div>
                {form.seasons.length > 1 && !(publishedSeries && season.stored) && (
                  <button type="button" className="button ghost small" onClick={() => setSeasons((ss) => ss.filter((_, i) => i !== si))}>
                    <Icon name="trash" /> Quitar temporada
                  </button>
                )}
              </header>

              {season.episodes.map((episode, ei) => {
                const patch = (key, value) =>
                  setSeasons((ss) =>
                    ss.map((s, i) => (i === si ? { ...s, episodes: s.episodes.map((e, j) => (j === ei ? { ...e, [key]: value } : e)) } : s)),
                  )
                return (
                  <div key={ei} className="episode-row">
                    <label className="field">
                      Nº
                      <input
                        type="number"
                        min="1"
                        value={episode.number}
                        readOnly={publishedSeries && episode.stored}
                        title={publishedSeries && episode.stored ? 'Episodio publicado: su número forma parte de su id' : undefined}
                        onChange={(e) => patch('number', e.target.value)}
                        required
                      />
                    </label>
                    <label className="field">
                      Título
                      <input value={episode.title} onChange={(e) => patch('title', e.target.value)} required />
                    </label>
                    <label className="field">
                      Sinopsis
                      <input value={episode.synopsis} onChange={(e) => patch('synopsis', e.target.value)} required />
                    </label>
                    <label className="field">
                      Minutos
                      <input type="number" min="1" value={episode.minutes} onChange={(e) => patch('minutes', e.target.value)} required />
                    </label>
                    <button
                      type="button"
                      className="button ghost small"
                      aria-label={`Quitar episodio ${episode.number}`}
                      disabled={season.episodes.length === 1 || (publishedSeries && episode.stored)}
                      onClick={() => setSeasons((ss) => ss.map((s, i) => (i === si ? { ...s, episodes: s.episodes.filter((_, j) => j !== ei) } : s)))}
                    >
                      <Icon name="trash" />
                    </button>
                  </div>
                )
              })}
              <div>
                <button
                  type="button"
                  className="button secondary small"
                  onClick={() =>
                    setSeasons((ss) =>
                      ss.map((s, i) =>
                        i === si ? { ...s, episodes: [...s.episodes, emptyEpisode(Math.max(0, ...s.episodes.map((e) => Number(e.number) || 0)) + 1)] } : s,
                      ),
                    )
                  }
                >
                  <Icon name="plus" /> Añadir episodio
                </button>
              </div>
            </section>
          ))}
          <div>
            <button
              type="button"
              className="button secondary"
              onClick={() => setSeasons((ss) => [...ss, emptySeason(Math.max(0, ...ss.map((s) => Number(s.seasonNumber) || 0)) + 1)])}
            >
              <Icon name="plus" /> Añadir temporada
            </button>
          </div>
        </fieldset>
      )}

      <div className="sticky-actions">
        <Link to="/admin/content" className="button ghost">
          Cancelar
        </Link>
        <button type="submit" className="button large" disabled={saving}>
          {saving ? 'Guardando…' : isNew ? 'Crear como borrador' : 'Guardar cambios'}
        </button>
      </div>
    </form>
  )
}

function ListField({ label, value, onChange, placeholder, required }) {
  return (
    <label className="field">
      {label}
      <input value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} required={required} />
      <span className="hint">Separa con comas</span>
    </label>
  )
}

/** The server's answer, with the per-field details of a 400 when there are any. */
function ServerError({ error }) {
  const details = error instanceof ApiError ? error.details : null
  return (
    <Notice tone="error" title="No se pudo guardar">
      {error.message}
      {details?.length > 0 && (
        <>
          <br />
          {details.join(' · ')}
        </>
      )}
    </Notice>
  )
}
