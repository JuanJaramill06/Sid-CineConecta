import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { useProfile } from '../context/ProfileContext.jsx'
import { initials } from '../lib/format.js'

/**
 * Two rooms, one house: customers get a quiet cinematic bar (catalog, My list, profile);
 * administrators get a working bar (catalog management, reports). Same tokens, different jobs.
 */
export default function Layout() {
  const { isAuthenticated, isClient, isAdmin, auth, logout } = useAuth()
  const { profile } = useProfile()
  const navigate = useNavigate()

  function handleLogout() {
    const wasAdmin = isAdmin
    logout()
    navigate(wasAdmin ? '/admin/login' : '/login')
  }

  return (
    <div className={`app-shell${isAdmin ? ' admin' : ''}`}>
      <a className="skip-link" href="#main">
        Saltar al contenido
      </a>
      <header className="app-header">
        <Link to={isAdmin ? '/admin/content' : '/'} className="brand">
          CineConecta
          {isAdmin && <span className="brand-tag">Administración</span>}
        </Link>

        <nav className="app-nav" aria-label="Principal">
          {isAdmin ? (
            <>
              <NavLink to="/admin/content">Catálogo</NavLink>
              <NavLink to="/admin/reports">Informes</NavLink>
              <span className="spacer" />
              <span className="muted" style={{ fontSize: 'var(--text-sm)' }}>
                {auth?.fullName}
              </span>
              <button type="button" className="link-button" onClick={handleLogout}>
                Salir
              </button>
            </>
          ) : (
            <>
              {isClient && profile && (
                <>
                  <NavLink to="/" end>
                    Inicio
                  </NavLink>
                  <NavLink to="/catalog">Catálogo</NavLink>
                  <NavLink to="/my-list">Mi lista</NavLink>
                </>
              )}
              {!(isClient && profile) && <NavLink to="/plans">Planes</NavLink>}
              <span className="spacer" />
              {isClient && (
                <>
                  <NavLink to="/subscription">Cuenta</NavLink>
                  <NavLink to="/select-profile" className="profile-chip" title="Cambiar de perfil">
                    {profile ? (
                      <>
                        <span className="avatar" aria-hidden="true">
                          {initials(profile.name)}
                        </span>
                        {profile.name}
                      </>
                    ) : (
                      'Elegir perfil'
                    )}
                  </NavLink>
                </>
              )}
              {isAuthenticated ? (
                <button type="button" className="link-button" onClick={handleLogout}>
                  Salir
                </button>
              ) : (
                <>
                  <NavLink to="/login">Entrar</NavLink>
                  <Link to="/register" className="button small">
                    Crear cuenta
                  </Link>
                </>
              )}
            </>
          )}
        </nav>
      </header>

      <main id="main" className="app-content">
        <Outlet />
      </main>

      <footer className="app-footer">
        CineConecta · proyecto académico de persistencia políglota (Oracle + MongoDB).{' '}
        {!isAuthenticated && <Link to="/admin/login">Acceso de administración</Link>}
      </footer>
    </div>
  )
}
