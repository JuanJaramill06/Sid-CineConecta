import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout.jsx'
import HomePage from './pages/HomePage.jsx'
import LoginPage from './pages/auth/LoginPage.jsx'
import RegisterPage from './pages/auth/RegisterPage.jsx'
import PlansPage from './pages/plans/PlansPage.jsx'
import SubscriptionPage from './pages/plans/SubscriptionPage.jsx'
import ProfilesPage from './pages/profiles/ProfilesPage.jsx'
import ProfileSelectPage from './pages/profiles/ProfileSelectPage.jsx'
import './App.css'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/plans" element={<PlansPage />} />
        <Route path="/subscription" element={<SubscriptionPage />} />
        <Route path="/profiles" element={<ProfilesPage />} />
        <Route path="/select-profile" element={<ProfileSelectPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
