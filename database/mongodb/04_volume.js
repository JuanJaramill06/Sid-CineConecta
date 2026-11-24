// =============================================================================
// CINECONECTA - MongoDB: carga de volumen para las mediciones de índices
//
// Ejecutar después de 03_seed.js y el MISMO día que database/oracle/04_volume_load.sql:
//   mongosh "$env:MONGO_URI" --file database/mongodb/04_volume.js
//
// Volumen: 5.000 contenidos (2.500 películas MOV-10001..12500, 1.500 series
// SER-10001..11500, 1.000 documentales DOC-10001..11000) y 200.000 sesiones de los
// perfiles 1001..26000. Cabe en Atlas M0 (512 MB).
// Reejecutable: borra solo esos rangos. Datos deterministas (PRNG con semilla fija).
//
// CONTRATO DE IDS CON ORACLE (copiado de 04_volume_load.sql; deben coincidir):
//   i = 0..9999, accountId = 1001 + i, perfiles = 2 + i%2
//   profileId = 1001 + 2i + floor(i/2) + j
//   maxAgeRating: j=0 -> 18, j=1 -> 16 si i%3==0 si no 12, j=2 -> 7
//   k = 0..11, subscriptionId = 1001 + 12i + k, vigencia [hoy-350+30k, hoy-320+30k)
//   planId: 2 perfiles -> 2 + (i+k)%3 ; 3 perfiles -> 3 + (i+k)%2
// =============================================================================
db = db.getSiblingDB('cineconecta');

function check(cond, msg) { if (!cond) throw new Error('ASSERT FAILED: ' + msg); }

// PRNG determinista (mulberry32): misma carga en cada ejecución
let seed = 20261124;
function rand() {
  seed = (seed + 0x6D2B79F5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
const pick = (arr) => arr[Math.floor(rand() * arr.length)];
const between = (lo, hi) => lo + Math.floor(rand() * (hi - lo + 1)); // entero en [lo, hi]

const TODAY = new Date(); TODAY.setHours(0, 0, 0, 0);
const DAY = 86400000;
const NOW = Date.now();

// ---- Contrato con Oracle ------------------------------------------------------
const ACCOUNTS = 10000, PROFILES = 25000, SUBS_PER_ACCOUNT = 12;
function profileAt(p) {               // p = 0..24999 -> cuenta i, posición j
  const block = Math.floor(p / 5), r = p % 5; // cada 2 cuentas suman 5 perfiles (2 + 3)
  return r < 2 ? { i: 2 * block, j: r } : { i: 2 * block + 1, j: r - 2 };
}
const profileId = (i, j) => 1001 + 2 * i + Math.floor(i / 2) + j;
const maxAge = (i, j) => (j === 0 ? 18 : j === 1 ? (i % 3 === 0 ? 16 : 12) : 7);
const subscriptionId = (i, k) => 1001 + SUBS_PER_ACCOUNT * i + k;
const planId = (i, k) => (i % 2 === 0 ? 2 + ((i + k) % 3) : 3 + ((i + k) % 2));
const subStartDay = (k) => -350 + 30 * k;

// Autocomprobación del contrato: los rangos coinciden con los de Oracle
check(profileId(0, 0) === 1001 && profileId(ACCOUNTS - 1, 2) === 26000, 'rango de perfiles');
for (const p of [0, 1, 2, 3, 4, 12345, PROFILES - 1]) {
  const { i, j } = profileAt(p);
  check(profileId(i, j) === 1001 + p && j < 2 + (i % 2), `profileAt(${p})`);
}
check(subscriptionId(ACCOUNTS - 1, 11) === 121000, 'rango de suscripciones');

// =============================================================================
// 1. CONTENIDOS (5.000)
// =============================================================================
const ADJ = ['oscura', 'eterna', 'perdida', 'roja', 'salvaje', 'secreta', 'última', 'dorada', 'silenciosa', 'lejana'];
const NOUN = ['noche', 'ciudad', 'frontera', 'montaña', 'memoria', 'isla', 'carretera', 'selva', 'familia', 'promesa',
  'tormenta', 'herencia', 'canción', 'fuga', 'travesía'];
const GENRES = ['Drama', 'Comedia', 'Acción', 'Thriller', 'Romance', 'Terror', 'Animación', 'Ciencia ficción',
  'Crimen', 'Aventura', 'Familiar', 'Historia', 'Música', 'Naturaleza'];
const COUNTRIES = ['Colombia', 'México', 'Argentina', 'Chile', 'España', 'Perú', 'Estados Unidos', 'Brasil'];
const LANGS = ['es', 'en', 'pt', 'fr'];
const RATINGS = [0, 7, 12, 16, 18];
const PEOPLE = ['Ana Ruiz', 'Carlos Mejía', 'Lucía Prieto', 'Mario Henao', 'Diana Cárdenas', 'Pablo Rincón',
  'Sara Quintero', 'Iván Salazar', 'Elena Mora', 'Jorge Vélez'];
const sample = (arr, n) => { const s = new Set(); while (s.size < n) s.add(pick(arr)); return [...s]; };
const created = new Date(TODAY.getTime() - 400 * DAY);

function baseDoc(id, type, n) {
  // 95 % publicados, 3 % retirados, 2 % borradores
  const r = rand();
  const status = r < 0.95 ? 'PUBLISHED' : r < 0.98 ? 'WITHDRAWN' : 'DRAFT';
  const audio = sample(LANGS, between(1, 2));
  return {
    _id: id, type, status,
    title: `La ${pick(NOUN)} ${pick(ADJ)} ${n}`,
    synopsis: `Contenido sintético ${id} para pruebas de volumen.`,
    releaseYear: between(1980, 2026),
    genres: sample(GENRES, between(1, 3)),
    productionCountries: sample(COUNTRIES, between(1, 2)),
    audioLanguages: audio,
    subtitleLanguages: sample(LANGS, between(0, 3)),
    ageRating: pick(RATINGS),
    coverUrl: `https://img.cineconecta.test/${id}.jpg`,
    createdAt: created, updatedAt: created,
  };
}

const contents = [];
for (let n = 10001; n <= 12500; n++) {
  const id = `MOV-${n}`;
  contents.push({ ...baseDoc(id, 'MOVIE', n), movie: {
    durationSeconds: between(80, 150) * 60, directors: sample(PEOPLE, 1),
    cast: sample(PEOPLE, between(1, 4)).map((actor, x) => (x % 3 === 2 ? { actor } : { actor, character: `Personaje ${x + 1}` })),
  } });
}
for (let n = 10001; n <= 11500; n++) {
  const id = `SER-${n}`;
  const epMinutes = between(20, 55);
  const seasons = Array.from({ length: between(1, 4) }, (_, si) => ({
    seasonNumber: si + 1,
    description: `Temporada ${si + 1}`,
    episodes: Array.from({ length: between(6, 10) }, (_, ei) => {
      const code = `S${String(si + 1).padStart(2, '0')}E${String(ei + 1).padStart(2, '0')}`;
      return { _id: `${id}-${code}`, number: ei + 1, title: `Episodio ${code}`,
               synopsis: `Episodio ${ei + 1} de la temporada ${si + 1}.`, durationSeconds: epMinutes * 60 };
    }),
  }));
  contents.push({ ...baseDoc(id, 'SERIES', n), series: { seasons } });
}
for (let n = 10001; n <= 11000; n++) {
  const id = `DOC-${n}`;
  const d = { durationSeconds: between(45, 110) * 60, directors: sample(PEOPLE, 1),
              topics: sample(GENRES, between(1, 3)), interviewees: sample(PEOPLE, between(1, 4)) };
  if (rand() < 0.7) d.sources = ['Universidad Nacional', 'Archivo General'].slice(0, between(1, 2));
  contents.push({ ...baseDoc(id, 'DOCUMENTARY', n), documentary: d });
}
check(contents.length === 5000, '5.000 contenidos');

// =============================================================================
// 2. SESIONES (200.000)
// =============================================================================
// Contenidos reproducibles por clasificación máxima (los retirados se vieron antes de retirarse)
const playable = contents.filter(c => c.status !== 'DRAFT');
const byMaxAge = {};
for (const a of [7, 12, 16, 18]) byMaxAge[a] = playable.filter(c => c.ageRating <= a);
const episodesOf = (c) => c.series.seasons.flatMap(s => s.episodes);
const durationOf = (c) => (c.type === 'MOVIE' ? c.movie.durationSeconds : c.documentary.durationSeconds);

const SESSIONS = 200000, BATCH = 5000;
function makeSession() {
  const { i, j } = profileAt(Math.floor(rand() * PROFILES));
  const k = Math.floor(rand() * SUBS_PER_ACCOUNT);
  const startDay = subStartDay(k);
  const lastDay = Math.min(startDay + 29, -1);           // dentro del período y antes de hoy
  const startedAt = new Date(TODAY.getTime() + between(startDay, lastDay) * DAY + between(8 * 60, 21 * 60) * 60000);

  const c = pick(byMaxAge[maxAge(i, j)]);
  let episodeId, duration;
  if (c.type === 'SERIES') { const e = pick(episodesOf(c)); episodeId = e._id; duration = e.durationSeconds; }
  else duration = durationOf(c);

  const done = rand() < 0.65;
  const pct = done ? 0.9 + rand() * 0.1 : 0.05 + rand() * 0.8;
  const pos = Math.round(duration * pct);
  const watched = Math.round(pos * (0.8 + rand() * 0.2)); // a veces hubo saltos (RF53)
  const lastActivityAt = new Date(startedAt.getTime() + (watched + between(0, 900)) * 1000);
  const open = !done && k === 11 && rand() < 0.3;        // pausadas recientes: Continuar viendo

  const s = {
    profileId: profileId(i, j), subscriptionId: subscriptionId(i, k), planId: planId(i, k),
    contentId: c._id, contentType: c.type,
    startedAt, watchedSeconds: watched, lastPositionSeconds: pos, durationSeconds: duration,
    completed: done, state: open ? 'PAUSED' : 'ENDED', lastActivityAt,
  };
  if (episodeId) s.episodeId = episodeId;
  if (!open) s.endedAt = lastActivityAt;
  return s;
}

// =============================================================================
// 3. CARGA (borra solo los rangos del volumen)
// =============================================================================
const volumeIds = { $or: [
  { _id: { $gte: 'MOV-10001', $lte: 'MOV-12500' } },
  { _id: { $gte: 'SER-10001', $lte: 'SER-11500' } },
  { _id: { $gte: 'DOC-10001', $lte: 'DOC-11000' } },
] };
db.Content.deleteMany(volumeIds);
db.Watch_sessions.deleteMany({ profileId: { $gte: 1001, $lte: 26000 } });

for (let b = 0; b < contents.length; b += 1000) db.Content.insertMany(contents.slice(b, b + 1000));
print('Contenidos: 5000');

const t0 = Date.now();
for (let b = 0; b < SESSIONS; b += BATCH) {
  const batch = Array.from({ length: BATCH }, makeSession);
  for (const s of batch) check(s.lastActivityAt.getTime() < NOW, 'sesión en el futuro');
  db.Watch_sessions.insertMany(batch, { ordered: false });
  print(`Sesiones: ${b + BATCH} (${Math.round((Date.now() - t0) / 1000)} s)`);
}

for (const [prefix, n] of [['MOV', 12500], ['SER', 11500], ['DOC', 11000]]) {
  db.counters.updateOne({ _id: prefix }, { $max: { seq: n } }, { upsert: true });
}

check(db.Content.countDocuments(volumeIds) === 5000, '5.000 contenidos de volumen');
check(db.Watch_sessions.countDocuments({ profileId: { $gte: 1001, $lte: 26000 } }) === SESSIONS, '200.000 sesiones');
const st = db.Watch_sessions.stats();
print(`OK volumen. Watch_sessions: ${st.count} docs, ${(st.size / 1048576).toFixed(1)} MB datos, ` +
      `${(st.totalIndexSize / 1048576).toFixed(1)} MB índices`);
