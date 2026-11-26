import Icon from './Icon.jsx'

const ICONS = { error: 'alert', warning: 'lock', success: 'check', info: 'info' }

/** A designed state message: what happened, and (optionally) what the user can do about it. */
export default function Notice({ tone = 'info', title, children, actions, role }) {
  return (
    <div className={`notice ${tone}`} role={role ?? (tone === 'error' ? 'alert' : 'status')}>
      <Icon name={ICONS[tone]} />
      <div>
        {title && <strong>{title}</strong>}
        {children && <p>{children}</p>}
        {actions && <div className="notice-actions">{actions}</div>}
      </div>
    </div>
  )
}
