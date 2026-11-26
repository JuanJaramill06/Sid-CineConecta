import { Navigate, Route, Routes } from 'react-router-dom'
import { RequireAdmin, RequireClient, RequireProfile } from './components/Guards.jsx'
import Layout from './components/Layout.jsx'
import HomePage from './pages/HomePage.jsx'
import AdminContentPage from './pages/admin/AdminContentPage.jsx'
import AdminLoginPage from './pages/admin/AdminLoginPage.jsx'
import ContentEditorPage from './pages/admin/ContentEditorPage.jsx'
import ReportsPage from './pages/admin/ReportsPage.jsx'
import LoginPage from './pages/auth/LoginPage.jsx'
import RegisterPage from './pages/auth/RegisterPage.jsx'
import CatalogPage from './pages/catalog/CatalogPage.jsx'
import TitlePage from './pages/catalog/TitlePage.jsx'
import MyListPage from './pages/mylist/MyListPage.jsx'
import PlansPage from './pages/plans/PlansPage.jsx'
import SubscriptionPage from './pages/plans/SubscriptionPage.jsx'
import PlayerPage from './pages/player/PlayerPage.jsx'
import ProfileSelectPage from './pages/profiles/ProfileSelectPage.jsx'
import ProfilesPage from './pages/profiles/ProfilesPage.jsx'
import './App.css'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/plans" element={<PlansPage />} />

        <Route element={<RequireClient />}>
          <Route path="/subscription" element={<SubscriptionPage />} />
          <Route path="/profiles" element={<ProfilesPage />} />
          <Route path="/select-profile" element={<ProfileSelectPage />} />
        </Route>

        {/* Everything that acts on a profile sends X-Profile-Id; the server re-checks it each time */}
        <Route element={<RequireProfile />}>
          <Route path="/catalog" element={<CatalogPage />} />
          <Route path="/title/:id" element={<TitlePage />} />
          <Route path="/my-list" element={<MyListPage />} />
          <Route path="/watch/:contentId" element={<PlayerPage />} />
        </Route>

        <Route path="/admin/login" element={<AdminLoginPage />} />
        <Route element={<RequireAdmin />}>
          <Route path="/admin" element={<Navigate to="/admin/content" replace />} />
          <Route path="/admin/content" element={<AdminContentPage />} />
          <Route path="/admin/content/new" element={<ContentEditorPage />} />
          <Route path="/admin/content/:id" element={<ContentEditorPage />} />
          <Route path="/admin/reports" element={<ReportsPage />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
