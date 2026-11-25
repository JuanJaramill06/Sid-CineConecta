// =============================================================================
// CINECONECTA - MongoDB: datos sintéticos funcionales
//
// Ejecutar después de 01 y 02 (y del seed de Oracle):
//   mongosh "$env:MONGO_URI" --file database/mongodb/03_seed.js
//
// Reejecutable: borra solo lo suyo (los 20 contenidos del seed por id exacto, MyList y
// sesiones de los perfiles 1-23, los 2 admins) y vuelve a insertar.
//
// Fechas relativas a HOY, igual que 01_cineconecta_oracle.sql: cargar Oracle y Mongo el
// MISMO día para que cada sesión caiga dentro del período de su subscriptionId.
//
// Ids de Oracle usados (deben existir: RNF6): perfiles 1-23, suscripciones 1-11, planes 1-4.
// Contraseña de los admins: Admin123 (BCrypt cost 10).
// =============================================================================
db = db.getSiblingDB('cineconecta');

function check(cond, msg) { if (!cond) throw new Error('ASSERT FAILED: ' + msg); }

const TODAY = new Date(); TODAY.setHours(0, 0, 0, 0);
const DAY = 86400000;
// Día relativo a hoy (puede ser negativo) + hora local
const at = (day, hour = 20, min = 0) => new Date(TODAY.getTime() + day * DAY + (hour * 60 + min) * 60000);
const NOW = new Date(); // ninguna actividad del seed puede quedar en el futuro

// ---- Copia de lo que define el seed de Oracle (solo para validar el seed aquí) ----
const PROFILE_MAX_AGE = { 1: 18, 2: 12, 3: 7, 4: 18, 5: 7, 6: 18, 7: 18, 8: 18, 9: 12, 10: 12, 11: 7,
  12: 16, 13: 18, 14: 16, 15: 18, 16: 12, 17: 16, 18: 7, 19: 18, 20: 12, 21: 7, 22: 18, 23: 12 };
// subscriptionId -> [planId, startsAt día, endsAt día] (vigencia [inicio, fin))
const SUBS = { 1: [3, -5, 25], 2: [2, -10, 20], 3: [1, -75, -45], 4: [4, -2, 28], 5: [2, -70, -40],
  6: [2, -40, -10], 7: [3, -10, 20], 8: [2, -50, -20], 9: [1, -15, 15], 10: [3, -60, -30], 11: [2, -30, 0] };

// =============================================================================
// 1. CONTENIDOS (20): 8 películas, 6 series, 6 documentales
//    2 DRAFT (MOV-00007, SER-00006) y 2 WITHDRAWN (MOV-00006, SER-00005)
// =============================================================================
const created = at(-120, 9);
function base(id, type, status, title, synopsis, year, genres, rating, extra = {}) {
  return {
    _id: id, type, status, title, synopsis, releaseYear: year, genres,
    productionCountries: extra.countries || ['Colombia'],
    audioLanguages: extra.audio || ['es'],
    subtitleLanguages: extra.subs || ['es', 'en'],
    ageRating: rating,
    coverUrl: `https://img.cineconecta.test/${id}.jpg`,
    createdAt: created, updatedAt: created,
  };
}
const movie = (id, status, title, syn, year, genres, rating, minutes, directors, cast, extra) =>
  ({ ...base(id, 'MOVIE', status, title, syn, year, genres, rating, extra),
     movie: { durationSeconds: minutes * 60, directors, cast } });
const doc = (id, status, title, syn, year, genres, rating, minutes, directors, topics, interviewees, sources, extra) => {
  const d = { durationSeconds: minutes * 60, directors, topics, interviewees };
  if (sources) d.sources = sources; // solo cuando corresponde
  return { ...base(id, 'DOCUMENTARY', status, title, syn, year, genres, rating, extra), documentary: d };
};
// seasons: [[episodios, descripción], ...]; todos los episodios duran epMinutes
function series(id, status, title, syn, year, genres, rating, epMinutes, seasons, extra) {
  return {
    ...base(id, 'SERIES', status, title, syn, year, genres, rating, extra),
    series: {
      seasons: seasons.map(([count, description], si) => ({
        seasonNumber: si + 1,
        description,
        episodes: Array.from({ length: count }, (_, ei) => {
          const code = `S${String(si + 1).padStart(2, '0')}E${String(ei + 1).padStart(2, '0')}`;
          return { _id: `${id}-${code}`, number: ei + 1, title: `${title} ${code}`,
                   synopsis: `Episodio ${ei + 1} de la temporada ${si + 1} de ${title}.`,
                   durationSeconds: epMinutes * 60 };
        }),
      })),
    },
  };
}

const contents = [
  movie('MOV-00001', 'PUBLISHED', 'El último tren a Bogotá', 'Un maquinista retirado hace su último viaje.', 2021,
    ['Drama'], 12, 110, ['Laura Restrepo Díaz'],
    [{ actor: 'Andrés Parra', character: 'Ernesto' }, { actor: 'Marcela Benjumea', character: 'Inés' }]),
  movie('MOV-00002', 'PUBLISHED', 'Noche de lobos', 'Un thriller en una hacienda aislada.', 2019,
    ['Thriller', 'Terror'], 18, 98, ['Ciro Guerra Peña'],
    [{ actor: 'Juan Pablo Raba', character: 'Tomás' }, { actor: 'Actor sin acreditar' }]),
  movie('MOV-00003', 'PUBLISHED', 'La isla de los colores', 'Animación para toda la familia.', 2022,
    ['Animación', 'Familiar'], 0, 85, ['Sofía Vargas'], [{ actor: 'Voces de Colombia', character: 'Narrador' }],
    { audio: ['es', 'en'] }),
  movie('MOV-00004', 'PUBLISHED', 'Código rojo', 'Una hacker descubre una conspiración bancaria.', 2023,
    ['Acción', 'Thriller'], 16, 124, ['Diego Ramírez'], [{ actor: 'Carolina Gaitán', character: 'Valeria' }],
    { countries: ['Colombia', 'México'], audio: ['es', 'en'] }),
  movie('MOV-00005', 'PUBLISHED', 'Aventuras en el páramo', 'Tres niños buscan un frailejón mágico.', 2020,
    ['Aventura', 'Familiar'], 7, 92, ['Pedro Gómez'], [{ actor: 'Elenco infantil', character: 'Los niños' }]),
  movie('MOV-00006', 'WITHDRAWN', 'Sombras del pasado', 'Un detective reabre un caso de 1990.', 2018,
    ['Drama', 'Crimen'], 16, 105, ['Luis Ospina'], [{ actor: 'Damián Alcázar', character: 'Inspector Rojas' }]),
  movie('MOV-00007', 'DRAFT', 'Proyecto Cóndor', 'Ciencia ficción en los Andes (en preparación).', 2026,
    ['Ciencia ficción'], 12, 115, ['Ana Lucía Pardo'], []),
  movie('MOV-00008', 'PUBLISHED', 'Amor en Cartagena', 'Comedia romántica en la ciudad amurallada.', 2024,
    ['Comedia', 'Romance'], 12, 101, ['Camila Torres'],
    [{ actor: 'Majida Issa', character: 'Lucía' }, { actor: 'Manolo Cardona', character: 'Sebastián' }],
    { countries: ['Colombia', 'España'], audio: ['es'], subs: ['es', 'en', 'pt'] }),

  series('SER-00001', 'PUBLISHED', 'Crónicas del Caribe', 'Tres generaciones de una familia costeña.', 2020,
    ['Drama'], 16, 45, [[3, 'Los orígenes'], [3, 'La herencia'], [2, 'El regreso']]),
  series('SER-00002', 'PUBLISHED', 'Los detectives de Medellín', 'Una unidad especial resuelve crímenes.', 2022,
    ['Crimen', 'Thriller'], 18, 50, [[3, 'Primer caso'], [3, 'La red']], { audio: ['es', 'en'] }),
  series('SER-00003', 'PUBLISHED', 'Pequeños exploradores', 'Ciencia para niños.', 2021,
    ['Infantil', 'Educativo'], 0, 20, [[2, 'El bosque'], [2, 'El mar']]),
  series('SER-00004', 'PUBLISHED', 'Hacienda', 'Intrigas en una finca cafetera.', 2023,
    ['Drama'], 12, 42, [[4, 'La cosecha'], [3, 'La tormenta']]),
  series('SER-00005', 'WITHDRAWN', 'Frontera', 'Contrabando en la frontera (retirada).', 2017,
    ['Acción'], 16, 48, [[3, 'El paso'], [2, 'La caída']]),
  series('SER-00006', 'DRAFT', 'Startup', 'Emprendedores en Bogotá (en preparación).', 2026,
    ['Comedia'], 12, 30, [[2, 'El pitch'], [2, 'La ronda']]),

  doc('DOC-00001', 'PUBLISHED', 'Amazonía viva', 'La selva vista por sus guardianes.', 2021,
    ['Naturaleza'], 0, 80, ['Marta Rodríguez'], ['Biodiversidad', 'Pueblos indígenas'],
    ['Líder Murui', 'Bióloga del Sinchi'], ['Instituto Sinchi', 'Parques Nacionales']),
  doc('DOC-00002', 'PUBLISHED', 'El café que nos une', 'Del grano a la taza.', 2019,
    ['Cultura', 'Gastronomía'], 7, 70, ['Jorge Silva'], ['Café', 'Economía rural'],
    ['Caficultora del Huila'], ['Federación Nacional de Cafeteros']),
  doc('DOC-00003', 'PUBLISHED', 'Voces de la guerra', 'Testimonios del conflicto armado.', 2020,
    ['Historia'], 16, 95, ['Patricia Ariza'], ['Conflicto', 'Memoria', 'Paz'],
    ['Excombatiente', 'Víctima', 'Periodista'], ['Centro Nacional de Memoria Histórica']),
  doc('DOC-00004', 'PUBLISHED', 'Cumbia: historia de un ritmo', 'El viaje de la cumbia por América.', 2022,
    ['Música'], 7, 75, ['Andrés Caicedo Ruiz'], ['Música', 'Identidad'], ['Músico de gaitas', 'Musicóloga'], null),
  doc('DOC-00005', 'PUBLISHED', 'Océanos de plástico', 'La contaminación en el Pacífico.', 2023,
    ['Naturaleza', 'Ciencia'], 12, 60, ['Natalia Orozco'], ['Contaminación', 'Océanos'],
    ['Oceanógrafa', 'Pescador de Buenaventura'], ['Invemar'], { audio: ['es', 'en'] }),
  doc('DOC-00006', 'PUBLISHED', 'Ciudades del futuro', 'Urbanismo sostenible en Latinoamérica.', 2024,
    ['Ciencia', 'Sociedad'], 7, 65, ['Felipe Aljure'], ['Urbanismo', 'Movilidad'],
    ['Arquitecta', 'Alcalde'], null, { countries: ['Colombia', 'Chile'] }),
];

// =============================================================================
// 2. MI LISTA
// =============================================================================
const myList = [
  [1, 'MOV-00002', -4], [1, 'SER-00001', -3],
  [1, 'MOV-00006', -30],              // retirado: se muestra como "no disponible" (RF39)
  [2, 'MOV-00008', -2], [2, 'DOC-00005', -2],
  [3, 'MOV-00003', -1], [3, 'SER-00003', -1],
  [4, 'SER-00002', -3], [4, 'DOC-00003', -2],
  [6, 'MOV-00004', -50], [6, 'DOC-00002', -50], // cuenta 3 vencida: rechazo al reproducir
  [13, 'SER-00004', -60],
  [16, 'MOV-00001', -45],             // perfil inactivo: su lista se conserva
  [22, 'MOV-00008', -20],             // cuenta 10: venció hoy a las 00:00
].map(([profileId, contentId, day]) => ({ profileId, contentId, addedAt: at(day, 19) }));

// =============================================================================
// 3. SESIONES
//    pct: posición final / duración. watchedPct: tiempo visto (sin saltos, RF53).
//    open: true -> sesión en pausa sin finalizar (aparece en "Continuar viendo").
// =============================================================================
const byId = Object.fromEntries(contents.map(c => [c._id, c]));
function durationOf(contentId, episodeId) {
  const c = byId[contentId];
  if (c.type === 'MOVIE') return c.movie.durationSeconds;
  if (c.type === 'DOCUMENTARY') return c.documentary.durationSeconds;
  for (const s of c.series.seasons) for (const e of s.episodes) if (e._id === episodeId) return e.durationSeconds;
  throw new Error(`Episodio ${episodeId} no existe en ${contentId}`);
}
function session({ p, sub, c, ep, day, hour = 20, pct = 1, watchedPct = pct, open = false }) {
  const content = byId[c];
  const duration = durationOf(c, ep);
  const startedAt = at(day, hour);
  const pos = Math.round(duration * pct);
  const watched = Math.round(duration * watchedPct);
  const last = new Date(startedAt.getTime() + (watched + 300) * 1000); // +5 min de pausas
  const s = {
    profileId: p, subscriptionId: sub, planId: SUBS[sub][0],
    contentId: c, contentType: content.type,
    startedAt, watchedSeconds: watched, lastPositionSeconds: pos, durationSeconds: duration,
    completed: !open && pct >= 0.9,   // completada al finalizar o al llegar al 90 % (RF56)
    state: open ? 'PAUSED' : 'ENDED',
    lastActivityAt: last,
  };
  if (ep) s.episodeId = ep;
  if (!open) s.endedAt = last;
  return s;
}
const E = (series, s, e) => `${series}-S${String(s).padStart(2, '0')}E${String(e).padStart(2, '0')}`;

const sessions = [
  // Perfil 1 (cuenta 1, sub 1, plan Premium): Continuar viendo
  session({ p: 1, sub: 1, c: 'MOV-00002', day: -4 }),
  session({ p: 1, sub: 1, c: 'MOV-00006', day: -4, hour: 22, pct: 0.5, open: true }),     // retirado: NO aparece (RF61)
  session({ p: 1, sub: 1, c: 'SER-00001', ep: E('SER-00001', 1, 1), day: -3 }),
  session({ p: 1, sub: 1, c: 'SER-00001', ep: E('SER-00001', 1, 2), day: -2 }),
  session({ p: 1, sub: 1, c: 'SER-00001', ep: E('SER-00001', 1, 3), day: -1, hour: 21, pct: 0.4, open: true }), // sigue S01E03
  session({ p: 1, sub: 1, c: 'MOV-00001', day: -1, hour: 18, pct: 0.4, watchedPct: 0.25, open: true }),         // hubo un salto
  // Perfil 1 vuelve a ver una película completa: la sesión anterior se conserva (RF55)
  session({ p: 1, sub: 1, c: 'MOV-00002', day: -2, hour: 23, pct: 0.95 }),

  // Perfil 2 (sub 1): completada al pasar el 90 %
  session({ p: 2, sub: 1, c: 'DOC-00005', day: -2, pct: 0.92 }),
  // Perfil 3 "Niños" (máx. 7): último episodio de la serie -> la serie desaparece de Continuar viendo
  session({ p: 3, sub: 1, c: 'MOV-00003', day: -3, hour: 17 }),
  session({ p: 3, sub: 1, c: 'SER-00003', ep: E('SER-00003', 2, 1), day: -2, hour: 17 }),
  session({ p: 3, sub: 1, c: 'SER-00003', ep: E('SER-00003', 2, 2), day: -1, hour: 17 }),

  // Perfil 4 (sub 2, Estándar): terminó el último episodio de T1 -> se ofrece S02E01 desde 0
  session({ p: 4, sub: 2, c: 'SER-00002', ep: E('SER-00002', 1, 2), day: -3 }),
  session({ p: 4, sub: 2, c: 'SER-00002', ep: E('SER-00002', 1, 3), day: -2 }),
  session({ p: 4, sub: 2, c: 'DOC-00003', day: -1, pct: 0.6, open: true }),

  // Perfil 6 (cuenta 3, sub 3 vencida): historial antiguo
  session({ p: 6, sub: 3, c: 'MOV-00004', day: -60 }),
  // Cuenta 5 (sub 4, Familiar)
  session({ p: 7, sub: 4, c: 'MOV-00004', day: -1 }),
  session({ p: 11, sub: 4, c: 'MOV-00005', day: -1, hour: 16, pct: 0.3, open: true }),

  // Cuenta 6: mismo perfil bajo distintos planes (sub 5 y 6 Estándar, sub 7 Premium) -> informe de consumo
  session({ p: 13, sub: 5, c: 'SER-00004', ep: E('SER-00004', 1, 1), day: -60 }),
  session({ p: 13, sub: 5, c: 'SER-00004', ep: E('SER-00004', 1, 2), day: -55 }),
  session({ p: 13, sub: 6, c: 'SER-00004', ep: E('SER-00004', 1, 3), day: -25 }),
  session({ p: 14, sub: 6, c: 'MOV-00008', day: -20 }),
  session({ p: 13, sub: 7, c: 'SER-00004', ep: E('SER-00004', 1, 4), day: -5, pct: 0.7, open: true }),
  session({ p: 14, sub: 7, c: 'DOC-00006', day: -4 }),

  // Cuenta 7 (sub 8 vencida): el perfil 16 está INACTIVO y conserva su historial; serie retirada
  session({ p: 15, sub: 8, c: 'DOC-00001', day: -30 }),
  session({ p: 15, sub: 8, c: 'SER-00005', ep: E('SER-00005', 1, 1), day: -35 }),
  session({ p: 16, sub: 8, c: 'MOV-00001', day: -40 }),
  session({ p: 16, sub: 8, c: 'DOC-00002', day: -38 }),

  // Cuenta 8 (sub 9, Básico)
  session({ p: 17, sub: 9, c: 'DOC-00004', day: -10 }),
  session({ p: 17, sub: 9, c: 'MOV-00008', day: -3, pct: 0.55, open: true }),
  // Cuenta 9 (sub 10 vencida, Premium)
  session({ p: 19, sub: 10, c: 'SER-00001', ep: E('SER-00001', 1, 1), day: -50 }),
  session({ p: 20, sub: 10, c: 'MOV-00001', day: -45 }),
  // Cuenta 10 (sub 11 terminó hoy a las 00:00)
  session({ p: 22, sub: 11, c: 'MOV-00008', day: -20 }),
];

// =============================================================================
// 4. ADMINISTRADORES (independientes de las cuentas de clientes)
// =============================================================================
const ADMIN_HASH = '$2a$10$UZ2SYab5tslRTRMcwKVLX.TFlg7q7gyvgc5fIPBZ.OoiMIZXhhV66'; // Admin123
const admins = [
  { email: 'admin@cineconecta.com', fullName: 'Administrador General', passwordHash: ADMIN_HASH, createdAt: at(-200, 8) },
  { email: 'catalogo@cineconecta.com', fullName: 'Editora de Catálogo', passwordHash: ADMIN_HASH, createdAt: at(-100, 8) },
];

// =============================================================================
// 5. VALIDACIONES DEL SEED (lo que $jsonSchema no puede revisar)
// =============================================================================
for (const c of contents.filter(c => c.type === 'SERIES')) {
  const seasonNums = c.series.seasons.map(s => s.seasonNumber);
  check(new Set(seasonNums).size === seasonNums.length, `${c._id}: temporada repetida`);
  for (const s of c.series.seasons) {
    const nums = s.episodes.map(e => e.number);
    check(new Set(nums).size === nums.length, `${c._id} T${s.seasonNumber}: episodio repetido`);
  }
}
for (const s of sessions) {
  const [, from, to] = SUBS[s.subscriptionId];
  check(s.startedAt >= at(from, 0, 0) && s.startedAt < at(to, 0, 0),
    `sesión de ${s.profileId} fuera del período de la suscripción ${s.subscriptionId}`);
  check(byId[s.contentId].ageRating <= PROFILE_MAX_AGE[s.profileId],
    `perfil ${s.profileId} no puede ver ${s.contentId}`);
  check(byId[s.contentId].status !== 'DRAFT', `sesión sobre borrador ${s.contentId}`);
  check(s.lastActivityAt < NOW, 'sesión en el futuro');
}
for (const m of myList) check(byId[m.contentId], `MyList apunta a ${m.contentId} inexistente`);

// =============================================================================
// 6. CARGA (borra solo lo del seed)
// =============================================================================
const seedIds = contents.map(c => c._id);
db.Content.deleteMany({ _id: { $in: seedIds } });
db.MyList.deleteMany({ profileId: { $gte: 1, $lte: 23 } });
db.Watch_sessions.deleteMany({ profileId: { $gte: 1, $lte: 23 } });
db.Admin_user.deleteMany({ email: { $in: admins.map(a => a.email) } });

db.Content.insertMany(contents);
db.MyList.insertMany(myList);
db.Watch_sessions.insertMany(sessions);
db.Admin_user.insertMany(admins);

// Contadores: $max nunca los baja (correr este script después de 04_volume.js es seguro)
for (const [prefix, n] of [['MOV', 8], ['SER', 6], ['DOC', 6]]) {
  db.counters.updateOne({ _id: prefix }, { $max: { seq: n } }, { upsert: true });
}

check(db.Content.countDocuments({ _id: { $in: seedIds } }) === 20, '20 contenidos');
check(db.MyList.countDocuments({ profileId: { $lte: 23 } }) === myList.length, 'MyList');
check(db.Watch_sessions.countDocuments({ profileId: { $lte: 23 } }) === sessions.length, 'sesiones');
check(db.Admin_user.countDocuments({ email: { $in: admins.map(a => a.email) } }) === 2, 'admins');
print(`OK seed: 20 contenidos, ${myList.length} en Mi lista, ${sessions.length} sesiones, 2 admins`);
