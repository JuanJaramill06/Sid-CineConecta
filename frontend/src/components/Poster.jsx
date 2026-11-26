import { useState } from 'react'
import { TYPE_LABEL, hueFor } from '../lib/format.js'

/**
 * The content's cover. The typographic poster (title in the display serif on a calm tint) is
 * always there; the real image fades in on top only once it has actually loaded, so a slow or
 * broken URL (the seed uses placeholder domains) never leaves an empty grey box.
 */
export default function Poster({ id, title, type, year, coverUrl, unavailable = false, children }) {
  const [loaded, setLoaded] = useState(false)
  const [failed, setFailed] = useState(false)

  return (
    <div className={`poster${unavailable ? ' unavailable' : ''}`} style={{ '--hue': hueFor(id) }}>
      <div className="poster-art" aria-hidden="true">
        <span className="poster-kind">{TYPE_LABEL[type]?.toUpperCase()}</span>
        <span className="poster-title">{title}</span>
        <span className="poster-year">{year}</span>
      </div>
      {coverUrl && !failed && (
        <img
          src={coverUrl}
          alt=""
          loading="lazy"
          className={loaded ? 'loaded' : undefined}
          onLoad={() => setLoaded(true)}
          onError={() => setFailed(true)}
        />
      )}
      {children}
    </div>
  )
}
