import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { ApiError, api } from '../../api/client.js'
import Notice from '../../components/Notice.jsx'
import { useAuth } from '../../context/AuthContext.jsx'

/** RF64: administrators log in against Admin_user (MongoDB), separate from customer accounts. */
export default function AdminLoginPage() {
  const { isAdmin, isClient, login, logout } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  if (isAdmin) return <Navigate to="/admin/content" replace />

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const session = await api.post('/api/admin/auth/login', { email, password })
      if (isClient) logout() // never mix a customer session with an admin one
      login(session)
      navigate('/admin/content')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No se pudo iniciar sesión')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="auth-layout">
      <div className="auth-copy">
        <h1>Administración</h1>
        <p className="lede">
          Gestiona el catálogo (registrar, editar, publicar y retirar) y consulta los informes de ingresos y consumo.
        </p>
        <p className="muted">
          Las cuentas de administración son independientes de las de clientes: no tienen suscripción ni perfiles.
        </p>
      </div>
      <div className="card">
        <h2>Entrar como administrador</h2>
        <form className="form" onSubmit={handleSubmit} noValidate={false}>
          <label>
            Correo electrónico
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" required />
          </label>
          <label>
            Contraseña
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
          </label>
          {error && <Notice tone="error">{error}</Notice>}
          <button type="submit" className="button" disabled={submitting}>
            {submitting ? 'Entrando…' : 'Entrar'}
          </button>
        </form>
        <p className="muted" style={{ marginTop: 'var(--s-4)', fontSize: 'var(--text-sm)' }}>
          ¿Eres cliente? <Link to="/login">Entra aquí</Link>.
        </p>
      </div>
    </section>
  )
}
