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

## Running the project

### 1. Oracle database

1. Connect with your assigned user (for example, using SQL Developer).
2. Run the scripts in `database/oracle/` in order: table creation, constraints and indexes, then synthetic data load.

### 2. MongoDB

1. Create the database and collections with their validations and indexes.
2. Run the data load from `database/mongodb/`.

### 3. Backend

1. Configure the connections in `backend/src/main/resources/application.properties`:

```properties
# Oracle
spring.datasource.url=jdbc:oracle:thin:@HOST:1521:ESTUD
spring.datasource.username=YOUR_USER
spring.datasource.password=YOUR_PASSWORD

# MongoDB
spring.data.mongodb.uri=mongodb://localhost:27017/cineconecta
```

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