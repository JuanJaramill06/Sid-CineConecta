/** /watch/MOV-00001, /watch/SER-00002?episode=SER-00002-S02E01&t=1500 */
export function watchPath(contentId, episodeId, positionSeconds) {
  const params = new URLSearchParams()
  if (episodeId) params.set('episode', episodeId)
  if (positionSeconds > 0) params.set('t', String(positionSeconds))
  const query = params.toString()
  return `/watch/${contentId}${query ? `?${query}` : ''}`
}
