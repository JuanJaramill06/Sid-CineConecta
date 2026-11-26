import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { ApiError, api } from '../../api/client.js'
import { useAuth } from '../../context/AuthContext.jsx'

export default function LoginPage() {
  const { isAuthenticated, login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  if (isAuthenticated) {
    return <Navigate to="/" replace />
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const response = await api.post('/api/auth/login', { email, password })
      login(response)
      navigate('/')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No se pudo iniciar sesión')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="card">
      <h1>Entrar</h1>
      <form className="form" onSubmit={handleSubmit}>
        <label>
          Correo electrónico
          <input
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="email"
            required
          />
        </label>
        <label>
          Contraseña
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        {error && <p className="error-text">{error}</p>}
        <button type="submit" className="button" disabled={submitting}>
          {submitting ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
      <p>
        ¿No tienes cuenta? <Link to="/register">Crear una</Link>
      </p>
    </section>
  )
}
