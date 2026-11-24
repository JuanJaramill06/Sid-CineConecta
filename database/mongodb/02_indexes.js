// =============================================================================
// CINECONECTA - MongoDB: índices de las consultas principales (RNF14)
//
// Ejecutar después de 01_collections.js:
//   mongosh "$env:MONGO_URI" --file database/mongodb/02_indexes.js
// createIndex es idempotente: se puede volver a ejecutar.
// Los nombres de índice los usa 05_index_experiment.js.
// =============================================================================
db = db.getSiblingDB('cineconecta');

// ---- Content -----------------------------------------------------------------
// Búsqueda por título (RF32): por palabras, sin mayúsculas ni tildes.
db.Content.createIndex({ title: 'text' },
  { name: 'ix_content_title_text', default_language: 'spanish' });

// Catálogo filtrado (RF33/34): status siempre es igualdad (PUBLISHED), luego tipo;
// releaseYear sirve para filtrar por año y para ordenar los resultados paginados.
db.Content.createIndex({ status: 1, type: 1, releaseYear: -1 },
  { name: 'ix_content_status_type_year' });

// Filtro por género (multikey). Solo un campo arreglo por índice compuesto.
db.Content.createIndex({ status: 1, genres: 1 },
  { name: 'ix_content_status_genres' });

// Id de episodio único ENTRE series. No evita repetidos dentro de la misma serie
// (un índice multikey no compara un documento consigo mismo): eso lo valida la app.
db.Content.createIndex({ 'series.seasons.episodes._id': 1 },
  { name: 'ux_content_episode_id', unique: true,
    partialFilterExpression: { type: 'SERIES' } });

// ---- MyList ------------------------------------------------------------------
// Sin duplicados por perfil (RF37) y lectura de la lista de un perfil (prefijo profileId).
db.MyList.createIndex({ profileId: 1, contentId: 1 },
  { name: 'ux_mylist_profile_content', unique: true });

// ---- Watch_sessions ----------------------------------------------------------
// Continuar viendo (RF57-59): sesiones de un perfil por actividad reciente.
db.Watch_sessions.createIndex({ profileId: 1, lastActivityAt: -1 },
  { name: 'ix_sessions_profile_activity' });

// Informe de consumo (RF67-69): filtra solo por rango de fechas y agrupa por
// plan y tipo. Con estos 5 campos la agregación queda "covered" (no lee documentos).
db.Watch_sessions.createIndex(
  { startedAt: 1, planId: 1, contentType: 1, profileId: 1, watchedSeconds: 1 },
  { name: 'ix_sessions_report' });

// ---- Admin_user --------------------------------------------------------------
db.Admin_user.createIndex({ email: 1 }, { name: 'ux_admin_email', unique: true });

for (const c of ['Content', 'MyList', 'Watch_sessions', 'Admin_user']) {
  print(`${c}: ${db.getCollection(c).getIndexes().map(i => i.name).join(', ')}`);
}
