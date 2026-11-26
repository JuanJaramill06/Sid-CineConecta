import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { useProfile } from '../context/ProfileContext.jsx'

export default function Layout() {
  const { isAuthenticated, isClient, logout } = useAuth()
  const { profile } = useProfile()

  return (
    <div className="app-shell">
      <header className="app-header">
        <NavLink to="/" className="brand">
          CineConecta
        </NavLink>
        <nav>
          <NavLink to="/plans">Planes</NavLink>
          {isClient && <NavLink to="/subscription">Mi suscripción</NavLink>}
          {isClient && <NavLink to="/profiles">Perfiles</NavLink>}
          {isClient && <NavLink to="/select-profile">{profile ? profile.name : 'Elegir perfil'}</NavLink>}
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
