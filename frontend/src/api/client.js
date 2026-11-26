const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

/** Mirrors backend/.../exception/ErrorResponse: { code, message, timestamp, details? }. */
export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || `Error ${status}`)
    this.status = status
    this.code = body?.code
    this.details = body?.details
  }
}

function buildHeaders(token, profileId, hasBody) {
  const headers = {}
  if (hasBody) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`
  // X-Profile-Id (RNF2, RF12): only sent once a profile has been selected.
  if (profileId) headers['X-Profile-Id'] = String(profileId)
  return headers
}

function buildUrl(path, query) {
  const url = new URL(path, API_BASE_URL)
  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, value)
      }
    })
  }
  return url
}

async function parseBody(response) {
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

// keepalive: the request survives the page being closed (the player's last "pause").
async function request(path, { method = 'GET', body, token, profileId, query, keepalive = false } = {}) {
  let response
  try {
    response = await fetch(buildUrl(path, query), {
      method,
      headers: buildHeaders(token, profileId, body !== undefined),
      body: body !== undefined ? JSON.stringify(body) : undefined,
      keepalive,
    })
  } catch {
    // The API itself is unreachable (backend stopped, wrong URL): same shape as a 503.
    throw new ApiError(0, {
      code: 'NETWORK',
      message: 'No se pudo contactar el servidor de CineConecta. Revisa que el backend esté en marcha.',
    })
  }
  const data = await parseBody(response)
  if (!response.ok) {
    throw new ApiError(response.status, data)
  }
  return data
}

/** Downloads a report as a Blob: a plain <a href> cannot carry the Authorization header. */
async function download(path, { token, profileId, query } = {}) {
  const response = await fetch(buildUrl(path, query), {
    headers: buildHeaders(token, profileId, false),
  })
  if (!response.ok) {
    throw new ApiError(response.status, await parseBody(response))
  }
  const disposition = response.headers.get('Content-Disposition') || ''
  const match = disposition.match(/filename="?([^"]+)"?/)
  return { blob: await response.blob(), filename: match?.[1] ?? 'export.csv' }
}

export const api = {
  get: (path, options) => request(path, { ...options, method: 'GET' }),
  post: (path, body, options) => request(path, { ...options, method: 'POST', body }),
  put: (path, body, options) => request(path, { ...options, method: 'PUT', body }),
  delete: (path, options) => request(path, { ...options, method: 'DELETE' }),
  download,
}

export { API_BASE_URL }
