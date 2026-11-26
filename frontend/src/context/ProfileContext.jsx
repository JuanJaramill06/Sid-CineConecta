import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { useAuth } from './AuthContext.jsx'

const ProfileContext = createContext(null)
const STORAGE_KEY = 'cineconecta.profile'

function readStoredProfile() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

/** profile = { profileId, name } | null — becomes the X-Profile-Id header (RF12). */
export function ProfileProvider({ children }) {
  const { auth } = useAuth()
  const [profile, setProfile] = useState(readStoredProfile)

  useEffect(() => {
    if (profile) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(profile))
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  }, [profile])

  // No client session (logged out, or an admin session): no profile makes sense.
  useEffect(() => {
    if (!auth?.token || auth.role !== 'CLIENT') {
      setProfile(null)
    }
  }, [auth])

  const value = useMemo(
    () => ({
      profile,
      selectProfile: setProfile,
      clearProfile: () => setProfile(null),
    }),
    [profile],
  )

  return <ProfileContext.Provider value={value}>{children}</ProfileContext.Provider>
}

export function useProfile() {
  const context = useContext(ProfileContext)
  if (!context) {
    throw new Error('useProfile must be used within a ProfileProvider')
  }
  return context
}
