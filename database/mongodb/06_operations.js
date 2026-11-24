// =============================================================================
// CINECONECTA - MongoDB: operaciones requeridas (referencia ejecutable)
//
// Ejecutar después de 03_seed.js:
//   mongosh "$env:MONGO_URI" --file database/mongodb/06_operations.js
//
// Son las consultas y escrituras que deben implementar los servicios del backend.
// Lo que viene de Oracle (perfil activo, maxAgeRating, suscripción vigente, planId)
// lo valida la app ANTES de llamar a Mongo; aquí se pasa como parámetro.
// El script limpia lo que escribe (salvo el número consumido en counters, ver §8).
// =============================================================================
db = db.getSiblingDB('cineconecta');
const show = (title, value) => { print(`\n== ${title}`); printjson(value); };

// -----------------------------------------------------------------------------
// 1. Búsqueda paginada del catálogo (RF32-34, RNF19)
//    Solo PUBLISHED y ageRating <= maxAgeRating del perfil (dato de Oracle).
// -----------------------------------------------------------------------------
function searchCatalog({ maxAgeRating, text, type, genre, language, year, page = 0, size = 10 }) {
  const filter = { status: 'PUBLISHED', ageRating: { $lte: maxAgeRating } };
  if (text) filter.$text = { $search: text };
  if (type) filter.type = type;
  if (genre) filter.genres = genre;
  if (language) filter.audioLanguages = language;
  if (year) filter.releaseYear = year;
  const sort = text ? { score: { $meta: 'textScore' }, _id: 1 } : { releaseYear: -1, _id: 1 };
  const projection = { title: 1, type: 1, releaseYear: 1, genres: 1, ageRating: 1, coverUrl: 1 };
  if (text) projection.score = { $meta: 'textScore' };
  return {
    items: db.Content.find(filter, projection).sort(sort).skip(page * size).limit(size).toArray(),
    page, size, totalItems: db.Content.countDocuments(filter),
  };
}
show('Perfil 1 (máx. 18) busca "noche"', searchCatalog({ maxAgeRating: 18, text: 'noche' }));
show('Perfil 3 "Niños" (máx. 7) busca "noche": no ve contenidos de 18', searchCatalog({ maxAgeRating: 7, text: 'noche' }));
show('Perfil 3, solo películas', searchCatalog({ maxAgeRating: 7, type: 'MOVIE' }).items.map(c => `${c._id} ${c.ageRating}`));
show('Filtro género Drama + idioma es', searchCatalog({ maxAgeRating: 18, genre: 'Drama', language: 'es' }).items.map(c => c.title));

// -----------------------------------------------------------------------------
// 2. Abrir una serie: temporadas y episodios en una sola lectura (RF35)
// -----------------------------------------------------------------------------
const openSeries = (id, maxAgeRating) => db.Content.findOne(
  { _id: id, type: 'SERIES', status: 'PUBLISHED', ageRating: { $lte: maxAgeRating } },
  { title: 1, 'series.seasons.seasonNumber': 1, 'series.seasons.description': 1,
    'series.seasons.episodes._id': 1, 'series.seasons.episodes.number': 1,
    'series.seasons.episodes.title': 1, 'series.seasons.episodes.durationSeconds': 1 });
show('Abrir SER-00003 (perfil 3)', openSeries('SER-00003', 7));
show('Perfil 3 intenta abrir SER-00002 (18): null = rechazado', openSeries('SER-00002', 7));

// -----------------------------------------------------------------------------
// 3. Mi lista con estado del contenido: los retirados se muestran como no disponibles (RF39)
// -----------------------------------------------------------------------------
const myListOf = (profileId) => db.MyList.aggregate([
  { $match: { profileId } },
  { $sort: { addedAt: -1 } },
  { $lookup: { from: 'Content', localField: 'contentId', foreignField: '_id', as: 'c',
               pipeline: [{ $project: { title: 1, type: 1, status: 1, coverUrl: 1 } }] } },
  { $unwind: '$c' },
  { $project: { _id: 0, contentId: 1, addedAt: 1, title: '$c.title', type: '$c.type',
                available: { $eq: ['$c.status', 'PUBLISHED'] } } },
]).toArray();
show('Mi lista del perfil 1 (MOV-00006 retirado -> available:false)', myListOf(1));

// 4. Agregar / quitar de Mi lista. El índice único impide duplicados (RF37).
db.MyList.insertOne({ profileId: 2, contentId: 'SER-00004', addedAt: new Date() });
try {
  db.MyList.insertOne({ profileId: 2, contentId: 'SER-00004', addedAt: new Date() });
  print('ERROR: se permitió un duplicado');
} catch (e) {
  show('Duplicado en Mi lista rechazado', e.code); // 11000
}
show('Quitar de Mi lista', db.MyList.deleteOne({ profileId: 2, contentId: 'SER-00004' }).deletedCount);

// -----------------------------------------------------------------------------
// 5. Ciclo de una sesión de reproducción simulada (RF45-56)
//    El servidor calcula el tiempo visto con $$NOW - playingSince (no confía en el
//    cliente). Cada transición es un update CONDICIONAL al estado: si llegan dos
//    "pausar" a la vez, solo uno encuentra state:'PLAYING' y el tiempo no se suma dos veces.
// -----------------------------------------------------------------------------
const elapsed = { $dateDiff: { startDate: '$playingSince', endDate: '$$NOW', unit: 'second' } };
const progress = (pos) => ({ $gte: [pos, { $multiply: [0.9, '$durationSeconds'] }] }); // regla del 90 %

// Iniciar (la app ya verificó en Oracle: perfil activo, suscripción vigente -> subscriptionId/planId)
const content = db.Content.findOne({ _id: 'MOV-00008', status: 'PUBLISHED' });
// Antes de abrir otra, se cierran sesiones abiertas del mismo perfil y contenido (abandonadas)
db.Watch_sessions.updateMany(
  { profileId: 2, contentId: 'MOV-00008', state: { $ne: 'ENDED' } },
  [{ $set: { state: 'ENDED', endedAt: '$lastActivityAt' } }, { $unset: 'playingSince' }]);
const started = new Date(Date.now() - 600 * 1000); // simula que empezó hace 10 minutos
const sid = db.Watch_sessions.insertOne({
  profileId: 2, subscriptionId: 1, planId: 3, contentId: content._id, contentType: content.type,
  startedAt: started, watchedSeconds: 0, lastPositionSeconds: 0,
  durationSeconds: content.movie.durationSeconds, completed: false,
  state: 'PLAYING', playingSince: started, lastActivityAt: started,
}).insertedId;

// Pausar: suma lo reproducido desde playingSince
db.Watch_sessions.updateOne({ _id: sid, state: 'PLAYING' }, [
  { $set: { watchedSeconds: { $add: ['$watchedSeconds', elapsed] },
            lastPositionSeconds: { $add: ['$lastPositionSeconds', elapsed] },
            state: 'PAUSED', lastActivityAt: '$$NOW' } },
  { $set: { completed: progress('$lastPositionSeconds') } },
  { $unset: 'playingSince' },
]);
const twice = db.Watch_sessions.updateOne({ _id: sid, state: 'PLAYING' }, [{ $set: { state: 'PAUSED' } }]);
show('Segunda "pausa" concurrente no hace nada (matchedCount)', twice.matchedCount);

// Reanudar
db.Watch_sessions.updateOne({ _id: sid, state: 'PAUSED' },
  [{ $set: { state: 'PLAYING', playingSince: '$$NOW', lastActivityAt: '$$NOW' } }]);

// Avanzar (salto a 3000 s): el salto mueve la posición pero NO suma tiempo visto (RF53)
db.Watch_sessions.updateOne({ _id: sid, state: 'PLAYING' }, [
  { $set: { watchedSeconds: { $add: ['$watchedSeconds', elapsed] },
            lastPositionSeconds: 3000, playingSince: '$$NOW', lastActivityAt: '$$NOW' } },
  { $set: { completed: progress('$lastPositionSeconds') } },
]);

// Finalizar: el usuario la finaliza -> completada (RF56). Desde PLAYING suma lo último.
db.Watch_sessions.updateOne({ _id: sid, state: { $in: ['PLAYING', 'PAUSED'] } }, [
  { $set: { watchedSeconds: { $add: ['$watchedSeconds',
              { $cond: [{ $eq: ['$state', 'PLAYING'] }, elapsed, 0] }] },
            state: 'ENDED', endedAt: '$$NOW', lastActivityAt: '$$NOW', completed: true } },
  { $unset: 'playingSince' },
]);
show('Sesión finalizada (watchedSeconds ≈ 600, posición 3000: el salto no cuenta)',
  db.Watch_sessions.findOne({ _id: sid }, { state: 1, watchedSeconds: 1, lastPositionSeconds: 1, completed: 1 }));
db.Watch_sessions.deleteOne({ _id: sid }); // limpieza

// -----------------------------------------------------------------------------
// 6. Continuar viendo (RF57-61): última sesión por contenido, más reciente primero.
//    Serie: una entrada; si el último episodio se completó, el siguiente desde 0.
//    Excluye retirados y lo ya completado. Usa ix_sessions_profile_activity.
// -----------------------------------------------------------------------------
const continueWatching = (profileId, limit = 20) => db.Watch_sessions.aggregate([
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
  { $project: { _id: 0, contentId: 1, title: '$c.title', type: '$c.type', coverUrl: '$c.coverUrl',
      lastActivityAt: 1,
      episodeId: { $cond: ['$completed', '$next._id', '$episodeId'] },
      positionSeconds: { $cond: ['$completed', 0, '$lastPositionSeconds'] } } },
  { $sort: { lastActivityAt: -1 } },
  { $limit: limit },
]).toArray();
show('Continuar viendo, perfil 1 (sin MOV-00006 retirado ni MOV-00002 completada)', continueWatching(1));
show('Continuar viendo, perfil 4 (SER-00002 pasa a S02E01 desde 0)', continueWatching(4));
show('Continuar viendo, perfil 3 (terminó la serie: vacío)', continueWatching(3));

// -----------------------------------------------------------------------------
// 7. Informe de consumo por plan y tipo (RF67-70). Plan = foto guardada al iniciar
//    cada sesión; series contadas por episodio. Los nombres de plan los pone la app
//    desde Oracle. Proyectar solo campos del índice -> consulta "covered".
// -----------------------------------------------------------------------------
const consumptionReport = (from, to) => db.Watch_sessions.aggregate([
  { $match: { startedAt: { $gte: from, $lt: to } } },
  { $project: { _id: 0, planId: 1, contentType: 1, profileId: 1, watchedSeconds: 1 } },
  { $group: { _id: { planId: '$planId', contentType: '$contentType' },
              watchedSeconds: { $sum: '$watchedSeconds' },
              profiles: { $addToSet: '$profileId' } } },
  { $project: { _id: 0, planId: '$_id.planId', contentType: '$_id.contentType',
                minutesWatched: { $round: [{ $divide: ['$watchedSeconds', 60] }, 1] },
                distinctProfiles: { $size: '$profiles' } } },
  { $sort: { planId: 1, contentType: 1 } },
]).toArray();
const today = new Date(); today.setHours(0, 0, 0, 0);
show('Informe de consumo, últimos 90 días',
  consumptionReport(new Date(today.getTime() - 90 * 86400000), new Date(today.getTime() + 86400000)));

// -----------------------------------------------------------------------------
// 8. Siguiente id de contenido (lo usa ContentAdminService al registrar).
//    $inc atómico: dos admins concurrentes nunca reciben el mismo número.
//    Si el insert posterior falla queda un hueco: aceptable, los ids no se reutilizan,
//    por eso no se necesita una transacción multi-documento.
//    OJO: ejecutar esto CONSUME un número.
// -----------------------------------------------------------------------------
const nextContentId = (prefix) => {
  const { seq } = db.counters.findOneAndUpdate({ _id: prefix }, { $inc: { seq: 1 } },
    { upsert: true, returnDocument: 'after' });
  return `${prefix}-${String(seq).padStart(5, '0')}`;
};
show('Siguiente id de documental', nextContentId('DOC'));
