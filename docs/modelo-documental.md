# Modelo documental de CineConecta (MongoDB)

Base de datos `cineconecta` en MongoDB Atlas. Scripts en [`database/mongodb/`](../database/mongodb/).
Mapeo Java en `backend/src/main/java/.../model/document/`.

## 1. Qué guarda MongoDB y por qué

| Dato | Motor | Motivo |
|---|---|---|
| Cuentas, perfiles, planes, suscripciones, pagos | Oracle | Requieren integridad referencial y transacciones (RF19: pago + suscripción juntos o nada). Es la fuente de verdad |
| Catálogo (`Content`) | MongoDB | Tres tipos con atributos distintos (película, serie, documental). Una serie trae temporadas y episodios anidados. RNF17 pide poder agregar campos sin rediseñar |
| `MyList`, `Watch_sessions` | MongoDB | Alto volumen de escrituras pequeñas, consultas siempre por perfil y agregaciones para "Continuar viendo" y el informe de consumo |
| `Admin_user` | MongoDB | Los administradores son independientes de las cuentas de clientes (RF64): no tienen suscripción ni perfiles |
| `counters` | MongoDB | Último número usado por prefijo de id de contenido (RF25/RF63: ids nunca reutilizados) |

**Ninguna operación escribe en las dos bases a la vez.** Contratar y crear perfiles escribe solo en Oracle. Reproducir y usar Mi lista lee en Oracle y escribe solo en Mongo. Administrar el catálogo escribe solo en Mongo. Por eso no hace falta una transacción distribuida (ver §6).

## 2. Colecciones

### 2.1 `Content`: un documento por película, serie o documental

```js
{
  _id: "SER-00001",                    // legible y estable; nunca se reutiliza
  type: "SERIES",                      // MOVIE | SERIES | DOCUMENTARY
  status: "PUBLISHED",                 // DRAFT -> PUBLISHED <-> WITHDRAWN
  title: "Frontera Norte",
  synopsis: "...", releaseYear: 2023,
  genres: ["Drama", "Crimen"], productionCountries: ["Colombia"],
  audioLanguages: ["es"], subtitleLanguages: ["en"],
  ageRating: 16, coverUrl: "https://img.cineconecta.test/SER-00001.jpg",
  createdAt: ISODate("..."), updatedAt: ISODate("..."),
  series: {
    seasons: [
      { seasonNumber: 1, description: "Temporada 1",
        episodes: [
          { _id: "SER-00001-S01E01", number: 1, title: "Piloto", synopsis: "...", durationSeconds: 2700 },
          ...
        ] },
      ...
    ]
  }
}
```

Una película lleva `movie: { durationSeconds, directors, cast: [{ actor, character? }] }` y un documental lleva `documentary: { durationSeconds, directors, topics, interviewees, sources? }`.

| Decisión | Consulta que la justifica |
|---|---|
| Una sola colección para los tres tipos (patrón *polimórfico*) | La búsqueda y los filtros (RF32-34) recorren todo el catálogo a la vez, con un solo índice de texto y un solo índice por estado/tipo/año |
| Atributos propios de cada tipo en un subdocumento (`movie`, `series`, `documentary`) | Los campos comunes quedan arriba para filtrar. El validador exige exactamente un subdocumento según `type` (`oneOf`) |
| Temporadas y episodios **embebidos** en la serie | "Abrir una serie" (RF35) es **una sola lectura** por `_id`. Los episodios no existen sin su serie y se leen siempre con ella |
| `character` opcional en el reparto | RF27: el personaje solo se guarda "cuando se conozca" |
| `status` en lugar de borrar | RF60-62: un retirado conserva su id y sus referencias históricas, y puede volver a publicarse |

### 2.2 `MyList`: un documento por (perfil, contenido)

```js
{ _id: ObjectId("..."), profileId: 1, contentId: "MOV-00006", addedAt: ISODate("...") }
```

- **Referencia, no copia**: Mi lista muestra el estado **actual** del contenido. Un retirado aparece como "no disponible" (RF39) usando `$lookup` a `Content` (`06_operations.js`, operación 3).
- **Colección propia y no un arreglo en "el perfil"**: los perfiles viven en Oracle, así que en Mongo no hay un documento padre natural. Además, el índice único `{profileId, contentId}` impide duplicados desde la base de datos (RF37) y no solo desde la app.
- RF38 se cumple solo: la lista es independiente de la sesión de login.

### 2.3 `Watch_sessions`: un documento por sesión de reproducción

```js
{
  _id: ObjectId("..."),
  profileId: 1, subscriptionId: 1, planId: 3,   // ids de Oracle: foto al iniciar
  contentId: "SER-00001", contentType: "SERIES",
  episodeId: "SER-00001-S01E03",                 // solo series (RF49)
  startedAt: ISODate("..."), endedAt: ISODate("..."),   // RF51
  watchedSeconds: 1210,                          // sin los saltos (RF52/RF53)
  lastPositionSeconds: 1500,                     // RF54
  durationSeconds: 2700,                         // copia, para la regla del 90 %
  completed: false,                              // RF56
  state: "PAUSED",                               // PLAYING | PAUSED | ENDED
  playingSince: ISODate("..."),                  // solo mientras state = PLAYING
  lastActivityAt: ISODate("...")                 // orden de "Continuar viendo" (RF58)
}
```

| Decisión | Motivo |
|---|---|
| Un documento **por sesión**, nunca se sobrescribe | RF55: volver a ver un contenido crea otra sesión y la anterior se conserva |
| Colección aparte (no embebida en el contenido ni en el perfil) | Crece sin límite (ver §5). Las dos consultas principales cruzan perfiles o contenidos |
| `state` + `playingSince` | El servidor calcula el tiempo visto con `$$NOW - playingSince`, sin confiar en el cliente, con tope en lo que falta del contenido (`durationSeconds - lastPositionSeconds`): una sesión olvidada en `PLAYING` no infla el informe. Un salto (RF47) mueve `lastPositionSeconds` pero no suma tiempo (RF53) |
| `planId` y `subscriptionId` copiados al iniciar | RF68: el informe de consumo agrupa por el plan **con el que se vio**, aunque la cuenta cambie de plan después (cuenta 6 del seed) |
| `contentType` copiado | El informe agrupa por tipo sin hacer `$lookup` a `Content`, y así puede quedar *covered* (ver `indices-y-optimizacion.md`) |

"Continuar viendo" (RF57-61) **no** es una colección. Es una agregación sobre `Watch_sessions`:
1. toma la última sesión de cada contenido del perfil;
2. descarta las completadas y los contenidos no publicados;
3. en series, si el último episodio se completó, ofrece el **siguiente desde 0**.

Calcularlo al vuelo evita mantener una segunda copia del progreso, que podría quedar desactualizada.

### 2.4 `Admin_user`

```js
{ _id: ObjectId("..."), email: "admin@cineconecta.com", fullName: "...",
  passwordHash: "$2a$10$...", createdAt: ISODate("...") }
```
El correo se guarda en minúsculas y es único (índice `ux_admin_email`). El validador rechaza una contraseña que no tenga formato BCrypt, así que nunca se guarda en texto plano.

### 2.5 `counters`

```js
{ _id: "MOV", seq: 12500 }
```
El siguiente id se obtiene con `findOneAndUpdate({_id:"MOV"}, {$inc:{seq:1}}, {upsert:true, returnDocument:"after"})`, que es atómico.
- **No usa una transacción multi-documento**: si el `$inc` funciona y luego falla el insert del contenido, queda un hueco en la numeración. Es aceptable, porque RF63 exige no **reutilizar** ids, no que sean consecutivos.
- Los scripts de carga ajustan los contadores con `$max`, que nunca los baja.

## 3. Validaciones (`01_collections.js`)

Todas las colecciones usan `$jsonSchema` con `validationLevel: "strict"` y `validationAction: "error"`: un documento inválido se rechaza, venga de la app o de un script.

| Regla | Cómo se valida |
|---|---|
| Campos obligatorios del contenido (RF25) | `required` del documento |
| Película: duración, dirección, reparto (RF27) | `movie.required` |
| Serie con al menos una temporada, y cada temporada con al menos un episodio (RF28-30) | `minItems: 1` |
| Documental (RF31) | `documentary.required`; `sources` es opcional |
| Exactamente el subdocumento de su tipo | `oneOf` sobre `type` con `not: anyOf` |
| Clasificación entre 0 y 18 | `minimum` / `maximum` |
| Formato de ids | `pattern`: `^(MOV\|SER\|DOC)-\d{5}$` y `^SER-\d{5}-S\d{2}E\d{2}$` |
| Sesión de serie con episodio; de película/documental, sin episodio (RF49) | `oneOf` sobre `contentType` |
| Tiempos no negativos y duración positiva | `minimum` |
| Contraseña de admin en BCrypt | `pattern: ^\$2[aby]\$` |
| Sin duplicados en Mi lista (RF37) | Índice único (error `E11000`) |
| Ids de episodio únicos entre series | Índice único parcial `ux_content_episode_id` |

- **Enteros**: los campos enteros aceptan `["int","long"]`, porque mongosh guarda `int32` y Spring puede enviar `Long`. En Java todos son `Integer`.
- **Campos adicionales**: no se usa `additionalProperties: false`. El catálogo puede ganar campos (RNF17), y Spring agrega `_class`.
- **Evidencia**: `07_validation_tests.js` tiene un control positivo y 12 rechazos esperados, cada uno con un solo defecto.

## 4. Identificadores

- Los contenidos usan ids **legibles y estables** (`MOV-00001`, `SER-00001`, `DOC-00001`). El de un episodio deriva del de su serie (`SER-00001-S01E01`). Las sesiones, Mi lista y los informes los guardan tal cual, y se leen sin consultar el catálogo.
- Los ids de MyList, Watch_sessions y Admin_user son `ObjectId` generados.
- Rangos reservados:

  | Uso | Rango |
  |---|---|
  | Seed funcional | `00001`–`00020` |
  | Volumen | `10001`–`12500` |
  | Prueba de ida y vuelta de `MongoMappingIT` | `SER-99999` |
  | `07_validation_tests.js` | `99998` |

## 5. Crecimiento

| Documento | Tamaño | Límite (16 MB por documento) |
|---|---|---|
| Episodio embebido | ~150 B con la sinopsis corta del volumen; ~600 B con una sinopsis real de 500 caracteres | Una serie de 30 temporadas × 25 episodios (750) ocupa ~110–450 KB. Hacen falta >25.000 episodios para acercarse al límite: no ocurre en la práctica |
| Sesión | ~320 B (200.000 sesiones ≈ 64 MB en `04_volume.js`) | Crece sin límite: **por eso va en su propia colección**. Embebida en un documento por perfil llegaría al límite con ~50.000 sesiones y además cada pausa reescribiría un documento cada vez más grande |
| Mi lista | ~80 B por elemento | Acotada por perfil, pero va aparte por el índice único (§2.2) |

Las colecciones crecen así:
- `Content` crece despacio: solo el administrador inserta contenidos.
- `Watch_sessions` crece en proporción a la actividad. Sus dos índices sirven a las consultas por perfil y por rango de fechas, que no se degradan al crecer: examinan solo las claves del perfil o del rango.

## 6. Integración con Oracle

**Contrato de ids.**
- Mongo guarda `profileId`, `subscriptionId` y `planId` de Oracle. La referencia va en **un solo sentido** (Mongo → Oracle); Oracle no sabe nada de Mongo.
- Los scripts de carga respetan el mismo contrato:
  - el seed: perfiles 1–23, suscripciones 1–11 y planes 1–4;
  - el volumen: perfiles 1001–26000 y suscripciones 1001–121000, con fórmulas compartidas.
- **Las dos bases se cargan el mismo día**, porque las fechas son relativas a hoy.

**Verificaciones antes de escribir en Mongo** (responsabilidad de la app):

| Operación | Oracle (lectura) | Mongo |
|---|---|---|
| Buscar / filtrar / abrir serie (RF32-35) | Perfil activo y suscripción vigente; leer `maxAgeRating` | `status: PUBLISHED`, `ageRating <= maxAgeRating` |
| Iniciar reproducción (RF40-44) | Suscripción vigente (`v_active_subscriptions`) → `subscriptionId`, `planId`; perfil activo y su `maxAgeRating` | Contenido publicado y clasificación permitida; insertar la sesión con la foto de `subscriptionId`/`planId` |
| Mi lista (RF36) | Perfil activo de la cuenta autenticada | Insertar/borrar; el índice único rechaza duplicados |

**Orden de escritura y fallos.**
- Como ninguna operación escribe en las dos bases, no hay estados a medias entre ellas. Lo peor que puede pasar es que Oracle responda y la escritura en Mongo falle: la operación se reporta como fallida y no queda nada registrado.
- **Si una base no responde**, la app devuelve 503 (`ServiceUnavailableException`) y **nunca autoriza por omisión**: sin Oracle no hay forma de saber si la suscripción está vigente, así que la reproducción se rechaza.

**Efectos de los cambios de estado.**

| Evento | Oracle | Mongo |
|---|---|---|
| Desactivar perfil (RF11) | `isActive = 'N'` | No se toca nada: su historial y su lista se conservan (perfil 16 del seed). Mientras esté inactivo, la app no lo deja seleccionar (RF12) |
| Retirar contenido | — | `status: WITHDRAWN`: desaparece de búsqueda y de "Continuar viendo" (RF61), sigue en Mi lista como no disponible (RF39), no admite reproducciones nuevas (RF60) y sus sesiones se conservan (RF62) |
| Cambio de plan | Nueva suscripción (RF17) | Las sesiones viejas conservan su `planId` (RF68) |
| Suscripción vencida | Deja de aparecer en `v_active_subscriptions` | No se puede iniciar una sesión nueva (RF40); el historial queda |

## 7. Datos duplicados (y por qué no hay riesgo)

| Copia | Original | Por qué se copia | ¿Puede quedar desactualizada? |
|---|---|---|---|
| `Watch_sessions.planId`, `subscriptionId` | Oracle | Foto del momento (RF68) | No: debe reflejar el pasado |
| `Watch_sessions.contentType` | `Content.type` | Informe sin `$lookup`, covered | No: el tipo de un contenido no cambia |
| `Watch_sessions.durationSeconds` | Duración del contenido o episodio | Regla del 90 % en el update, sin otra lectura | Solo si el admin corrige la duración; la sesión vieja conserva la duración con que se vio |
| — | `maxAgeRating`, `isActive`, estado del contenido | **No se copian**: se leen en el momento de cada verificación | — |

## 8. Concurrencia en MongoDB

- **Atomicidad por documento.** Cada transición de una sesión (pausar, reanudar, avanzar o finalizar) es **un solo update condicional al estado**: `updateOne({_id, state:"PLAYING"}, pipeline)`. Si llegan dos "pausa" a la vez, solo una encuentra `PLAYING`, así que el tiempo no se suma dos veces (`06_operations.js`, operación 5: el segundo update da `matchedCount = 0`).
- **El tiempo lo calcula el servidor** (`$dateDiff` entre `playingSince` y `$$NOW`) dentro del mismo update, sin leer y luego escribir.
- **Unicidad.** El índice único de Mi lista rechaza el segundo de dos inserts simultáneos (`E11000`).
- **Ids.** El `$inc` de `counters` es atómico: dos administradores nunca reciben el mismo id (§2.5).
- **Lo que no se usa.** No hacen falta transacciones multi-documento: ninguna operación del modelo modifica más de un documento que deba quedar consistente con otro.

## 9. Limitaciones de MongoDB en este diseño

| Limitación | Consecuencia | Mitigación |
|---|---|---|
| Sin integridad referencial | Mongo no sabe si `profileId` o `contentId` existen | La app verifica antes de escribir (§6). Los seeds afirman el contrato con asserts |
| Sin transacción entre Oracle y Mongo | No hay commit conjunto | Ninguna operación escribe en las dos bases (§1) |
| Unicidad dentro de un mismo documento | Un índice multikey no compara un documento consigo mismo: no impide dos temporadas `1` ni dos episodios `1` en la misma serie (RF29/RF30) | La app valida antes de guardar; `03_seed.js` lo comprueba con asserts. El índice único parcial sí impide repetir un id de episodio **entre** series |
| Límite de 16 MB por documento | Una serie no puede crecer indefinidamente | Muy lejos del límite (§5) |
| `$jsonSchema` no valida reglas entre campos con fechas o entre documentos | Por ejemplo, `endedAt >= startedAt` o que la sesión caiga dentro de la suscripción | Las calcula el servidor y las verifican los asserts del seed |

## 10. Casos de prueba sembrados

**Oracle** (`01_cineconecta_oracle.sql`) y **Mongo** (`03_seed.js`) se cargan con los mismos ids. Las pruebas de rechazo están en `05_verification.sql` y `07_validation_tests.js`, y la concurrencia en `05b_concurrency_demo.sql`.

| Regla | Dato que la demuestra | Dónde se ve |
|---|---|---|
| RF2 correo único sin mayúsculas | `JUAN.Perez@Example.COM` rechazado | `05_verification.sql` |
| RF8 nombre de perfil único | Perfil `JUAN` en la cuenta 1 rechazado | `05_verification.sql` |
| RF9/RF10 máximo de perfiles, en concurrencia | Cuentas `demo.perfiles`: sin bloqueo quedan 3 activos con Estándar; con `FOR UPDATE` se rechaza | `05b_concurrency_demo.sql` caso 2 |
| RF10 en el límite | Cuenta 5 con Familiar y 6 perfiles; cuenta 8 con Básico, 1 activo y 1 inactivo | Seed Oracle |
| RF11 desactivar conserva | Perfil 16 inactivo con sesiones y Mi lista; perfiles 20 y 21 | Seed Mongo, `05_verification.sql` |
| RF16/RF22 sin superposición | Cuenta 1 con suscripción vigente → `-20001`; dos contrataciones simultáneas → `-20001` | `05_verification.sql`, `05b` caso 1 |
| RF17 historial de suscripciones | Cuenta 6: 3 períodos, planes Estándar → Premium | `05_verification.sql`, informe de consumo |
| RF19 todo o nada | Pago con referencia repetida → la suscripción tampoco queda | `05_verification.sql` §3 |
| RF20 un pago por suscripción | Segundo pago rechazado (`uq_payments_subscription`) | `05_verification.sql` |
| RF21 el fin se excluye | Suscripción 11 (cuenta 10, perfiles 22 y 23) terminó hoy a las 00:00: no está vigente | `05_verification.sql`, perfil 22 en Mi lista |
| RF23 exceso de perfiles | Cuenta 9 (3 activos) contrata Básico → `-20002`; tras desactivar 2 contrata | `05_verification.sql` |
| Flujo completo de contratación desde la app | Cuenta 4, sin suscripciones ni perfiles | Se deja intacta para la demo |
| RF34 solo publicados y clasificación | Perfil 3 "Niños" (máx. 7) no ve contenidos de 16/18; `MOV-00007` y `SER-00006` en DRAFT no aparecen | `06_operations.js` 1-2 |
| RF37 Mi lista sin duplicados | Perfil 1 + `MOV-00002` | `07_validation_tests.js`, `06_operations.js` 4 |
| RF39 retirado en Mi lista | Perfil 1 + `MOV-00006` → `available: false` | `06_operations.js` 3 |
| RF40 suscripción vencida | Perfil 6 (cuenta 3): tiene Mi lista pero no puede reproducir | App (siguiente tarea) |
| RF53 los saltos no cuentan | Perfil 1, `MOV-00001`: posición 40 %, tiempo visto 25 % | Seed Mongo, `06_operations.js` 5 |
| RF55 historial al volver a ver | Perfil 1 vio una película dos veces: dos sesiones | Seed Mongo |
| RF56 completado al 90 % | Perfil 2 | Seed Mongo |
| RF57-59 Continuar viendo | Perfil 4: `SER-00002` pasa a `S02E01` desde 0; perfil 3 terminó la serie y queda vacío | `06_operations.js` 6 |
| RF61 retirado fuera de Continuar viendo | Perfil 1, sesión pausada de `MOV-00006` | `06_operations.js` 6 |
| RF62 referencias históricas | `SER-00005` retirada con una sesión del perfil 15 | Seed Mongo |
| RF68 consumo por plan | Cuenta 6 vio bajo planes 2 y 3 | `06_operations.js` 7 |
| Validaciones del catálogo | 12 rechazos (sin duración, `ageRating` 21, tipo inválido, dos subdocumentos, id mal formado, serie sin temporadas...) | `07_validation_tests.js` |

## 11. Orden de carga

Ver la sección *MongoDB* del [README](../readme.md). Resumen:

1. Oracle: `01_cineconecta_oracle.sql`.
2. Mongo, en este orden: `01_collections.js`, `02_indexes.js`, `03_seed.js` y `07_validation_tests.js`.
3. Volumen, el mismo día en los dos motores: `04_volume_load.sql` y `04_volume.js`.
4. Evidencias: `05_verification.sql`, `05b_concurrency_demo.sql`, los experimentos de índices `05_index_experiment.js` y `06_index_experiment.sql`, y los catálogos `07_object_catalog.sql` y `08_catalog.js`.

**Diagrama:** [`cineconecta.mml`](MongoDb%20Diagram/cineconecta.mml) (imagen: [`cineconecta.png`](MongoDb%20Diagram/cineconecta.png)). Coincide campo por campo con los validadores de `01_collections.js`: ids de contenido como strings legibles (`MOV-00001`, `SER-00001-S01E01`), la colección `counters` y el estado de la reproducción en `Watch_sessions` (`state`, `playingSince`, `durationSeconds`).
