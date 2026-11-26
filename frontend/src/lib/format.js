/** Shared labels and formatters, so every screen says the same thing the same way. */

export const TYPE_LABEL = { MOVIE: 'Película', SERIES: 'Serie', DOCUMENTARY: 'Documental' }
export const TYPE_LABEL_PLURAL = { MOVIE: 'Películas', SERIES: 'Series', DOCUMENTARY: 'Documentales' }
export const STATUS_LABEL = { DRAFT: 'Borrador', PUBLISHED: 'Publicado', WITHDRAWN: 'Retirado' }

export const LANGUAGES = { es: 'Español', en: 'Inglés', pt: 'Portugués', fr: 'Francés' }

// Seed + volume genres (database/mongodb/03_seed.js, 04_volume.js); the filter also accepts free text.
export const GENRES = [
  'Acción', 'Animación', 'Aventura', 'Ciencia', 'Ciencia ficción', 'Comedia', 'Crimen', 'Drama',
  'Educativo', 'Familiar', 'Historia', 'Infantil', 'Música', 'Naturaleza', 'Romance', 'Sociedad',
  'Terror', 'Thriller',
]

export function languageLabel(code) {
  return LANGUAGES[code] ?? code?.toUpperCase()
}

/** 0 -> "TP" (todo público), otherwise "+12". */
export function ratingLabel(rating) {
  if (rating === null || rating === undefined) return ''
  return rating === 0 ? 'TP' : `+${rating}`
}

/** 6600 -> "1 h 50 min"; 2700 -> "45 min". */
export function formatDuration(seconds) {
  if (!seconds && seconds !== 0) return ''
  const minutes = Math.round(seconds / 60)
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m} min`
  return m === 0 ? `${h} h` : `${h} h ${m} min`
}

/** 3725 -> "1:02:05"; 65 -> "1:05". */
export function formatClock(seconds) {
  const s = Math.max(0, Math.floor(seconds || 0))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = String(s % 60).padStart(2, '0')
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`
}

const dateFormat = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short', year: 'numeric' })
const dateTimeFormat = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })

export function formatDate(value) {
  return value ? dateFormat.format(new Date(value)) : ''
}

export function formatDateTime(value) {
  return value ? dateTimeFormat.format(new Date(value)) : ''
}

export const currency = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })
export const number = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 1 })

/** "SER-00002-S02E01" -> "T2 · E1". */
export function episodeLabel(episodeId) {
  const match = episodeId?.match(/S(\d+)E(\d+)$/)
  return match ? `T${Number(match[1])} · E${Number(match[2])}` : ''
}

/** Stable hue per content id, for the typographic posters (kept inside a calm band). */
export function hueFor(id = '') {
  let hash = 0
  for (const char of id) hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  return [20, 45, 75, 160, 200, 250, 290, 330][hash % 8]
}

export function initials(name = '') {
  return name.trim().split(/\s+/).slice(0, 2).map((part) => part[0]?.toUpperCase()).join('') || '?'
}

export function isoDate(date) {
  const d = new Date(date)
  const local = new Date(d.getTime() - d.getTimezoneOffset() * 60000)
  return local.toISOString().slice(0, 10)
}
