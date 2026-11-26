/** One stroke icon set (24px grid, 1.75 stroke) used everywhere; decorative unless labelled. */
const PATHS = {
  play: <path d="M7 4.5v15l12-7.5z" fill="currentColor" stroke="none" />,
  pause: (
    <>
      <rect x="6" y="4.5" width="4" height="15" rx="1" fill="currentColor" stroke="none" />
      <rect x="14" y="4.5" width="4" height="15" rx="1" fill="currentColor" stroke="none" />
    </>
  ),
  stop: <rect x="6" y="6" width="12" height="12" rx="1.5" fill="currentColor" stroke="none" />,
  plus: <path d="M12 5v14M5 12h14" />,
  check: <path d="m5 12.5 4.5 4.5L19 7.5" />,
  close: <path d="M6 6l12 12M18 6 6 18" />,
  alert: (
    <>
      <path d="M12 3.5 2.5 20h19z" />
      <path d="M12 10v4.5M12 17.5v.01" />
    </>
  ),
  info: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5.5M12 7.5v.01" />
    </>
  ),
  lock: (
    <>
      <rect x="5" y="10.5" width="14" height="10" rx="2" />
      <path d="M8 10.5V8a4 4 0 0 1 8 0v2.5" />
    </>
  ),
  search: (
    <>
      <circle cx="11" cy="11" r="6.5" />
      <path d="m16 16 4.5 4.5" />
    </>
  ),
  back: <path d="M15 5 8 12l7 7" />,
  next: <path d="m9 5 7 7-7 7" />,
  download: <path d="M12 4v11m0 0-4.5-4.5M12 15l4.5-4.5M5 19.5h14" />,
  edit: <path d="M4.5 19.5h4l10-10-4-4-10 10zM13 7l4 4" />,
  forward: <path d="M5 6.5v11l7-5.5zM12 6.5v11l7-5.5z" fill="currentColor" stroke="none" />,
  rewind: <path d="M19 6.5v11l-7-5.5zM12 6.5v11L5 12z" fill="currentColor" stroke="none" />,
  trash: <path d="M5 7h14M10 7V5h4v2M7 7l1 12.5h8L17 7" />,
}

export default function Icon({ name, label, className = 'icon' }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    >
      {PATHS[name]}
    </svg>
  )
}
