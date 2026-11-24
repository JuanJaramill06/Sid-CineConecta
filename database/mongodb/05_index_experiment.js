// =============================================================================
// CINECONECTA - MongoDB: experimento de índices (antes / después)
//
// Ejecutar después de 04_volume.js (necesita las 200.000 sesiones):
//   mongosh "$env:MONGO_URI" --file database/mongodb/05_index_experiment.js
//
// Para cada consulta mide SIN y CON su índice:
//   * Métrica principal: keys examinadas, documentos examinados y etapas del plan
//     (COLLSCAN / IXSCAN / FETCH / PROJECTION_COVERED). No dependen de la carga
//     del servidor.
//   * Métrica secundaria: mediana de 5 ejecuciones en ms (Atlas M0 es compartido:
//     el tiempo varía entre corridas).
// Los índices se quitan con dropIndex y se recrean en un finally con la misma
// definición: aunque algo falle, Atlas no queda sin índices.
// Al final imprime todos los resultados en JSON para docs/indices-y-optimizacion.md.
// =============================================================================
db = db.getSiblingDB('cineconecta');
const S = db.Watch_sessions, C = db.Content;
const RUNS = 5, DAY = 86400000;
const TODAY = new Date(); TODAY.setHours(0, 0, 0, 0);

function check(cond, msg) { if (!cond) throw new Error('ASSERT FAILED: ' + msg); }
check(S.countDocuments({ profileId: { $gte: 1001, $lte: 26000 } }) >= 100000, 'cargue primero 04_volume.js');

// ---- Utilidades de medición ---------------------------------------------------
function medianMs(fn) {
  const t = [];
  for (let i = 0; i < RUNS; i++) { const t0 = Date.now(); fn(); t.push(Date.now() - t0); }
  return t.sort((a, b) => a - b)[Math.floor(RUNS / 2)];
}

// executionStats está arriba (find, o agregación empujada al motor SBE) o
// dentro de stages[0].$cursor (motor clásico): se busca el primero.
function execStats(explain) {
  let found = null;
  (function walk(o) {
    if (found || !o || typeof o !== 'object') return;
    if ('totalDocsExamined' in o && 'totalKeysExamined' in o) { found = o; return; }
    for (const [k, v] of Object.entries(o)) if (k !== 'rejectedPlans' && k !== 'allPlansExecution') walk(v);
  })(explain);
  return found || {};
}

// Etapas del plan ganador, de la hoja a la raíz (ej. IXSCAN -> FETCH -> GROUP)
function planStages(explain) {
  const out = [];
  (function walk(o, inPlan) {
    if (!o || typeof o !== 'object') return;
    for (const [k, v] of Object.entries(o)) {
      if (k === 'rejectedPlans') continue;
      const p = inPlan || k === 'winningPlan';
      if (p && k === 'stage' && typeof v === 'string' && !out.includes(v)) out.push(v);
      walk(v, p);
    }
  })(explain, false);
  return out.reverse().join(' -> ') || '(sin plan)';
}

const results = [];
function measure(experiment, variant, explainFn, runFn) {
  const ex = explainFn();
  const st = execStats(ex);
  const r = {
    experiment, variant, plan: planStages(ex),
    keysExamined: st.totalKeysExamined, docsExamined: st.totalDocsExamined, nReturned: st.nReturned,
    covered: st.totalKeysExamined > 0 && st.totalDocsExamined === 0,
    medianMs: medianMs(runFn),
  };
  results.push(r);
  print(`  ${variant.padEnd(34)} keys=${String(r.keysExamined).padStart(7)}  docs=${String(r.docsExamined).padStart(7)}` +
        `  n=${String(r.nReturned).padStart(6)}  mediana=${String(r.medianMs).padStart(5)} ms  ${r.covered ? '[covered] ' : ''}${r.plan}`);
  return r;
}

// Quita el índice, ejecuta fn y lo recrea SIEMPRE con su definición original
function withoutIndex(coll, name, fn) {
  const spec = coll.getIndexes().find(i => i.name === name);
  check(spec, `falta el índice ${name}: ejecute 02_indexes.js`);
  const { key, v, ns, ...options } = spec;   // options conserva name, unique, partialFilterExpression...
  coll.dropIndex(name);
  try { fn(); } finally { coll.createIndex(key, options); }
}

const section = (t) => print(`\n=== ${t} ===`);

// ---- 0. Volumen ---------------------------------------------------------------
section('0. Volumen (db.collection.stats())');
for (const coll of [C, S]) {
  const st = coll.stats();
  print(`  ${coll.getName().padEnd(15)} ${st.count} docs, ${(st.size / 1048576).toFixed(1)} MB datos, ` +
        `promedio ${Math.round(st.avgObjSize)} B/doc, ${(st.storageSize / 1048576).toFixed(1)} MB en disco, ` +
        `${(st.totalIndexSize / 1048576).toFixed(1)} MB índices`);
  for (const [name, size] of Object.entries(st.indexSizes)) print(`      ${name.padEnd(32)} ${(size / 1048576).toFixed(2)} MB`);
}
const TOTAL = S.estimatedDocumentCount();

// ---- 1. Continuar viendo --------------------------------------------------------
// Mismo pipeline que 06_operations.js. El acceso que decide el costo es
// find({profileId}).sort({lastActivityAt:-1}): eso es lo que se explica.
const PROFILE = 1001;
const continueWatching = (profileId) => [
  { $match: { profileId } },
  { $sort: { lastActivityAt: -1 } },
  { $group: { _id: '$contentId', s: { $first: '$$ROOT' } } },
  { $replaceWith: '$s' },
  { $lookup: { from: 'Content', localField: 'contentId', foreignField: '_id', as: 'c',
               pipeline: [{ $project: { title: 1, status: 1, type: 1, coverUrl: 1,
                 episodes: { $reduce: { input: { $ifNull: ['$series.seasons', []] }, initialValue: [],
                   in: { $concatArrays: ['$$value', '$$this.episodes'] } } } } }] } },
  { $unwind: '$c' },
  { $match: { 'c.status': 'PUBLISHED' } },
  { $set: { next: { $let: {
      vars: { i: { $indexOfArray: ['$c.episodes._id', '$episodeId'] } },
      in: { $arrayElemAt: ['$c.episodes', { $add: ['$$i', 1] }] } } } } },
  { $match: { $or: [{ completed: false }, { 'c.type': 'SERIES', next: { $exists: true } }] } },
  { $sort: { lastActivityAt: -1 } },
  { $limit: 20 },
];
const cwExplain = () => S.find({ profileId: PROFILE }).sort({ lastActivityAt: -1 }).explain('executionStats');
const cwRun = () => S.aggregate(continueWatching(PROFILE)).toArray();

section(`1. Continuar viendo (perfil ${PROFILE}, ${S.countDocuments({ profileId: PROFILE })} sesiones de ${TOTAL})`);
withoutIndex(S, 'ix_sessions_profile_activity', () =>
  measure('continuar_viendo', 'sin índice', cwExplain, cwRun));
measure('continuar_viendo', 'con ix_sessions_profile_activity', cwExplain, cwRun);

// ---- 2. Informe de consumo: rango de 1 mes y de 1 año ---------------------------
const report = (from, to) => [
  { $match: { startedAt: { $gte: from, $lt: to } } },
  { $project: { _id: 0, planId: 1, contentType: 1, profileId: 1, watchedSeconds: 1 } },
  { $group: { _id: { planId: '$planId', contentType: '$contentType' },
              watchedSeconds: { $sum: '$watchedSeconds' }, profiles: { $addToSet: '$profileId' } } },
  { $project: { _id: 0, planId: '$_id.planId', contentType: '$_id.contentType',
                minutesWatched: { $round: [{ $divide: ['$watchedSeconds', 60] }, 1] },
                distinctProfiles: { $size: '$profiles' } } },
  { $sort: { planId: 1, contentType: 1 } },
];
const RANGES = [
  ['1 mes', new Date(TODAY.getTime() - 30 * DAY), TODAY],
  ['1 año', new Date(TODAY.getTime() - 366 * DAY), new Date(TODAY.getTime() + DAY)],
];
section('2. Informe de consumo por plan y tipo');
for (const [label, from, to] of RANGES) {
  const n = S.countDocuments({ startedAt: { $gte: from, $lt: to } });
  print(`  Rango ${label}: ${n} sesiones = ${(100 * n / TOTAL).toFixed(1)} % (selectividad)`);
}
withoutIndex(S, 'ix_sessions_report', () => {
  for (const [label, from, to] of RANGES) {
    measure(`informe_${label}`, `${label}, sin índice`,
      () => S.explain('executionStats').aggregate(report(from, to)), () => S.aggregate(report(from, to)).toArray());
  }
});
for (const [label, from, to] of RANGES) {
  measure(`informe_${label}`, `${label}, con ix_sessions_report`,
    () => S.explain('executionStats').aggregate(report(from, to)), () => S.aggregate(report(from, to)).toArray());
}

// ---- 3. Cuándo el índice deja de convenir ---------------------------------------
// Misma agregación pero contando sesiones completadas: "completed" NO está en el
// índice, así que ya no es covered y cada clave obliga a leer el documento (FETCH).
// Se fuerza cada camino con hint: índice frente a recorrido completo ($natural).
const reportCompleted = (from, to) => [
  { $match: { startedAt: { $gte: from, $lt: to } } },
  { $group: { _id: { planId: '$planId', contentType: '$contentType' },
              sessions: { $sum: 1 }, completed: { $sum: { $cond: ['$completed', 1, 0] } } } },
];
section('3. Informe NO covered (agrega "completed"): índice + FETCH frente a COLLSCAN');
for (const [label, from, to] of RANGES) {
  for (const [variant, hint] of [['hint índice', 'ix_sessions_report'], ['hint $natural (COLLSCAN)', { $natural: 1 }]]) {
    measure(`no_covered_${label}`, `${label}, ${variant}`,
      () => S.explain('executionStats').aggregate(reportCompleted(from, to), { hint }),
      () => S.aggregate(reportCompleted(from, to), { hint }).toArray());
  }
}

// ---- 4. Búsqueda por título: $regex frente a $text --------------------------------
// $text no se puede medir "sin índice" (sin índice de texto da error), así que se
// compara con la alternativa real: una expresión regular sin ancla, que no usa
// índice sobre title. "ultima" muestra además que $text ignora tildes y $regex no.
section('4. Búsqueda por título en Content');
for (const word of ['noche', 'ultima']) {
  const rx = { status: 'PUBLISHED', title: { $regex: word, $options: 'i' } };
  const tx = { status: 'PUBLISHED', $text: { $search: word } };
  measure(`busqueda_${word}`, `"${word}" con $regex`,
    () => C.find(rx, { title: 1 }).explain('executionStats'), () => C.find(rx, { title: 1 }).toArray());
  measure(`busqueda_${word}`, `"${word}" con $text`,
    () => C.find(tx, { title: 1 }).explain('executionStats'), () => C.find(tx, { title: 1 }).toArray());
}

// ---- Índices restaurados + resultados ------------------------------------------
const names = S.getIndexes().map(i => i.name);
check(names.includes('ix_sessions_profile_activity') && names.includes('ix_sessions_report'),
  'los índices de Watch_sessions no se restauraron');
print(`\nÍndices de Watch_sessions restaurados: ${names.join(', ')}`);
print('\nResultados (JSON):');
print(JSON.stringify(results, null, 1));
