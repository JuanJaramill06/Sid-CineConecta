import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

export default function Layout() {
  const { isAuthenticated, logout } = useAuth()

  return (
    <div className="app-shell">
      <header className="app-header">
        <NavLink to="/" className="brand">
          CineConecta
        </NavLink>
        <nav>
          {isAuthenticated ? (
            <button type="button" className="link-button" onClick={logout}>
              Cerrar sesión
            </button>
          ) : (
            <>
              <NavLink to="/login">Entrar</NavLink>
              <NavLink to="/register">Crear cuenta</NavLink>
            </>
          )}
        </nav>
      </header>
      <main className="app-content">
        <Outlet />
      </main>
    </div>
  )
}
