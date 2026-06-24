# Backend (Spring Boot)

Modern Spring Boot backend API used by the frontend reference application.

This backend is intentionally designed as a lightweight instructional service
focused on:

- frontend/backend communication
- REST API design
- layered architecture
- DTO usage
- file-backed local persistence with a PostgreSQL migration path
- local development workflows
- backend tooling and CI integration

The goal is not production complexity, but establishing clean backend
engineering patterns and workflows that can scale over time.

---

## Tech stack

### Core

- Java 21
- Spring Boot 4
- Spring MVC
- Maven
- Jackson
- Spring JDBC
- PostgreSQL
- Flyway

### Tooling

- Maven Wrapper
- Spring Boot DevTools
- Spotless
- SortPom
- GitHub Actions
- JaCoCo
- Snyk
- OWASP Dependency Check

---

## Requirements

- Java 21+
- No database required for the default JSON-backed profile
- No external infrastructure required

Verify Java installation:

```sh
java -version
```

---

## Environment notes (Windows)

This project assumes:

- Java 21 or newer is installed
- `JAVA_HOME` points to the JDK installation directory
- `%JAVA_HOME%\bin` is available on the system PATH

---

## Project initialization

This project was generated using Spring Initializr:

```text
https://start.spring.io
```

Configuration:

- Project: Maven
- Language: Java
- Spring Boot: 4.x
- Packaging: Jar
- Java: 21

Dependencies:

- Spring Web MVC
- Spring Boot Actuator
- Lombok

---

## Running the application

From the `backend` directory:

### Standard startup

```sh
./mvnw spring-boot:run
```

### Development profile (recommended)

```powershell
$env:SPRING_PROFILES_ACTIVE="postgres"
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/financial_app"
$env:DATABASE_USERNAME="financial_app_user"
$env:DATABASE_PASSWORD="financial_app_password"

./mvnw.cmd -Pdev spring-boot:run
```

The `dev` profile enables Spring Boot DevTools for improved local development
experience. The `postgres` Spring profile enables PostgreSQL persistence using
the dedicated `financial_app_user` account.

Backend URL:

```text
http://localhost:8080
```

The frontend runs separately at:

```text
http://localhost:3000/
```

From the repository root, the PostgreSQL-backed backend can also be started with:

```powershell
.\scripts\start-backend-postgres.ps1
```

---

## Available endpoints

The current API is organized around a versioned financial snapshot aggregate.
The main read and save endpoints operate on the full financial workspace.

### Get financial snapshot

```http
GET /api/v1/financials
```

Returns the current pay period, withdrawals, income summaries, income calendar
events, asset category totals, debt balances, important dates, and calculated
overview totals.

---

### Save financial snapshot

```http
PUT /api/v1/financials
```

Persists the full edited financial snapshot in one request. This is the main
endpoint used by the frontend draft/save workflow.

---

### Granular bill endpoints

```http
POST /api/v1/financials/bills
PUT /api/v1/financials/bills/{id}
DELETE /api/v1/financials/bills/{id}
PUT /api/v1/financials/pay-period
```

These endpoints remain available for direct bill and pay period updates, even
though the current UI saves the full snapshot as the source of truth.

---

## Financial data storage

Financial data is stored in a local JSON file by default:

```text
backend/data/financials.local.json
```

That file is ignored by Git and should contain the user's real local financial
data. The committed template lives at:

```text
backend/data/financials.example.json
```

On startup, `FinancialsRepository` creates `financials.local.json` from the
example file when the local file does not already exist. This keeps the feature
usable after a fresh clone without committing personal data.

The storage path can be overridden with Spring configuration:

```properties
financials.data.path=data/financials.local.json
financials.example-data.path=data/financials.example.json
```

## PostgreSQL profile

PostgreSQL is the planned production persistence path. The current application
still uses the JSON repository by default, but a database profile and initial
schema migrations are available for the migration work.

Run with the `postgres` profile to enable datasource auto-configuration and
Flyway:

```sh
./mvnw spring-boot:run
```

Connection settings:

```properties
SPRING_PROFILES_ACTIVE=postgres
DATABASE_URL=jdbc:postgresql://localhost:5432/financial_app
DATABASE_USERNAME=financial_app_user
DATABASE_PASSWORD=financial_app_password
```

### Local PostgreSQL setup with pgAdmin

Use pgAdmin for local setup on Windows because `psql.exe` may be blocked by
Windows Application Control.

Local server context:

| Purpose        | Value                |
| -------------- | -------------------- |
| Host           | `localhost`          |
| Port           | `5432`               |
| Admin database | `postgres`           |
| Admin username | `postgres`           |
| App database   | `financial_app`      |
| App username   | `financial_app_user` |

Do not store the local admin password in repository documentation. The backend
does not use the admin account; it connects with the dedicated app user.

1. Open pgAdmin and connect to the local PostgreSQL server as the admin user.
2. Create the app login:
   - Right-click **Login/Group Roles**.
   - Select **Create** > **Login/Group Role**.
   - On **General**, set **Name** to `financial_app_user`.
   - On **Definition**, set **Password** to `financial_app_password`.
   - On **Privileges**, enable **Can login**.
   - Leave **Superuser** disabled.
3. Create the app database:
   - Right-click **Databases**.
   - Select **Create** > **Database**.
   - Set **Database** to `financial_app`.
   - Set **Owner** to `financial_app_user`.
4. Verify the app connection:
   - Add or open a server connection using host `localhost`, port `5432`,
     database `financial_app`, username `financial_app_user`, and password
     `financial_app_password`.
   - Confirm pgAdmin connects successfully as the app user.

The initial schema is managed in:

```text
src/main/resources/db/migration/V1__create_financials_schema.sql
src/main/resources/db/migration/V2__create_financial_snapshot_document.sql
```

The active PostgreSQL implementation stores the full financial snapshot in the
`financial_snapshot_document.snapshot_json` `jsonb` column. This intentionally
keeps the current frontend behavior unchanged: the browser still loads one
snapshot, edits a local draft, and saves one snapshot back to the API.

When PostgreSQL is empty, the backend seeds the first active snapshot from
`backend/data/financials.local.json` when that file exists. If no local file is
available, it falls back to `backend/data/financials.example.json`.

---

## Project structure

```text
backend/
|-- data/
|   |-- financials.example.json
|   `-- financials.local.json        # ignored by Git
|-- src/main/java/
|   |-- com/example/backend/api/
|   |-- com/example/backend/dto/
|   |   `-- financials/
|   |-- com/example/backend/repository/
|   |-- com/example/backend/service/
|   `-- com/example/backend/BackendApplication.java
|-- src/test/java/
|-- src/main/resources/db/migration/
|-- pom.xml
`-- README.md
```

---

## Architecture overview

### `api/`

REST controllers.

Responsibilities:

- HTTP request handling
- request/response mapping
- status code handling
- API boundary definition

### `dto/`

Data Transfer Objects.

Responsibilities:

- request payloads
- response payloads
- API contract typing

### `repository/`

Persistence-facing data models and storage adapters.

Responsibilities:

- loading local JSON data
- writing local JSON data
- assigning IDs for new local rows
- keeping persistence concerns out of controllers

### `service/`

Business/service layer.

Responsibilities:

- application logic
- validation
- pay period calculations
- totals and snapshot aggregation
- orchestration between API and repository layers

---

## Financials domain behavior

The financials service calculates:

- monthly withdrawal total
- paid total
- unpaid total
- pay period total
- annual withdrawal total
- annual withdrawals due in the active pay period
- per-category asset totals
- total tracked assets
- total debt
- net worth
- income calendar monthly paycheck counts

Pay periods are stored as start and end dates. When a snapshot is read, the
service advances or rewinds the stored pay period window to include the current
date while preserving the original period length.

Withdrawal due dates are derived from each row's due day and the active pay
period month. Days beyond the end of a month are clamped to the last valid day
of that month.

Annual withdrawals are stored as recurring month/day values. The service
projects them into the active pay period year so they can be displayed as
`MM/DD/YYYY` and included in pay period planning.

The persisted snapshot currently includes monthly bills, annual withdrawals,
asset accounts, debt accounts, the bi-weekly net income source item, income
calendar events, and important dates. Derived income summary rows are calculated
by the frontend. The default `json` profile stores this aggregate in a local
file. The `postgres` profile stores the same aggregate as a PostgreSQL `jsonb`
document until the project needs more granular relational CRUD.

---

## Development notes

### Spring Boot DevTools

DevTools are isolated to the `dev` Maven profile to avoid accidental inclusion
in production builds.

Benefits:

- automatic restart
- improved local iteration speed
- enhanced development workflow

### Spotless

Spotless is used to enforce consistent Java formatting.

Format Java source:

```sh
./mvnw spotless:apply
```

Verify formatting:

```sh
./mvnw spotless:check
```

### SortPom

SortPom is used to keep `pom.xml` consistently organized and formatted.

Format `pom.xml`:

```sh
./mvnw sortpom:sort
```

Verify `pom.xml` formatting:

```sh
./mvnw sortpom:verify
```

---

## Local frontend integration

The frontend communicates with the backend through the Vite development proxy.

Frontend requests:

```text
/api/*
```

are proxied to:

```text
http://localhost:8080
```

This avoids local CORS configuration requirements during development.

---

## Build the application

```sh
./mvnw clean verify
```

## PostgreSQL integration test

The PostgreSQL snapshot store has an opt-in integration test so normal builds do
not require a local database. The test creates and drops its own schema named
`financial_snapshot_store_test` inside the configured database.

PowerShell:

```powershell
$env:RUN_POSTGRES_INTEGRATION_TESTS="true"
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/financial_app"
$env:DATABASE_USERNAME="financial_app_user"
$env:DATABASE_PASSWORD="financial_app_password"

./mvnw test "-Dtest=PostgresFinancialsSnapshotStoreIT" "-Djacoco.skip=true"
```

The `jacoco.skip` flag keeps this local database smoke test focused and avoids
Java agent noise when running on newer local JDKs.

---

## CI / Security tooling

The backend participates in repository CI pipelines for:

- Maven builds
- dependency scanning
- Snyk security analysis
- OWASP dependency checks
- formatting verification

---

## Notes

Intentional simplifications:

- JSON remains the default local fallback
- PostgreSQL stores the full snapshot as `jsonb`; granular database-backed CRUD
  is not implemented yet
- no authentication
- no authorization
- no external APIs
- no production deployment infrastructure

Focus areas:

- backend architecture fundamentals
- clean API boundaries
- local persistence boundaries
- maintainability
- local development experience
- frontend/backend integration
- backend engineering standards
