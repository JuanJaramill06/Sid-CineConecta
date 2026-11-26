// =============================================================================
// CINECONECTA - MongoDB: colecciones y validadores ($jsonSchema)
//
// Ejecutar:  mongosh "$env:MONGO_URI" --file database/mongodb/01_collections.js
//
// ¡BORRA Y RECREA las 5 colecciones de CineConecta (y sus datos)! Después hay
// que correr 02_indexes.js y 03_seed.js. Es reejecutable.
//
// Decisiones:
//   * validationLevel "strict" + validationAction "error": un documento inválido
//     se rechaza, venga de la app o de un script (RNF9).
//   * Sin additionalProperties:false: el catálogo puede ganar campos nuevos sin
//     rediseñar la colección (RNF17). Spring además agrega "_class".
//   * Enteros como ["int","long"]: mongosh guarda int32 y Spring Integer/Long.
//   * Lo que $jsonSchema NO puede validar (unicidad de temporada/episodio dentro
//     de una serie, referencias a Oracle) lo valida la aplicación.
// =============================================================================
db = db.getSiblingDB('cineconecta');

const INT = { bsonType: ['int', 'long'] };
const POS_INT = { bsonType: ['int', 'long'], minimum: 1 };
const NON_NEG_INT = { bsonType: ['int', 'long'], minimum: 0 };
const TEXT = { bsonType: 'string', minLength: 1 };
const TEXT_LIST = { bsonType: 'array', minItems: 1, items: TEXT };
const OPT_TEXT_LIST = { bsonType: 'array', items: TEXT };
const CONTENT_ID = { bsonType: 'string', pattern: '^(MOV|SER|DOC)-[0-9]{5}$' };
const EPISODE_ID = { bsonType: 'string', pattern: '^SER-[0-9]{5}-S[0-9]{2}E[0-9]{2}$' };
const CONTENT_TYPE = { enum: ['MOVIE', 'SERIES', 'DOCUMENTARY'] };

const schemas = {
  Content: {
    bsonType: 'object',
    required: ['_id', 'type', 'status', 'title', 'synopsis', 'releaseYear', 'genres',
               'productionCountries', 'audioLanguages', 'subtitleLanguages',
               'ageRating', 'coverUrl', 'createdAt', 'updatedAt'],
    properties: {
      _id: CONTENT_ID,
      type: CONTENT_TYPE,
      status: { enum: ['DRAFT', 'PUBLISHED', 'WITHDRAWN'] },
      title: TEXT,
      synopsis: TEXT,
      releaseYear: { bsonType: ['int', 'long'], minimum: 1888, maximum: 2100 },
      genres: TEXT_LIST,
      productionCountries: TEXT_LIST,
      audioLanguages: TEXT_LIST,
      subtitleLanguages: OPT_TEXT_LIST,
      ageRating: { bsonType: ['int', 'long'], minimum: 0, maximum: 18 },
      coverUrl: TEXT,
      createdAt: { bsonType: 'date' },
      updatedAt: { bsonType: 'date' },
      movie: {
        bsonType: 'object',
        required: ['durationSeconds', 'directors', 'cast'],
        properties: {
          durationSeconds: POS_INT,
          directors: TEXT_LIST,
          cast: {
            bsonType: 'array',
            items: {
              bsonType: 'object',
              required: ['actor'],
              properties: { actor: TEXT, character: TEXT },   // personaje solo si se conoce
            },
          },
        },
      },
      series: {
        bsonType: 'object',
        required: ['seasons'],
        properties: {
          seasons: {
            bsonType: 'array',
            minItems: 1,
            items: {
              bsonType: 'object',
              required: ['seasonNumber', 'description', 'episodes'],
              properties: {
                seasonNumber: POS_INT,
                description: TEXT,
                episodes: {
                  bsonType: 'array',
                  minItems: 1,
                  items: {
                    bsonType: 'object',
                    required: ['_id', 'number', 'title', 'synopsis', 'durationSeconds'],
                    properties: {
                      _id: EPISODE_ID,
                      number: POS_INT,
                      title: TEXT,
                      synopsis: TEXT,
                      durationSeconds: POS_INT,
                    },
                  },
                },
              },
            },
          },
        },
      },
      documentary: {
        bsonType: 'object',
        required: ['durationSeconds', 'directors', 'topics', 'interviewees'],
        properties: {
          durationSeconds: POS_INT,
          directors: TEXT_LIST,
          topics: TEXT_LIST,
          interviewees: TEXT_LIST,
          sources: OPT_TEXT_LIST,   // fuentes o instituciones, cuando corresponda
        },
      },
    },
    // Cada tipo exige su subdocumento y no admite el de los otros tipos
    oneOf: [
      { properties: { type: { enum: ['MOVIE'] } },
        required: ['movie'], not: { anyOf: [{ required: ['series'] }, { required: ['documentary'] }] } },
      { properties: { type: { enum: ['SERIES'] } },
        required: ['series'], not: { anyOf: [{ required: ['movie'] }, { required: ['documentary'] }] } },
      { properties: { type: { enum: ['DOCUMENTARY'] } },
        required: ['documentary'], not: { anyOf: [{ required: ['movie'] }, { required: ['series'] }] } },
    ],
  },

  MyList: {
    bsonType: 'object',
    required: ['_id', 'profileId', 'contentId', 'addedAt'],
    properties: {
      _id: { bsonType: 'objectId' },
      profileId: POS_INT,          // PROFILES.profileId en Oracle (se verifica en la app)
      contentId: CONTENT_ID,
      addedAt: { bsonType: 'date' },
    },
  },

  Watch_sessions: {
    bsonType: 'object',
    required: ['_id', 'profileId', 'subscriptionId', 'planId', 'contentId', 'contentType',
               'startedAt', 'watchedSeconds', 'lastPositionSeconds', 'durationSeconds',
               'completed', 'state', 'lastActivityAt'],
    properties: {
      _id: { bsonType: 'objectId' },
      profileId: POS_INT,          // Oracle PROFILES
      subscriptionId: POS_INT,     // Oracle SUBSCRIPTIONS: foto al iniciar la sesión
      planId: POS_INT,             // Oracle PLANS: foto al iniciar la sesión (RF68)
      contentId: CONTENT_ID,
      contentType: CONTENT_TYPE,
      episodeId: EPISODE_ID,
      startedAt: { bsonType: 'date' },
      endedAt: { bsonType: 'date' },
      watchedSeconds: NON_NEG_INT, // sin los saltos del control de avance (RF53)
      lastPositionSeconds: NON_NEG_INT,
      durationSeconds: POS_INT,    // copia de la duración para la regla del 90 %
      completed: { bsonType: 'bool' },
      state: { enum: ['PLAYING', 'PAUSED', 'ENDED'] },
      playingSince: { bsonType: 'date' },
      lastActivityAt: { bsonType: 'date' },
    },
    // Las series se reproducen por episodio; películas y documentales no llevan episodio
    oneOf: [
      { properties: { contentType: { enum: ['SERIES'] } }, required: ['episodeId'] },
      { properties: { contentType: { enum: ['MOVIE', 'DOCUMENTARY'] } }, not: { required: ['episodeId'] } },
    ],
  },

  Admin_user: {
    bsonType: 'object',
    required: ['_id', 'email', 'fullName', 'passwordHash', 'createdAt'],
    properties: {
      _id: { bsonType: 'objectId' },
      email: { bsonType: 'string', pattern: '^[^A-Z\\s@]+@[^A-Z\\s@]+$' }, // en minúsculas
      fullName: TEXT,
      passwordHash: { bsonType: 'string', pattern: '^\\$2[aby]\\$' },     // BCrypt, nunca texto plano
      createdAt: { bsonType: 'date' },
    },
  },

  // Último número usado por prefijo de id. Solo crece ($inc / $max): ids nunca reutilizados.
  counters: {
    bsonType: 'object',
    required: ['_id', 'seq'],
    properties: {
      _id: { enum: ['MOV', 'SER', 'DOC'] },
      seq: NON_NEG_INT,
    },
  },
};

const existing = db.getCollectionNames();
for (const [name, schema] of Object.entries(schemas)) {
  if (existing.includes(name)) db.getCollection(name).drop();
  db.createCollection(name, {
    validator: { $jsonSchema: schema },
    validationLevel: 'strict',
    validationAction: 'error',
  });
  print(`OK  ${name}`);
}
print('Colecciones creadas. Siguiente: 02_indexes.js');
