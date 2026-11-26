// =============================================================================
// CineConecta - DEV-ONLY mock of the backend API (no Oracle, no Atlas)
//
// For working on the frontend without the databases. It is NOT the backend and must not be
// used for the sustentacion: the real rules live in Spring Boot + Oracle + MongoDB.
//
//   cd frontend; npm run mock          (port 8080, what the frontend calls by default)
//   $env:MOCK_PORT = '8081'; npm run mock   (if the real backend already uses 8080; then set
//                                            VITE_API_BASE_URL=http://localhost:8081)
//
// Data: the real MongoDB functional seed (database/mongodb/03_seed.js, evaluated here with a
// fake db) plus a copy of the Oracle seed (accounts 1-10, profiles 1-23, subscriptions 1-11).
// Same test logins as the seeds (passwords in the comments of the seed scripts).
// Everything lives in memory and resets when the process stops. It mimics the main rules
// (current subscription, profile ownership, rating, PUBLISHED only, My list duplicates,
// playback transitions, reports) but not every edge case of the real services.
// =============================================================================
import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import { fileURLToPath } from 'node:url'

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')
const PORT = Number(process.env.MOCK_PORT ?? 8080)

// ---- Mongo seed, evaluated with a fake db ----------------------------------------------
const store = {}
const coll = (name) => (store[name] ??= {
  docs: [],
  deleteMany() { this.docs = [] },
  insertMany(d) { this.docs.push(...d.map((x) => ({ _id: x._id ?? crypto.randomBytes(12).toString('hex'), ...x }))) },
  countDocuments() { return this.docs.length },
  updateOne() {},
})
const fakeDb = new Proxy({ getSiblingDB() { return fakeDb } }, { get: (t, k) => (k in t ? t[k] : coll(k)) })
new Function('db', 'print', fs.readFileSync(path.join(repo, 'database/mongodb/03_seed.js'), 'utf8'))(fakeDb, () => {})
const contents = store.Content.docs
const myList = store.MyList.docs
const sessions = store.Watch_sessions.docs

// ---- Oracle seed mirror ------------------------------------------------------------------
const plans = [
  { planId: 1, name: 'Básico', price: 14900, maxProfiles: 1 },
  { planId: 2, name: 'Estándar', price: 24900, maxProfiles: 2 },
  { planId: 3, name: 'Premium', price: 34900, maxProfiles: 4 },
  { planId: 4, name: 'Familiar', price: 44900, maxProfiles: 6 },
]
const day = (n) => { const d = new Date(); d.setHours(0, 0, 0, 0); return new Date(d.getTime() + n * 864e5) }
const accounts = [
  [1, 'Juan Pérez', 'juan.perez@example.com'], [2, 'María Gómez', 'maria.gomez@example.com'],
  [3, 'Carlos Rodríguez', 'carlos.rodriguez@example.com'], [4, 'Laura Martínez', 'laura.martinez@example.com'],
  [5, 'Andrés Castro', 'andres.castro@example.com'], [6, 'Sofía Herrera', 'sofia.herrera@example.com'],
  [7, 'Daniel Torres', 'daniel.torres@example.com'], [8, 'Valentina Ríos', 'valentina.rios@example.com'],
  [9, 'Mateo Ospina', 'mateo.ospina@example.com'], [10, 'Camila Silva', 'camila.silva@example.com'],
].map(([accountId, fullName, email]) => ({ accountId, fullName, email }))
const profiles = [
  [1, 1, 'Juan', 18], [2, 1, 'Ana', 12], [3, 1, 'Niños', 7], [4, 2, 'María', 18], [5, 2, 'Kids', 7], [6, 3, 'Carlos', 18],
  [7, 5, 'Andrés', 18], [8, 5, 'Esposa', 18], [9, 5, 'Hijo 1', 12], [10, 5, 'Hijo 2', 12], [11, 5, 'Niña', 7], [12, 5, 'Invitado', 16],
  [13, 6, 'Sofía', 18], [14, 6, 'Pareja', 16], [15, 7, 'Daniel', 18], [16, 7, 'Hermano', 12, false], [17, 8, 'Valentina', 16],
  [18, 8, 'Antiguo', 7, false], [19, 9, 'Mateo', 18], [20, 9, 'Hermana', 12], [21, 9, 'Sobrino', 7], [22, 10, 'Camila', 18], [23, 10, 'Primo', 12],
].map(([profileId, accountId, name, maxAgeRating, active = true]) => ({ profileId, accountId, name, maxAgeRating, active }))
const subscriptions = [
  [1, 1, 3, -5, 25], [2, 2, 2, -10, 20], [3, 3, 1, -75, -45], [4, 5, 4, -2, 28], [5, 6, 2, -70, -40], [6, 6, 2, -40, -10],
  [7, 6, 3, -10, 20], [8, 7, 2, -50, -20], [9, 8, 1, -15, 15], [10, 9, 3, -60, -30], [11, 10, 2, -30, 0],
].map(([subscriptionId, accountId, planId, s, e]) => ({ subscriptionId, accountId, planId, startsAt: day(s), endsAt: day(e) }))
const admins = [['a1', 'admin@cineconecta.com', 'Administración CineConecta'], ['a2', 'catalogo@cineconecta.com', 'Equipo de catálogo']]
let counters = { MOV: 20, SER: 20, DOC: 20 }

// ---- Helpers -------------------------------------------------------------------------------
const now = () => new Date()
const activeSub = (accountId) => subscriptions.find((s) => s.accountId === accountId && s.startsAt <= now() && now() < s.endsAt)
const planName = (id) => plans.find((p) => p.planId === id)?.name
const iso = (d) => new Date(d).toISOString().slice(0, 19)
const err = (status, code, message, details) => ({ status, body: { code, message, timestamp: new Date().toISOString(), ...(details ? { details } : {}) } })
const ok = (body, status = 200) => ({ status, body })
const summary = (c) => ({ id: c._id, type: c.type, status: c.status, title: c.title, releaseYear: c.releaseYear, genres: c.genres, ageRating: c.ageRating, coverUrl: c.coverUrl })
const detail = (c) => ({
  ...summary(c), synopsis: c.synopsis, productionCountries: c.productionCountries, audioLanguages: c.audioLanguages,
  subtitleLanguages: c.subtitleLanguages, movie: c.movie ?? null, documentary: c.documentary ?? null,
  series: c.series ? { seasons: c.series.seasons.map((s) => ({ ...s, episodes: s.episodes.map((e) => ({ ...e, id: e._id })) })) } : null,
})
const duration = (c, ep) => c.movie?.durationSeconds ?? c.documentary?.durationSeconds
  ?? c.series.seasons.flatMap((s) => s.episodes).find((e) => e._id === ep)?.durationSeconds
const state = (s) => ({ sessionId: s._id, contentId: s.contentId, episodeId: s.episodeId ?? null, contentType: s.contentType, state: s.state, watchedSeconds: s.watchedSeconds, lastPositionSeconds: s.lastPositionSeconds, durationSeconds: s.durationSeconds, completed: s.completed })
const elapsed = (s) => Math.max(0, Math.min(Math.floor((now() - new Date(s.playingSince)) / 1000), s.durationSeconds - s.lastPositionSeconds))
const complete = (s) => { s.completed = s.completed || s.lastPositionSeconds >= 0.9 * s.durationSeconds }

function auth(req) {
  const token = (req.headers.authorization ?? '').replace('Bearer ', '')
  const [role, id] = token.split(':')
  return role === 'client' ? { role: 'CLIENT', accountId: Number(id) } : role === 'admin' ? { role: 'ADMIN', id } : null
}
function profileOf(req, user) {
  const pid = Number(req.headers['x-profile-id'])
  if (!pid) return err(400, 'BAD_REQUEST', 'Falta el encabezado X-Profile-Id')
  const p = profiles.find((x) => x.profileId === pid && x.accountId === user.accountId)
  if (!p) return err(403, 'FORBIDDEN', 'El perfil no pertenece a la cuenta')
  if (!p.active) return err(422, 'BUSINESS_RULE', 'El perfil está desactivado: actívelo o elija otro')
  return p
}
const NO_SUB = () => err(422, 'BUSINESS_RULE', 'Se necesita una suscripción vigente para esta operación')
const NOT_AVAILABLE = () => err(403, 'FORBIDDEN', 'El contenido no existe o no está disponible')

// ---- Routes --------------------------------------------------------------------------------
function route(method, url, req, body) {
  const p = url.pathname
  const q = Object.fromEntries(url.searchParams)
  let m

  if (method === 'POST' && p === '/api/auth/login') {
    const a = accounts.find((x) => x.email === body.email?.toLowerCase())
    if (!a || body.password !== 'Cliente123') return err(401, 'BAD_CREDENTIALS', 'Correo o contraseña incorrectos')
    return ok({ token: `client:${a.accountId}`, expiresAt: day(1), role: 'CLIENT', fullName: a.fullName })
  }
  if (method === 'POST' && p === '/api/auth/register') {
    if (accounts.some((x) => x.email === body.email?.toLowerCase())) return err(409, 'CONFLICT', 'Ya existe una cuenta con ese correo')
    const a = { accountId: accounts.length + 1, fullName: body.fullName, email: body.email.toLowerCase() }
    accounts.push(a)
    return ok({ token: `client:${a.accountId}`, expiresAt: day(1), role: 'CLIENT', fullName: a.fullName }, 201)
  }
  if (method === 'POST' && p === '/api/admin/auth/login') {
    const a = admins.find((x) => x[1] === body.email?.toLowerCase())
    if (!a || body.password !== 'Admin123') return err(401, 'BAD_CREDENTIALS', 'Correo o contraseña incorrectos')
    return ok({ token: `admin:${a[0]}`, expiresAt: day(1), role: 'ADMIN', fullName: a[2] })
  }
  if (method === 'GET' && p === '/api/plans') return ok(plans)

  const user = auth(req)
  if (!user) return err(401, 'UNAUTHORIZED', 'Inicie sesión: falta el token o no es válido')

  // ---- admin
  if (p.startsWith('/api/admin/')) {
    if (user.role !== 'ADMIN') return err(403, 'FORBIDDEN', 'No tiene permiso para esta operación')
    if (method === 'GET' && p === '/api/admin/content') {
      const size = Math.min(Number(q.size ?? 20), 100), page = Number(q.page ?? 0)
      const all = contents.filter((c) => (!q.status || c.status === q.status) && (!q.type || c.type === q.type)).sort((a, b) => a._id.localeCompare(b._id))
      return ok({ items: all.slice(page * size, page * size + size).map(summary), page, size, totalItems: all.length })
    }
    if ((m = p.match(/^\/api\/admin\/content\/([^/]+)$/))) {
      const c = contents.find((x) => x._id === m[1])
      if (!c) return err(404, 'NOT_FOUND', `El contenido no existe: ${m[1]}`)
      if (method === 'GET') return ok(detail(c))
      if (method === 'PUT') { Object.assign(c, fromRequest(body, c._id)); return ok(detail(c)) }
    }
    if (method === 'POST' && p === '/api/admin/content') {
      const prefix = { MOVIE: 'MOV', SERIES: 'SER', DOCUMENTARY: 'DOC' }[body.type]
      const id = `${prefix}-${String(++counters[prefix]).padStart(5, '0')}`
      const c = { _id: id, status: 'DRAFT', ...fromRequest(body, id) }
      contents.push(c)
      return ok(detail(c), 201)
    }
    if ((m = p.match(/^\/api\/admin\/content\/([^/]+)\/(publish|withdraw)$/))) {
      const c = contents.find((x) => x._id === m[1])
      c.status = m[2] === 'publish' ? 'PUBLISHED' : 'WITHDRAWN'
      return ok(detail(c))
    }
    if (method === 'GET' && (m = p.match(/^\/api\/admin\/reports\/(income|consumption)$/))) {
      const from = new Date(`${q.from}T00:00`), to = new Date(new Date(`${q.to}T00:00`).getTime() + 864e5)
      let rows, csv
      if (m[1] === 'income') {
        rows = plans.map((pl) => {
          const subs = subscriptions.filter((s) => s.planId === pl.planId && s.startsAt >= from && s.startsAt < to)
          return { planId: pl.planId, planName: pl.name, subscriptionsCount: subs.length, totalIncome: subs.length * pl.price }
        }).filter((r) => r.subscriptionsCount > 0)
        csv = 'planId,planName,subscriptionsCount,totalIncome\n' + rows.map((r) => [r.planId, r.planName, r.subscriptionsCount, r.totalIncome].join(',')).join('\n')
      } else {
        const groups = {}
        sessions.filter((s) => new Date(s.startedAt) >= from && new Date(s.startedAt) < to).forEach((s) => {
          const g = (groups[`${s.planId}|${s.contentType}`] ??= { planId: s.planId, contentType: s.contentType, sec: 0, profiles: new Set() })
          g.sec += s.watchedSeconds; g.profiles.add(s.profileId)
        })
        rows = Object.values(groups).sort((a, b) => a.planId - b.planId || a.contentType.localeCompare(b.contentType))
          .map((g) => ({ planId: g.planId, planName: planName(g.planId), contentType: g.contentType, minutesWatched: Math.round(g.sec / 6) / 10, distinctProfiles: g.profiles.size }))
        csv = 'planId,planName,contentType,minutesWatched,distinctProfiles\n' + rows.map((r) => [r.planId, r.planName, r.contentType, r.minutesWatched, r.distinctProfiles].join(',')).join('\n')
      }
      return q.format === 'csv' ? { status: 200, csv, filename: `${m[1]}.csv` } : ok(rows)
    }
    return err(404, 'NOT_FOUND', 'No existe')
  }
  if (user.role !== 'CLIENT') return err(403, 'FORBIDDEN', 'No tiene permiso para esta operación')
  const acc = user.accountId

  if (method === 'GET' && p === '/api/auth/me') return ok({ id: String(acc), role: 'CLIENT' })
  if (method === 'GET' && p === '/api/subscriptions') return ok(subscriptions.filter((s) => s.accountId === acc).sort((a, b) => b.startsAt - a.startsAt).map((s) => ({ ...s, startsAt: iso(s.startsAt), endsAt: iso(s.endsAt), planName: planName(s.planId), active: s === activeSub(acc) })))
  if (method === 'GET' && p === '/api/subscriptions/current') { const s = activeSub(acc); return s ? ok({ ...s, startsAt: iso(s.startsAt), endsAt: iso(s.endsAt), planName: planName(s.planId), active: true }) : err(404, 'NOT_FOUND', 'La cuenta no tiene una suscripción vigente') }
  if (method === 'POST' && p === '/api/subscriptions') {
    if (!body.approvePayment) return err(402, 'PAYMENT_REJECTED', 'El pago fue rechazado: no se registró ninguna suscripción')
    if (activeSub(acc)) return err(409, 'SUBSCRIPTION_OVERLAP', 'La cuenta ya tiene una suscripción vigente o con período superpuesto')
    const s = { subscriptionId: subscriptions.length + 1, accountId: acc, planId: body.planId, startsAt: now(), endsAt: day(30) }
    subscriptions.push(s)
    return ok({ ...s, startsAt: iso(s.startsAt), endsAt: iso(s.endsAt), planName: planName(s.planId), active: true }, 201)
  }
  if (method === 'GET' && p === '/api/payments') return ok(subscriptions.filter((s) => s.accountId === acc).map((s) => ({ paymentId: s.subscriptionId, subscriptionId: s.subscriptionId, planName: planName(s.planId), paidAt: iso(s.startsAt), amount: plans.find((x) => x.planId === s.planId).price, reference: `SIM-${String(s.subscriptionId).padStart(8, '0')}` })))
  if (method === 'GET' && p === '/api/profiles') return ok(profiles.filter((x) => x.accountId === acc))
  if (method === 'POST' && p === '/api/profiles') {
    const sub = activeSub(acc)
    if (!sub) return err(422, 'BUSINESS_RULE', 'Se necesita una suscripción vigente para tener perfiles activos')
    const pl = plans.find((x) => x.planId === sub.planId)
    const active = profiles.filter((x) => x.accountId === acc && x.active).length
    if (active >= pl.maxProfiles) return err(422, 'BUSINESS_RULE', `El plan ${pl.name} admite ${pl.maxProfiles} perfiles activos y ya hay ${active}. Desactive uno o cambie de plan`)
    const np = { profileId: profiles.length + 1, accountId: acc, name: body.name, maxAgeRating: body.maxAgeRating, active: true }
    profiles.push(np); return ok(np, 201)
  }
  if ((m = p.match(/^\/api\/profiles\/(\d+)\/(deactivate|activate)$/))) {
    const pr = profiles.find((x) => x.profileId === Number(m[1]) && x.accountId === acc)
    if (!pr) return err(403, 'FORBIDDEN', 'El perfil no pertenece a la cuenta')
    pr.active = m[2] === 'activate'; return ok(pr)
  }
  if (method === 'GET' && p === '/api/profiles/current') { const pr = profileOf(req, user); return pr.status ? pr : ok(pr) }

  // ---- profile-scoped
  const prof = profileOf(req, user)
  if (prof.status) return prof

  if (method === 'GET' && p === '/api/catalog') {
    if (!activeSub(acc)) return NO_SUB()
    const size = Number(q.size ?? 20), page = Number(q.page ?? 0)
    const all = contents.filter((c) => c.status === 'PUBLISHED' && c.ageRating <= prof.maxAgeRating
      && (!q.type || c.type === q.type) && (!q.genre || c.genres.includes(q.genre))
      && (!q.language || c.audioLanguages.includes(q.language)) && (!q.year || c.releaseYear === Number(q.year))
      && (!q.title || c.title.toLowerCase().includes(q.title.toLowerCase()))).sort((a, b) => b.releaseYear - a.releaseYear)
    return ok({ items: all.slice(page * size, page * size + size).map(summary), page, size, totalItems: all.length })
  }
  if (method === 'GET' && (m = p.match(/^\/api\/catalog\/([^/]+)$/))) {
    if (!activeSub(acc)) return NO_SUB()
    const c = contents.find((x) => x._id === m[1])
    if (!c || c.status !== 'PUBLISHED' || c.ageRating > prof.maxAgeRating) return NOT_AVAILABLE()
    return ok(detail(c))
  }
  if (method === 'GET' && p === '/api/my-list') return ok(myList.filter((x) => x.profileId === prof.profileId).sort((a, b) => new Date(b.addedAt) - new Date(a.addedAt)).map((x) => { const c = contents.find((y) => y._id === x.contentId); return { contentId: x.contentId, title: c?.title, type: c?.type, coverUrl: c?.coverUrl, addedAt: x.addedAt, available: c?.status === 'PUBLISHED' } }))
  if (method === 'POST' && p === '/api/my-list') {
    if (myList.some((x) => x.profileId === prof.profileId && x.contentId === body.contentId)) return err(409, 'CONFLICT', 'El contenido ya está en Mi lista')
    const c = contents.find((x) => x._id === body.contentId)
    if (!c || c.status !== 'PUBLISHED' || c.ageRating > prof.maxAgeRating) return NOT_AVAILABLE()
    const item = { profileId: prof.profileId, contentId: c._id, addedAt: new Date() }
    myList.push(item); return ok({ contentId: c._id, title: c.title, type: c.type, coverUrl: c.coverUrl, addedAt: item.addedAt, available: true }, 201)
  }
  if (method === 'DELETE' && (m = p.match(/^\/api\/my-list\/([^/]+)$/))) {
    const i = myList.findIndex((x) => x.profileId === prof.profileId && x.contentId === m[1]); if (i >= 0) myList.splice(i, 1)
    return { status: 204 }
  }
  if (method === 'GET' && p === '/api/continue-watching') {
    const latest = {}
    sessions.filter((s) => s.profileId === prof.profileId).sort((a, b) => new Date(b.lastActivityAt) - new Date(a.lastActivityAt)).forEach((s) => { latest[s.contentId] ??= s })
    return ok(Object.values(latest).map((s) => ({ s, c: contents.find((x) => x._id === s.contentId) }))
      .filter(({ s, c }) => c?.status === 'PUBLISHED' && !s.completed)
      .map(({ s, c }) => ({ contentId: c._id, title: c.title, type: c.type, coverUrl: c.coverUrl, lastActivityAt: s.lastActivityAt, episodeId: s.episodeId ?? null, positionSeconds: s.lastPositionSeconds })))
  }
  if (method === 'POST' && p === '/api/playback/start') {
    const sub = activeSub(acc)
    if (!sub) return NO_SUB()
    const c = contents.find((x) => x._id === body.contentId)
    if (!c || c.status !== 'PUBLISHED' || c.ageRating > prof.maxAgeRating) return NOT_AVAILABLE()
    if (c.type === 'SERIES' && !body.episodeId) return err(422, 'BUSINESS_RULE', 'Las series se reproducen por episodio')
    const s = { _id: crypto.randomBytes(12).toString('hex'), profileId: prof.profileId, subscriptionId: sub.subscriptionId, planId: sub.planId, contentId: c._id, contentType: c.type, episodeId: body.episodeId, startedAt: now(), watchedSeconds: 0, lastPositionSeconds: 0, durationSeconds: duration(c, body.episodeId), completed: false, state: 'PLAYING', playingSince: now(), lastActivityAt: now() }
    sessions.push(s); return ok(state(s), 201)
  }
  if ((m = p.match(/^\/api\/playback\/([^/]+)\/(pause|resume|seek|heartbeat|finish)$/))) {
    const s = sessions.find((x) => x._id === m[1] && x.profileId === prof.profileId)
    if (!s) return err(403, 'FORBIDDEN', 'La sesión no pertenece al perfil')
    const act = m[2]
    if ((act === 'seek' || act === 'resume') && s.state === 'ENDED') return err(422, 'BUSINESS_RULE', 'La sesión ya finalizó')
    if (s.state === 'PLAYING' && act !== 'resume') { const e = elapsed(s); s.watchedSeconds += e; s.lastPositionSeconds += e; s.playingSince = now() }
    if (act === 'pause' && s.state === 'PLAYING') { s.state = 'PAUSED'; delete s.playingSince }
    if (act === 'resume' && s.state === 'PAUSED') { s.state = 'PLAYING'; s.playingSince = now() }
    if (act === 'seek') s.lastPositionSeconds = Math.max(0, Math.min(body.positionSeconds, s.durationSeconds))
    if (act === 'finish' && s.state !== 'ENDED') { s.state = 'ENDED'; s.endedAt = now(); s.completed = true; delete s.playingSince }
    complete(s); s.lastActivityAt = now()
    return ok(state(s))
  }
  return err(404, 'NOT_FOUND', `No existe ${method} ${p}`)
}

function fromRequest(b, id) {
  return {
    type: b.type, title: b.title, synopsis: b.synopsis, releaseYear: b.releaseYear, genres: b.genres, productionCountries: b.productionCountries,
    audioLanguages: b.audioLanguages, subtitleLanguages: b.subtitleLanguages ?? [], ageRating: b.ageRating, coverUrl: b.coverUrl,
    movie: b.movie ?? undefined, documentary: b.documentary ?? undefined,
    series: b.series ? { seasons: b.series.seasons.map((s) => ({ ...s, episodes: s.episodes.map((e) => ({ ...e, _id: `${id}-S${String(s.seasonNumber).padStart(2, '0')}E${String(e.number).padStart(2, '0')}` })) })) } : undefined,
  }
}

http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', req.headers.origin ?? '*')
  res.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type, X-Profile-Id')
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS')
  res.setHeader('Access-Control-Expose-Headers', 'Content-Disposition')
  if (req.method === 'OPTIONS') { res.writeHead(200); return res.end() }
  let raw = ''
  req.on('data', (c) => (raw += c))
  req.on('end', () => {
    let out
    try { out = route(req.method, new URL(req.url, 'http://x'), req, raw ? JSON.parse(raw) : {}) } catch (e) { out = err(500, 'INTERNAL_ERROR', String(e)) }
    if (out.csv !== undefined) {
      res.writeHead(200, { 'Content-Type': 'text/csv', 'Content-Disposition': `attachment; filename="${out.filename}"` })
      return res.end(out.csv)
    }
    if (out.status === 204) { res.writeHead(204); return res.end() }
    res.writeHead(out.status, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify(out.body))
  })
}).listen(PORT, () => {
  console.log(`CineConecta mock API (dev only) on http://localhost:${PORT}`)
  console.log(`  ${contents.length} contents and ${sessions.length} sessions from 03_seed.js`)
  console.log('  logins: the seed accounts 1-10 and the two seed admins (see the seed comments)')
})
