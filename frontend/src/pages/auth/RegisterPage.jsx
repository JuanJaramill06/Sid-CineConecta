import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { ApiError, api } from '../../api/client.js'
import { useAuth } from '../../context/AuthContext.jsx'

export default function RegisterPage() {
  const { isAuthenticated, login } = useAuth()
  const navigate = useNavigate()
  const [fullName, setFullName] = useState('')
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
      const response = await api.post('/api/auth/register', { fullName, email, password })
      login(response)
      navigate('/')
    } catch (err) {
      if (err instanceof ApiError && err.details?.length) {
        setError(err.details.join(' · '))
      } else {
        setError(err instanceof ApiError ? err.message : 'No se pudo crear la cuenta')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="card">
      <h1>Crear cuenta</h1>
      <form className="form" onSubmit={handleSubmit}>
        <label>
          Nombre completo
          <input
            type="text"
            value={fullName}
            onChange={(event) => setFullName(event.target.value)}
            autoComplete="name"
            required
          />
        </label>
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
            autoComplete="new-password"
            minLength={8}
            required
          />
        </label>
        {error && <p className="error-text">{error}</p>}
        <button type="submit" className="button" disabled={submitting}>
          {submitting ? 'Creando…' : 'Crear cuenta'}
        </button>
      </form>
      <p>
        ¿Ya tienes cuenta? <Link to="/login">Entrar</Link>
      </p>
    </section>
  )
}
