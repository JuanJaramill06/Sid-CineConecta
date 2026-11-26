# CineConecta

Web application for a subscription-based streaming platform (movies, series and documentaries). It manages accounts, profiles, plans, subscriptions and simulated payments, a flexible content catalog, and the viewing activity of each profile, as a foundation for a future recommendation module.

The project uses **polyglot persistence**: a relational database (Oracle SQL) for transactional data and MongoDB for the catalog and viewing activity.

## Team members

* Tomas Jaramillo
* Juan Esteban Guerrero
* Juan Fernando Jaramillo
* María Juliana Niño

## Scope

Includes data management and simulated playback. It does not include video streaming, real payments, AI-based recommendations, or control of simultaneous streams per plan.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java + Spring Boot (Spring Web, Spring Data JPA, Spring Data MongoDB, Spring Security) |
| Frontend | React |
| Relational database | Oracle SQL |
| Document database | MongoDB |

## Data distribution

| Engine | Data | Role |
|---|---|---|
| Oracle | Accounts, profiles, plans, subscriptions and payments | Source of truth. Atomic payment + subscription transaction |
| MongoDB | Catalog (`Content`), `MyList`, `Watch_sessions` and `Admin_user` | Flexible catalog, viewing activity and administrators |

The two databases are linked only through identifiers (`profileId`, `subscriptionId`, `planId`), with a one-way reference (MongoDB → Oracle). Before writing to MongoDB, the application validates in Oracle that the profile is active, the subscription is valid, and the content is allowed for the profile.

## Repository structure


```
cineconecta/
├── backend/        # REST API (Spring Boot)
├── frontend/       # React application
├── database/
│   ├── oracle/     # Creation scripts and synthetic data load (DDL and data)
│   └── mongodb/    # Validations, indexes and data load
├── docs/           # Document model, object catalog and evidence
├── scripts/        # Demo scripts against the running API
└── README.md
```

## Prerequisites

- Java 21
- Maven
- Node.js and npm
- Access to an Oracle Database (course instance, SID `ESTUD`, or a local installation) and a user with permissions to create tables, indexes and views
- MongoDB (local or MongoDB Atlas)

## Credentials

The backend and the MongoDB scripts read their connections from four environment variables. Define them once as **user** variables in PowerShell so they persist after closing the terminal (then open a new terminal):

```powershell
[Environment]::SetEnvironmentVariable('ORACLE_URL', 'jdbc:oracle:thin:@HOST:1521:ESTUD', 'User')
[Environment]::SetEnvironmentVariable('ORACLE_USER', 'your_user', 'User')
[Environment]::SetEnvironmentVariable('ORACLE_PASSWORD', 'your_password', 'User')
[Environment]::SetEnvironmentVariable('MONGO_URI', 'mongodb+srv://user:password@cluster.mongodb.net/', 'User')
```

Optionally, a fifth variable signs the login tokens. Without it the backend generates a random key at startup, so every restart logs everyone out (fine while developing). Generate one per machine and never share or commit it:

```powershell
$key = [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
[Environment]::SetEnvironmentVariable('JWT_SECRET', $key, 'User')
```

Never commit real values. Spring Boot 4 reads MongoDB from `spring.mongodb.uri` / `spring.mongodb.database`; the old `spring.data.mongodb.*` names are ignored and the app would silently connect to `localhost:27017`.

### Least privilege

- **MongoDB Atlas:** the application user has only the `readWrite` role on the `cineconecta` database (no `atlasAdmin`, no access to other databases).
- **Oracle:** ideally the schema owner (creates tables, the view and the procedure) and the application user are different. The application user only needs:

```sql
-- Run as the schema owner (OWNER), replace APP_USER with the application user
GRANT CREATE SESSION TO APP_USER;  -- usually granted by the DBA
GRANT SELECT, INSERT, UPDATE ON OWNER.accounts      TO APP_USER;
GRANT SELECT, INSERT, UPDATE ON OWNER.profiles      TO APP_USER;
GRANT SELECT                 ON OWNER.plans         TO APP_USER;
GRANT SELECT, INSERT         ON OWNER.subscriptions TO APP_USER;
GRANT SELECT, INSERT         ON OWNER.payments      TO APP_USER;
GRANT SELECT                 ON OWNER.v_active_subscriptions TO APP_USER;
GRANT EXECUTE                ON OWNER.sp_contract_subscription TO APP_USER;
```

No `DELETE` and no DDL: profiles are deactivated, never deleted, and subscriptions/payments are append-only. On the course instance (`ESTUD`) each student has a single assigned user and cannot create users, so the application connects with that user; these grants document the intended least-privilege setup.

## Running the project

> **Load both databases on the same day** (ideally right before the demo). All dates are relative to the load day: Mongo sessions must fall inside the Oracle subscription periods, subscription 11 "ends today at 00:00" only on the load day, and the current volume subscriptions last about 10 days.

### 1. Oracle database

Connect with your assigned user in SQL Developer and run each script with **Run Script (F5)**:

| Order | Script | What it does |
|---|---|---|
| 1 | `database/oracle/01_cineconecta_oracle.sql` | Schema, view, `sp_contract_subscription` and functional seed (accounts 1–10) |
| 2 | `database/oracle/04_volume_load.sql` | Volume: 10,000 accounts, 25,000 profiles, 120,000 subscriptions and payments. Re-runnable |
| 3 | `database/oracle/05_verification.sql` | Invalid cases, procedure errors, mid-transaction failure, history. Prints PASS/FAIL and ends with `ROLLBACK` |
| 4 | `database/oracle/05b_concurrency_demo.sql` | Two-session demo, run step by step (second worksheet with Ctrl+Shift+N, autocommit off) |
| 5 | `database/oracle/06_index_experiment.sql` | Index before/after on the income report |
| 6 | `database/oracle/07_object_catalog.sql` | Object catalog from the data dictionary |

### 2. MongoDB

Install the MongoDB Shell once (then open a new terminal):

```powershell
winget install MongoDB.Shell
```

Every script selects the `cineconecta` database itself, so `MONGO_URI` can point to the cluster root. Run them from the repository root, in this order:

```powershell
mongosh "$env:MONGO_URI" --file database/mongodb/01_collections.js
```

| Order | Script | What it does |
|---|---|---|
| 1 | `01_collections.js` | **Drops and recreates** the 5 collections with their `$jsonSchema` validators |
| 2 | `02_indexes.js` | Indexes (idempotent) |
| 3 | `03_seed.js` | Functional seed with the same ids as the Oracle seed (re-runnable) |
| 4 | `07_validation_tests.js` | 12 expected rejections; exits with an error if any fails |
| 5 | `04_volume.js` | Volume: 5,000 contents and 200,000 sessions, same id contract as `04_volume_load.sql` |
| 6 | `06_operations.js` | Reference operations: search, open series, My list, playback lifecycle, Continue watching, consumption report |
| 7 | `05_index_experiment.js` | Index before/after (indexes are always restored) |
| 8 | `08_catalog.js` | Collections, validators and indexes |

Documentation: [document model](docs/modelo-documental.md), [indexes and optimization](docs/indices-y-optimizacion.md), [object catalog](docs/catalogo-objetos.md).

### 3. Backend

1. Configure the connections through **user environment variables** (see [Credentials](#credentials)). `application.properties` only references them; it never contains real values.

2. Start the API:

```powershell
cd backend; .\mvnw.cmd spring-boot:run
```

The API runs at `http://localhost:8080` and Swagger UI at `http://localhost:8080/swagger-ui.html`.

#### Authentication

| Who | Login | Token works on |
|---|---|---|
| Customer (Oracle `ACCOUNTS`) | `POST /api/auth/register`, `POST /api/auth/login` | `/api/**` except `/api/admin/**` |
| Administrator (MongoDB `Admin_user`) | `POST /api/admin/auth/login` | `/api/admin/**` only |

- Send the token as `Authorization: Bearer <token>`. `GET /api/auth/me` and `GET /api/admin/auth/me` show who it belongs to. Public routes: register, both logins and `GET /api/plans`.
- A customer token on an admin route, or the other way round, gets **403**: the server checks the role on every request (RNF3).
- Routes that act on a profile take it from the `X-Profile-Id` header. In a controller, declare `@CurrentProfile Profile profile`: it arrives already checked against the account in the token and active (RNF2, RF12). Never read a `profileId` from the body or the URL.
- Test logins: customers `juan.perez@example.com` (accounts 1–10, not account 4) and admins `admin@cineconecta.com`. Passwords are in the handoff notes and the seed comments.

#### Errors

Every error answers `{ code, message, timestamp, details? }`:

| Status | When |
|---|---|
| 400 | Invalid body or parameters (`details` lists each field), missing `X-Profile-Id` |
| 401 | No token, invalid or expired token, wrong email or password |
| 403 | Wrong role, or a profile of another account |
| 404 | Not found, including procedure errors `-20003` / `-20004` |
| 409 | Duplicate email, profile name or My list item; overlapping subscription (`-20001`) |
| 422 | Business rule, e.g. inactive profile or too many active profiles for the plan (`-20002`) |
| 402 | Simulated payment rejected: nothing was stored |
| 503 | Oracle or MongoDB not available: the operation is rejected, never allowed by default (RNF12) |

#### Accounts, profiles and subscriptions (Oracle)

All of them take the account from the token; none accepts an `accountId` from the client.

| Method and route | What it does |
|---|---|
| `GET /api/plans` | Plans, cheapest first. Public |
| `GET /api/subscriptions` | History, newest first, with `active` (RF4, RF17) |
| `GET /api/subscriptions/current` | Current subscription, or 404 if there is none |
| `POST /api/subscriptions` `{planId, approvePayment}` | Contract. `approvePayment: false` simulates a rejected payment (402, nothing stored). Otherwise `sp_contract_subscription` inserts subscription + payment in one transaction. 409 if a period overlaps (`-20001`), 422 if there are more active profiles than the plan allows (`-20002`) |
| `GET /api/payments` | Payment history (RF5) |
| `GET /api/profiles` | Every profile of the account, with `active` |
| `GET /api/profiles/current` | The profile in `X-Profile-Id`, only if it is active and belongs to the account (RF12) |
| `POST /api/profiles` `{name, maxAgeRating}` | Needs a current subscription and a free slot in its plan (422); name unique ignoring case (409). Locks the account row first, so concurrent requests cannot exceed the plan (RF9, RF10) |
| `PUT /api/profiles/{id}` | Rename or change `maxAgeRating` |
| `POST /api/profiles/{id}/deactivate` / `activate` | `isActive = 'N'` / `'Y'`; never deleted (RF11). Activating follows the same rules as creating |

#### Catalog and admin content (MongoDB)

Client routes need a current subscription (`ReferenceValidationService.requireActiveSubscription`, 422 if none) and the active profile in `X-Profile-Id`; admin routes are already restricted to `ROLE_ADMIN`.

| Method and route | What it does |
|---|---|
| `GET /api/catalog` `?title&type&genre&language&year&page&size` | Search/filter/paginate (RF32-34). Only `PUBLISHED` content with `ageRating <= ` the profile's limit. `title` runs a `$text` search sorted by relevance; otherwise newest first |
| `GET /api/catalog/{id}` | Full detail, with seasons/episodes for a series (RF35). Not published, withdrawn, or above the profile's rating: 403 (same answer as "does not exist", so it never reveals which one it is) |
| `GET /api/admin/content` | Every content, any status |
| `GET /api/admin/content/{id}` | Full detail, any status |
| `POST /api/admin/content` | Create (RF24-31). The id (`MOV-00001`/`SER-00001`/`DOC-00001`) and each episode's id are generated server-side and never reused (RF63); starts as `DRAFT`. Exactly the subdocument matching `type` is accepted; repeated season/episode numbers: 422 |
| `PUT /api/admin/content/{id}` | Update in place; `type` cannot change |
| `POST /api/admin/content/{id}/publish` / `withdraw` | `status` -> `PUBLISHED` / `WITHDRAWN`; never deletes (RF60-62) |

#### My list, playback and Continue watching (MongoDB)

My list only needs an active profile (no subscription check); playback needs both, through `PlaybackEligibilityService`.

| Method and route | What it does |
|---|---|
| `GET /api/my-list` | Every item of the profile; a withdrawn content stays with `available: false` (RF39) |
| `POST /api/my-list` `{contentId}` | Adds it; 409 if it is already there (RF37) |
| `DELETE /api/my-list/{contentId}` | Removes it; a no-op if it was not there |
| `POST /api/playback/start` `{contentId, episodeId?}` | Authorizes (current subscription, published, `ageRating`), closes any session left open for the same profile+content, and starts a new one `PLAYING`. `episodeId` is required for a series and forbidden otherwise (422) |
| `POST /api/playback/{sessionId}/pause` \| `resume` \| `finish` | Conditional on the session's current `state`; a second, concurrent call for the same transition is a no-op, never a duplicated `watchedSeconds` (RF46, RF48, RF56) |
| `POST /api/playback/{sessionId}/seek` `{positionSeconds}` | Moves `lastPositionSeconds`; the jump itself never adds watched time (RF47, RF53) |
| `GET /api/continue-watching` | Last non-completed session per content, newest activity first; a completed series moves to its next episode from 0 (RF57-61) |

#### Reports (Oracle + MongoDB)

Both take `from`/`to` as inclusive calendar dates (`?from=2026-01-01&to=2026-01-31`) and an optional `&format=csv` (JSON otherwise). Already restricted to `ROLE_ADMIN`.

| Method and route | What it does |
|---|---|
| `GET /api/admin/reports/income` | Total collected and subscriptions acquired per plan, from `payments` (RF66) |
| `GET /api/admin/reports/consumption` | Minutes watched and distinct profiles per plan and content type, from `Watch_sessions` — each session counts under the plan vigente when it started (RF68) and a series by its episodes (RF69); plan names are added from Oracle |

### 4. Frontend

```bash
cd frontend
npm install
npm run dev
```

The application runs at `http://localhost:5173`.

> The backend must be running before using the frontend: the frontend only displays data and sends requests to the API, which is the one that talks to both databases.

#### Working on the frontend without the databases (mock API, dev only)

`frontend/mock/mock-api.mjs` imitates the API in memory with the real MongoDB seed (`03_seed.js`) and a copy of the Oracle seed. Use it to build or review screens when Oracle/Atlas are not at hand. **It is not the backend:** it skips many edge cases, and nothing in it counts as evidence for the sustentación. Run it in one terminal and the frontend in another:

```powershell
cd frontend; npm run mock
```

```powershell
cd frontend; npm run dev
```

It listens on port 8080, so stop the real backend first. To run both, change the mock's port and point the frontend to it:

```powershell
cd frontend; $env:MOCK_PORT = '8081'; npm run mock
```

```powershell
cd frontend; $env:VITE_API_BASE_URL = 'http://localhost:8081'; npm run dev
```

Logins are the same as in the seeds (accounts 1–10 and the two admins; the passwords are in the seed comments). Data resets every time the mock restarts.

## Tests

```powershell
cd backend; .\mvnw.cmd test
```

Without environment variables the context test is skipped, so `mvn test` passes offline. The JWT, security, error-mapping and profile-check tests need no database. With `MONGO_URI` defined, `MongoMappingIT` checks the real Atlas connection (at least 20 contents) and a save/read round trip against the validators:

```powershell
cd backend; .\mvnw.cmd test -Dtest=MongoMappingIT
```

Database-level evidence:

- **Transaction failure (RF19):** `05_verification.sql` calls the procedure with a duplicate payment reference. The payment insert fails and the subscription is rolled back too.
- **Concurrency (RNF10):** `05b_concurrency_demo.sql` runs two sessions:
  - Two simultaneous subscriptions for the same account: the second one waits on the `FOR UPDATE` lock, then gets `-20001`.
  - Two simultaneous profile creations: without the account lock both succeed and break RF10; with it, the second one is rejected. `ProfileServiceImpl` takes the same lock.
- **MongoDB:** conditional updates on the session state stop a duplicated "pause" from adding time twice (`06_operations.js`).

Application-level evidence (through the API, for the sustentación): `scripts/demo-concurrencia-http.ps1` creates a new account each run, so account 4 stays clean, and checks:

1. a rejected payment answers 402 and stores nothing;
2. two simultaneous contracts for the same account: one 201, the other 409;
3. two simultaneous profile creations with one free slot: one 201, the other 422.

With the backend running:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\demo-concurrencia-http.ps1
```

To also show a failure between the inserts and the commit (RF19, RNF11), start the backend with the demo switch on and add `-IncludeFailure`. The request fails with 500 `SIMULATED_FAILURE` after both inserts, and the account ends with 0 subscriptions and 0 payments:

```powershell
$env:DEMO_SIMULATE_FAILURES = 'true'; cd backend; .\mvnw.cmd spring-boot:run
```

```powershell
powershell -ExecutionPolicy Bypass -File scripts\demo-concurrencia-http.ps1 -IncludeFailure
```

## Additional documentation

- [MongoDB data model and justification](docs/modelo-documental.md)
- [Object catalog (Oracle and MongoDB)](docs/catalogo-objetos.md)
- [Index justification and before/after optimization comparison](docs/indices-y-optimizacion.md)
- Reflection on the use of generative AI <!-- pending -->