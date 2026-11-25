# Catálogo de objetos

Este catálogo se arma a partir de los scripts de creación:
- Oracle: [`database/oracle/01_cineconecta_oracle.sql`](../database/oracle/01_cineconecta_oracle.sql)
- MongoDB: [`database/mongodb/01_collections.js`](../database/mongodb/01_collections.js) y [`02_indexes.js`](../database/mongodb/02_indexes.js)

La evidencia desde el diccionario de datos se genera con:
- [`database/oracle/07_object_catalog.sql`](../database/oracle/07_object_catalog.sql): consulta `USER_OBJECTS`, `USER_TAB_COLUMNS`, `USER_CONSTRAINTS`, `USER_INDEXES`, `USER_IND_COLUMNS`, `USER_IND_EXPRESSIONS`, `USER_VIEWS` y `USER_ARGUMENTS`;
- [`database/mongodb/08_catalog.js`](../database/mongodb/08_catalog.js): usa `getCollectionInfos()` y `getIndexes()`.

Su salida va en el [Anexo](#anexo-salida-de-los-scripts).

## 1. Oracle

### 1.1 Resumen

| Tipo | Objetos |
|---|---|
| Tablas | `PLANS`, `ACCOUNTS`, `PROFILES`, `SUBSCRIPTIONS`, `PAYMENTS` |
| Vista | `V_ACTIVE_SUBSCRIPTIONS` |
| Procedimiento | `SP_CONTRACT_SUBSCRIPTION` |
| Índices | 5 de llave primaria, 3 de restricción única y 7 creados explícitamente (§1.3) |
| Secuencias | Una `ISEQ$$_…` por columna IDENTITY (las genera Oracle) |

### 1.2 Tablas

**PLANS** (catálogo de planes, RF13-14)

| Columna | Tipo | Nulo | Notas |
|---|---|---|---|
| planId | INTEGER IDENTITY | No | PK `pk_plans` |
| name | VARCHAR2(60) | No | Único `uq_plans_name` |
| price | NUMBER(12,2) | No | `ck_plans_price`: `price > 0` (pesos colombianos) |
| maxProfiles | INTEGER | No | `ck_plans_maxprofiles`: `maxProfiles >= 1` |

**ACCOUNTS** (RF1-3)

| Columna | Tipo | Nulo | Notas |
|---|---|---|---|
| accountId | INTEGER IDENTITY | No | PK `pk_accounts` |
| fullName | VARCHAR2(150) | No | |
| email | VARCHAR2(254) | No | Único sin mayúsculas: `ux_accounts_email_ci` sobre `LOWER(email)` |
| passwordHash | VARCHAR2(255) | No | BCrypt (RNF1) |
| createdAt | TIMESTAMP | No | Por defecto `LOCALTIMESTAMP` |

**PROFILES** (RF6-12)

| Columna | Tipo | Nulo | Notas |
|---|---|---|---|
| profileId | INTEGER IDENTITY | No | PK `pk_profiles` |
| accountId | INTEGER | No | FK `fk_profiles_account` → ACCOUNTS |
| name | VARCHAR2(60) | No | Único por cuenta: `ux_profiles_name_ci` sobre `(accountId, LOWER(name))` |
| maxAgeRating | INTEGER | No | `ck_profiles_rating`: `>= 0` |
| isActive | CHAR(1) | No | Por defecto `'Y'`; `ck_profiles_active`: `IN ('Y','N')` |

**SUBSCRIPTIONS** (RF15-23)

| Columna | Tipo | Nulo | Notas |
|---|---|---|---|
| subscriptionId | INTEGER IDENTITY | No | PK `pk_subscriptions` |
| accountId | INTEGER | No | FK `fk_subs_account` → ACCOUNTS |
| planId | INTEGER | No | FK `fk_subs_plan` → PLANS |
| startsAt | TIMESTAMP | No | Vigencia `[startsAt, endsAt)` (RF21) |
| endsAt | TIMESTAMP | No | `ck_subs_period`: `endsAt > startsAt` |

**PAYMENTS** (RF18-20)

| Columna | Tipo | Nulo | Notas |
|---|---|---|---|
| paymentId | INTEGER IDENTITY | No | PK `pk_payments` |
| subscriptionId | INTEGER | No | FK `fk_payments_subscription`; único `uq_payments_subscription` (un pago por suscripción) |
| paidAt | TIMESTAMP | No | |
| amount | NUMBER(12,2) | No | `ck_payments_amount`: `amount > 0` |
| reference | VARCHAR2(100) | No | Único `uq_payments_reference` |

### 1.3 Índices creados explícitamente

| Índice | Tabla | Columnas | Único |
|---|---|---|---|
| `ux_accounts_email_ci` | ACCOUNTS | `LOWER(email)` | Sí |
| `ux_profiles_name_ci` | PROFILES | `accountId, LOWER(name)` | Sí |
| `ix_profiles_account_active` | PROFILES | `accountId, isActive` | No |
| `ix_subs_account_period` | SUBSCRIPTIONS | `accountId, startsAt, endsAt` | No |
| `ix_subs_plan` | SUBSCRIPTIONS | `planId` | No |
| `ix_payments_paidat` | PAYMENTS | `paidAt, amount, subscriptionId` | No |

La justificación de cada uno está en [indices-y-optimizacion.md](indices-y-optimizacion.md).

### 1.4 Vista y procedimiento

- **`V_ACTIVE_SUBSCRIPTIONS`**: las suscripciones vigentes ahora (`startsAt <= LOCALTIMESTAMP < endsAt`) con el nombre del plan y su `maxProfiles`. La usan las verificaciones de vigencia y el conteo de perfiles.
- **`SP_CONTRACT_SUBSCRIPTION`**
  - Parámetros: `p_account_id IN`, `p_plan_id IN`, `p_reference IN` y `p_subscription_id OUT`.
  - Crea la suscripción y su pago en una sola transacción (RF19).
  - Bloquea la cuenta con `FOR UPDATE` (RNF10) y usa `SAVEPOINT` + `ROLLBACK TO` (RNF11).
  - Errores:

    | Código | Causa |
    |---|---|
    | `-20001` | Período superpuesto |
    | `-20002` | Más perfiles activos de los que admite el plan |
    | `-20003` | La cuenta no existe |
    | `-20004` | El plan no existe |

  - No hace COMMIT: lo hace quien lo llama.

## 2. MongoDB (base `cineconecta`)

Todas las colecciones tienen validador `$jsonSchema` con `validationLevel: strict` y `validationAction: error`.

| Colección | Documento | Obligatorios | Índices (además de `_id`) |
|---|---|---|---|
| `Content` | Película, serie o documental (`_id` = `MOV-/SER-/DOC-#####`) | `_id, type, status, title, synopsis, releaseYear, genres, productionCountries, audioLanguages, subtitleLanguages, ageRating, coverUrl, createdAt, updatedAt` + el subdocumento de su tipo (`oneOf`) | `ix_content_title_text` (texto, español), `ix_content_status_type_year`, `ix_content_status_genres`, `ux_content_episode_id` (único parcial) |
| `MyList` | Un contenido en la lista de un perfil | `_id, profileId, contentId, addedAt` | `ux_mylist_profile_content` (único) |
| `Watch_sessions` | Una sesión de reproducción | `_id, profileId, subscriptionId, planId, contentId, contentType, startedAt, watchedSeconds, lastPositionSeconds, durationSeconds, completed, state, lastActivityAt` (+ `episodeId` si es serie) | `ix_sessions_profile_activity`, `ix_sessions_report` |
| `Admin_user` | Un administrador | `_id, email, fullName, passwordHash, createdAt` | `ux_admin_email` (único) |
| `counters` | Último número usado por prefijo | `_id` (`MOV`/`SER`/`DOC`), `seq` | — |

El detalle de cada campo y de cada decisión está en [modelo-documental.md](modelo-documental.md).

### 2.1 Mapeo Java (Spring Data MongoDB)

| Colección | Clase `@Document` | Repositorio |
|---|---|---|
| `Content` | `Content` (implementa `Persistable<String>`; embebe `MovieDetails`, `CastMember`, `SeriesDetails`, `Season`, `Episode`, `DocumentaryDetails`) | `ContentRepository` |
| `MyList` | `MyListItem` | `MyListRepository` |
| `Watch_sessions` | `WatchSession` | `WatchSessionRepository` |
| `Admin_user` | `AdminUser` | `AdminUserRepository` |

## Anexo: salida de los scripts

_Pegar aquí la salida de `07_object_catalog.sql` (Oracle) y `08_catalog.js` (MongoDB) después de cargar las dos bases._
