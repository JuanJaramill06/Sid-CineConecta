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

### 1. Oracle database

1. Connect with your assigned user (for example, using SQL Developer).
2. Run the scripts in `database/oracle/` in order: table creation, constraints and indexes, then synthetic data load.

### 2. MongoDB

1. Create the database and collections with their validations and indexes.
2. Run the data load from `database/mongodb/`.

### 3. Backend

1. Configure the connections through **user environment variables** (see [Credentials](#credentials)). `application.properties` only references them; it never contains real values.

2. Start the API:

```bash
cd backend
mvn spring-boot:run
```

The API runs at `http://localhost:8080`.

### 4. Frontend

```bash
cd frontend
npm install
npm run dev
```

The application runs at `http://localhost:5173`.

> The backend must be running before using the frontend: the frontend only displays data and sends requests to the API, which is the one that talks to both databases.

## Tests

```bash
cd backend
mvn test
```

<!-- Describe here the concurrency tests (two simultaneous subscription requests) and the transaction failure tests -->

## Additional documentation

<!-- Add links when ready -->

- MongoDB data model and justification
- Oracle object catalog
- Index justification and before/after optimization comparison
- Reflection on the use of generative AI