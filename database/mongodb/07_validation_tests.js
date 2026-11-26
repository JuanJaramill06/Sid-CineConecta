// =============================================================================
// CINECONECTA - MongoDB: pruebas de validación (casos inválidos)
//
// Ejecutar después de 03_seed.js:
//   mongosh "$env:MONGO_URI" --file database/mongodb/07_validation_tests.js
//
// Cada caso parte de un documento VÁLIDO y le introduce un solo defecto, así el
// rechazo se debe a ese defecto. Imprime PASS/FAIL y termina con error si algo falla.
// Usa ids MOV-99998 / SER-99998 (fuera del seed y del volumen) y los borra al final.
// =============================================================================
db = db.getSiblingDB('cineconecta');

const now = new Date();
const validMovie = () => ({
  _id: 'MOV-99998', type: 'MOVIE', status: 'DRAFT', title: 'Prueba', synopsis: 'Prueba.',
  releaseYear: 2026, genres: ['Drama'], productionCountries: ['Colombia'], audioLanguages: ['es'],
  subtitleLanguages: [], ageRating: 12, coverUrl: 'https://img.cineconecta.test/MOV-99998.jpg',
  createdAt: now, updatedAt: now,
  movie: { durationSeconds: 5400, directors: ['Directora'], cast: [{ actor: 'Actor' }] },
});
const validSession = () => ({
  profileId: 1, subscriptionId: 1, planId: 3, contentId: 'SER-00001', contentType: 'SERIES',
  episodeId: 'SER-00001-S01E01', startedAt: now, watchedSeconds: 0, lastPositionSeconds: 0,
  durationSeconds: 2700, completed: false, state: 'PLAYING', playingSince: now, lastActivityAt: now,
});

let failures = 0;
function expectRejected(name, expectedCode, fn) {
  try {
    fn();
    failures++;
    print(`FAIL  ${name}: se aceptó`);
  } catch (e) {
    const ok = e.code === expectedCode;
    if (!ok) failures++;
    print(`${ok ? 'PASS' : 'FAIL'}  ${name} (code ${e.code}${ok ? '' : `, se esperaba ${expectedCode}: ${e.message}`})`);
  }
}
const INVALID = 121;  // DocumentValidationFailure ($jsonSchema)
const DUPLICATE = 11000;

// Control positivo: el documento base sí pasa (si no, las pruebas no significan nada)
db.Content.insertOne(validMovie());
db.Content.deleteOne({ _id: 'MOV-99998' });
print('PASS  control: la película válida se acepta');

expectRejected('película sin durationSeconds', INVALID, () => {
  const d = validMovie(); delete d.movie.durationSeconds; db.Content.insertOne(d);
});
expectRejected('ageRating 21 (máximo 18)', INVALID, () => {
  db.Content.insertOne({ ...validMovie(), ageRating: 21 });
});
expectRejected('type inválido (PODCAST)', INVALID, () => {
  db.Content.insertOne({ ...validMovie(), type: 'PODCAST' });
});
expectRejected('película que además trae series', INVALID, () => {
  db.Content.insertOne({ ...validMovie(), series: { seasons: [] } });
});
expectRejected('id con formato no permitido', INVALID, () => {
  db.Content.insertOne({ ...validMovie(), _id: 'pelicula-1' });
});
expectRejected('serie sin temporadas', INVALID, () => {
  const d = validMovie(); delete d.movie;
  db.Content.insertOne({ ...d, _id: 'SER-99998', type: 'SERIES', series: { seasons: [] } });
});
expectRejected('publicar con estado inexistente', INVALID, () => {
  db.Content.insertOne({ ...validMovie(), status: 'ARCHIVED' });
});
expectRejected('Mi lista duplicada (perfil 1, MOV-00002)', DUPLICATE, () => {
  db.MyList.insertOne({ profileId: 1, contentId: 'MOV-00002', addedAt: now });
});
expectRejected('sesión de serie sin episodeId', INVALID, () => {
  const s = validSession(); delete s.episodeId; db.Watch_sessions.insertOne(s);
});
expectRejected('sesión con tiempo visto negativo', INVALID, () => {
  db.Watch_sessions.insertOne({ ...validSession(), watchedSeconds: -10 });
});
expectRejected('admin con contraseña en texto plano', INVALID, () => {
  db.Admin_user.insertOne({ email: 'nuevo@cineconecta.com', fullName: 'Nuevo', passwordHash: 'Admin123', createdAt: now });
});
expectRejected('admin con correo repetido', DUPLICATE, () => {
  db.Admin_user.insertOne({ email: 'admin@cineconecta.com', fullName: 'Otro',
    passwordHash: '$2a$10$UZ2SYab5tslRTRMcwKVLX.TFlg7q7gyvgc5fIPBZ.OoiMIZXhhV66', createdAt: now });
});

// Limpieza por si algún caso se aceptó por error
db.Content.deleteMany({ _id: { $in: ['MOV-99998', 'SER-99998', 'pelicula-1'] } });

print(failures === 0 ? '\nTodas las pruebas pasaron' : `\n${failures} prueba(s) fallaron`);
if (failures > 0) quit(1);
