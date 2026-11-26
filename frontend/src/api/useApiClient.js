import { useCallback, useMemo } from 'react'
import { useAuth } from '../context/AuthContext.jsx'
import { useProfile } from '../context/ProfileContext.jsx'
import { api } from './client.js'

/** The same client as api/client.js, with the current token and selected profile already attached. */
export function useApiClient() {
  const { auth } = useAuth()
  const { profile } = useProfile()
  const token = auth?.token
  const profileId = profile?.profileId

  const withContext = useCallback((options) => ({ ...options, token, profileId }), [token, profileId])

  return useMemo(
    () => ({
      get: (path, options) => api.get(path, withContext(options)),
      post: (path, body, options) => api.post(path, body, withContext(options)),
      put: (path, body, options) => api.put(path, body, withContext(options)),
      delete: (path, options) => api.delete(path, withContext(options)),
      download: (path, options) => api.download(path, withContext(options)),
    }),
    [withContext],
  )
}
