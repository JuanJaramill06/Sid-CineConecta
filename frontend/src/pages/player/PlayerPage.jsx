import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { ApiError } from '../../api/client.js'
import { useApiClient } from '../../api/useApiClient.js'
import Icon from '../../components/Icon.jsx'
import { useProfile } from '../../context/ProfileContext.jsx'
import { TYPE_LABEL, episodeLabel, formatClock, hueFor, ratingLabel } from '../../lib/format.js'

const HEARTBEAT_MS = 30_000 // backend: POST /api/playback/{id}/heartbeat every ~30 s while playing
const STATE_LABEL = { PLAYING: 'Reproduciendo', PAUSED: 'En pausa', ENDED: 'Finalizada' }

/**
 * Simulated playback (RF40-RF56). The server is the clock: every control sends the transition
 * and the response is the truth. Between responses the screen only extrapolates while PLAYING.
 * Position and watched time are shown apart so a seek visibly moves one and not the other (RF53).
 */
export default function PlayerPage() {
  const { contentId } = useParams()
  const [params] = useSearchParams()
  const episodeId = params.get('episode')
  const resumeAt = Number(params.get('t') ?? 0)

  const api = useApiClient()
  const { profile } = useProfile()

  const [phase, setPhase] = useState('starting') // starting | playing | refused | error
  const [session, setSession] = useState(null)
  const [syncedAt, setSyncedAt] = useState(0)
  const [content, setContent] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const [scrub, setScrub] = useState(null) // position being dragged, not yet sent
  const [now, setNow] = useState(() => Date.now())

  const sessionRef = useRef(null)
  const mountedRef = useRef(false)
  const startedRef = useRef(false)
  const apiRef = useRef(api)
  useEffect(() => {
    apiRef.current = api
  }, [api])

  const apply = useCallback((next) => {
    sessionRef.current = next
    setSession(next)
    setSyncedAt(Date.now())
  }, [])

  const send = useCallback(
    async (action, body) => {
      const current = sessionRef.current
      if (!current) return
      setBusy(true)
      try {
        apply(await apiRef.current.post(`/api/playback/${current.sessionId}/${action}`, body))
      } catch (err) {
        setError(err)
      } finally {
        setBusy(false)
      }
    },
    [apply],
  )

  // Start once (StrictMode-safe), and pause when leaving: route change, tab close or reload.
  useEffect(() => {
    mountedRef.current = true
    if (!startedRef.current) {
      startedRef.current = true
      apiRef.current
        .get(`/api/catalog/${contentId}`)
        .then(setContent)
        .catch(() => {})
      apiRef.current
        .post('/api/playback/start', { contentId, episodeId: episodeId ?? undefined })
        .then(async (started) => {
          apply(started)
          setPhase('playing')
          if (resumeAt > 0) {
            apply(await apiRef.current.post(`/api/playback/${started.sessionId}/seek`, { positionSeconds: resumeAt }))
          }
        })
        .catch((err) => {
          setError(err)
          setPhase(err instanceof ApiError && (err.status === 403 || err.status === 422) ? 'refused' : 'error')
        })
    }

    const leave = () => {
      const current = sessionRef.current
      if (current?.state === 'PLAYING') {
        apiRef.current.post(`/api/playback/${current.sessionId}/pause`, undefined, { keepalive: true }).catch(() => {})
      }
    }
    window.addEventListener('pagehide', leave)
    return () => {
      mountedRef.current = false
      window.removeEventListener('pagehide', leave)
      // StrictMode re-mounts immediately; only a real unmount gets past this tick
      setTimeout(() => {
        if (!mountedRef.current) leave()
      }, 0)
    }
  }, [contentId, episodeId, resumeAt, apply])

  const playing = session?.state === 'PLAYING'

  // Local clock for the display, and the heartbeat that saves progress on the server.
  useEffect(() => {
    if (!playing) return undefined
    const tick = setInterval(() => setNow(Date.now()), 500)
    const heartbeat = setInterval(() => send('heartbeat'), HEARTBEAT_MS)
    return () => {
      clearInterval(tick)
      clearInterval(heartbeat)
    }
  }, [playing, send])

  const elapsed = playing ? Math.max(0, (now - syncedAt) / 1000) : 0
  const duration = session?.durationSeconds ?? 0
  const position = session ? Math.min(duration, session.lastPositionSeconds + elapsed) : 0
  const watched = session ? session.watchedSeconds + Math.min(elapsed, duration - session.lastPositionSeconds) : 0

  // Reaching the end finishes the session, as a real player would.
  useEffect(() => {
    if (playing && duration > 0 && position >= duration && !busy) send('finish')
  }, [playing, position, duration, busy, send])

  const seekTo = useCallback(
    (seconds) => send('seek', { positionSeconds: Math.round(Math.max(0, Math.min(duration, seconds))) }),
    [send, duration],
  )

  // Keyboard: space = play/pause, arrows = -10 s / +30 s
  useEffect(() => {
    function onKey(event) {
      if (!sessionRef.current || sessionRef.current.state === 'ENDED') return
      if (['INPUT', 'TEXTAREA', 'SELECT', 'BUTTON'].includes(event.target.tagName)) return
      if (event.code === 'Space') {
        event.preventDefault()
        send(sessionRef.current.state === 'PLAYING' ? 'pause' : 'resume')
      } else if (event.code === 'ArrowLeft') {
        seekTo(position - 10)
      } else if (event.code === 'ArrowRight') {
        seekTo(position + 30)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [send, seekTo, position])

  const episode = content?.series?.seasons.flatMap((s) => s.episodes).find((e) => e.id === episodeId)
  const title = content?.title ?? contentId
  const subtitle = episode ? `${episodeLabel(episodeId)} · ${episode.title}` : TYPE_LABEL[content?.type] ?? ''

  if (phase === 'refused' || phase === 'error') {
    return <Refusal error={error} profile={profile} contentId={contentId} title={content?.title} />
  }

  const ended = session?.state === 'ENDED'
  const shown = scrub ?? position

  return (
    <section className="player">
      <div className="page-head" style={{ marginBottom: 0 }}>
        <Link to={`/title/${contentId}`} className="button ghost small">
          <Icon name="back" /> {content?.title ? `Ficha de ${content.title}` : 'Volver'}
        </Link>
        {session && <span className="mono muted">Sesión {session.sessionId}</span>}
      </div>

      <div className={`screen${playing ? ' playing' : ''}`} style={{ '--hue': hueFor(contentId) }}>
        <div className="grain" aria-hidden="true" />
        {session && (
          <span className={`badge screen-state ${playing ? 'amber' : ended ? 'published' : ''}`} aria-live="polite">
            {STATE_LABEL[session.state]}
          </span>
        )}
        <div>
          <p className="screen-title">{title}</p>
          <p className="screen-sub">{phase === 'starting' ? 'Comprobando suscripción, perfil y clasificación…' : subtitle}</p>
          {ended && (
            <div className="title-actions" style={{ justifyContent: 'center' }}>
              <Link to={`/title/${contentId}`} className="button">
                Volver a la ficha
              </Link>
              <Link to="/" className="button secondary">
                Ir al inicio
              </Link>
            </div>
          )}
        </div>
      </div>

      {session && !ended && (
        <>
          <div className="timeline">
            <span>{formatClock(shown)}</span>
            <input
              type="range"
              min="0"
              max={duration}
              step="1"
              value={Math.round(shown)}
              aria-label="Posición de la reproducción"
              aria-valuetext={`${formatClock(shown)} de ${formatClock(duration)}`}
              onChange={(event) => setScrub(Number(event.target.value))}
              onPointerUp={() => {
                if (scrub !== null) seekTo(scrub)
                setScrub(null)
              }}
              onKeyUp={() => {
                if (scrub !== null) seekTo(scrub)
                setScrub(null)
              }}
            />
            <span>{formatClock(duration)}</span>
          </div>

          <div className="controls">
            <button type="button" className="button large" onClick={() => send(playing ? 'pause' : 'resume')} disabled={busy}>
              <Icon name={playing ? 'pause' : 'play'} />
              {playing ? 'Pausar' : 'Reanudar'}
            </button>
            <button type="button" className="button secondary" onClick={() => seekTo(position - 10)} disabled={busy}>
              <Icon name="rewind" /> 10 s
            </button>
            <button type="button" className="button secondary" onClick={() => seekTo(position + 30)} disabled={busy}>
              30 s <Icon name="forward" />
            </button>
            <span className="spacer" />
            <button type="button" className="button secondary" onClick={() => send('finish')} disabled={busy}>
              <Icon name="stop" /> Finalizar
            </button>
          </div>
        </>
      )}

      {session && (
        <dl className="stat-row">
          <div className="stat">
            <dt>Posición</dt>
            <dd>
              {formatClock(position)} <span className="muted">/ {formatClock(duration)}</span>
            </dd>
          </div>
          <div className="stat">
            <dt>Tiempo visto (sin contar los saltos)</dt>
            <dd>{formatClock(watched)}</dd>
          </div>
          <div className="stat">
            <dt>Completado (al 90 %: {formatClock(duration * 0.9)})</dt>
            <dd>{session.completed ? 'Sí' : 'Todavía no'}</dd>
          </div>
        </dl>
      )}

      {error && phase === 'playing' && <p className="error-text">{error.message}</p>}
      {!ended && session && (
        <p className="muted" style={{ fontSize: 'var(--text-sm)' }}>
          Espacio para pausar o reanudar, ← y → para retroceder 10 s o avanzar 30 s. El progreso se guarda cada 30 s y al salir.
        </p>
      )}
    </section>
  )
}

/** RF40-RF44: playback is refused when any check fails, in Oracle (subscription, profile) or MongoDB (status, rating). */
function Refusal({ error, profile, contentId, title }) {
  const status = error instanceof ApiError ? error.status : 0
  const reason =
    status === 422
      ? error.message
      : status === 403
        ? `«${title ?? contentId}» no está publicado o su clasificación supera la permitida para «${profile.name}» (hasta ${ratingLabel(profile.maxAgeRating)}).`
        : error?.message ?? 'No se pudo iniciar la reproducción.'
  const needsPlan = status === 422 && /suscripci/i.test(error.message)

  return (
    <section className="screen" style={{ '--hue': 28, aspectRatio: 'auto', minHeight: '24rem', padding: 'var(--s-6)' }}>
      <div style={{ maxWidth: '40rem' }}>
        <Icon name="lock" className="icon refusal-icon" />
        <p className="screen-title" style={{ fontSize: 'var(--text-3xl)', marginTop: 'var(--s-3)' }}>
          Reproducción rechazada
        </p>
        <p className="screen-sub">{reason}</p>
        <div className="title-actions" style={{ justifyContent: 'center' }}>
          {needsPlan && (
            <Link to="/plans" className="button">
              Ver planes
            </Link>
          )}
          {status === 403 && (
            <Link to="/select-profile" className="button">
              Cambiar de perfil
            </Link>
          )}
          <Link to="/catalog" className="button secondary">
            Volver al catálogo
          </Link>
        </div>
        <p className="muted" style={{ fontSize: 'var(--text-sm)', marginTop: 'var(--s-4)' }}>
          Antes de reproducir se comprueba en Oracle la suscripción vigente y el perfil, y en MongoDB que el
          contenido esté publicado y permitido para el perfil.
        </p>
      </div>
    </section>
  )
}
