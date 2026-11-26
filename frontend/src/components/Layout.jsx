import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

export default function Layout() {
  const { isAuthenticated, isClient, logout } = useAuth()

  return (
    <div className="app-shell">
      <header className="app-header">
        <NavLink to="/" className="brand">
          CineConecta
        </NavLink>
        <nav>
          <NavLink to="/plans">Planes</NavLink>
          {isClient && <NavLink to="/subscription">Mi suscripción</NavLink>}
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
