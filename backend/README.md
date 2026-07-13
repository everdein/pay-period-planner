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

The goal is not production complexity. The goal is to establish clean backend
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
- migration SQL under `src/main/resources/db/migration`

### Tooling

- Maven Wrapper
- Spring Boot DevTools
- Spotless
- SortPom
- GitHub Actions
- JaCoCo
- Snyk

---

## Requirements

- Java 21+
- Maven Wrapper through `mvnw.cmd`
- No database required for the default JSON-backed profile
- PostgreSQL required only for the `postgres` profile

Verify Java installation:

```powershell
java -version
javac -version
echo $env:JAVA_HOME
```

Verify Maven Wrapper:

```powershell
.\mvnw.cmd -v
```

Expected:

```text
Java 21+
Maven running with Java 21+
```

---

## Environment notes: Windows

This project assumes:

- Java 21 or newer is installed
- `JAVA_HOME` points to the JDK installation directory
- `%JAVA_HOME%\bin` is available on the system PATH

Example:

```text
JAVA_HOME=C:\Program Files\Java\jdk-21.0.11
```

If PowerShell blocks `npm` or other scripts in the full repository setup, allow
local user scripts:

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

---

## Running the backend

The backend has two local storage modes. Choose one backend startup path, then
run the frontend separately.

| Path | Storage                   | Best for                            |
| ---- | ------------------------- | ----------------------------------- |
| A    | Local JSON file           | Fastest startup without PostgreSQL  |
| B    | PostgreSQL JSONB snapshot | Testing database-backed persistence |

A running backend should keep its terminal open. If the command returns to the
PowerShell prompt, the backend stopped during startup; check the last Spring Boot
log lines before `BUILD SUCCESS` or `BUILD FAILURE`.

Local API sign-in defaults:

```text
Username: financial_app
Password: financial_app_local_password
```

Override these with `FINANCIALS_API_USERNAME` and `FINANCIALS_API_PASSWORD`
before starting the backend. These credentials protect every
`/api/v1/financials/**` endpoint during local development; do not reuse them for
real deployment.

Runtime guardrails:

- only `/actuator/health` and `/actuator/info` are exposed by default
- backend error responses do not include stack traces or binding internals
- request bodies above `FINANCIALS_MAX_REQUEST_BYTES` are rejected with `413`
  before controller handling; default is `1048576`
- cross-origin browser calls are denied unless `FINANCIALS_ALLOWED_ORIGINS`
  names exact allowed origins
- activating `prod` requires the `postgres` profile, non-default API
  credentials, and no wildcard CORS origin

---

## Path A: Default JSON-backed startup

Use this when you want the backend to run without PostgreSQL.

From the `backend` directory:

```powershell
.\mvnw.cmd spring-boot:run
```

Backend URL:

```text
http://localhost:8080
```

In this mode, the backend stores data in:

```text
backend/data/financials.local.json
```

If that file does not exist, the backend creates it from:

```text
backend/data/financials.example.json
```

This is the fastest path after a fresh clone.

---

## Path B: PostgreSQL-backed startup

Use this when testing or developing the database-backed persistence path.

The `postgres` Spring profile enables PostgreSQL persistence using the dedicated
database user:

```text
financial_app_user
```

Important: `financial_app_user` is a PostgreSQL database user. It is not a
frontend application login.

Connection settings:

```properties
SPRING_PROFILES_ACTIVE=postgres
DATABASE_URL=jdbc:postgresql://localhost:5432/financial_app
DATABASE_USERNAME=financial_app_user
DATABASE_PASSWORD=financial_app_password
```

From the repository root, prepare PostgreSQL with:

```powershell
cd C:\Users\<you>\dev\end-to-end-app
.\scripts\setup-local-postgres.ps1
```

Then start the PostgreSQL-backed backend from the repository root with:

```powershell
.\scripts\start-backend-postgres.ps1
```

The script starts Spring Boot with the `postgres` profile and the dedicated
database user `financial_app_user`; it does not run the application as the
PostgreSQL admin user.

Or run manually from the `backend` directory:

```powershell
$env:SPRING_PROFILES_ACTIVE="postgres"
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/financial_app"
$env:DATABASE_USERNAME="financial_app_user"
$env:DATABASE_PASSWORD="financial_app_password"

.\mvnw.cmd spring-boot:run
```

To include the Maven `dev` profile and Spring Boot DevTools:

```powershell
$env:SPRING_PROFILES_ACTIVE="postgres"
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/financial_app"
$env:DATABASE_USERNAME="financial_app_user"
$env:DATABASE_PASSWORD="financial_app_password"

.\mvnw.cmd -Pdev spring-boot:run
```

Backend URL:

```text
http://localhost:8080
```

The frontend runs separately at:

```text
http://localhost:3000/
```

---

## Local PostgreSQL setup

PostgreSQL is the planned production persistence path. The default backend mode
still uses JSON-backed local storage, but the `postgres` profile and initial
schema migrations are available for database-backed development.

The preferred setup path is the repository setup script, run from the repository
root:

```powershell
.\scripts\setup-local-postgres.ps1
```

The script prompts for the local PostgreSQL admin password for user `postgres`.
This password is not stored by the script or documented in the repository.

The script creates or updates:

```text
Database: financial_app
Database user: financial_app_user
Database password: financial_app_password
```

It also applies the local migration SQL files from:

```text
backend/src/main/resources/db/migration/V1__create_financials_schema.sql
backend/src/main/resources/db/migration/V2__create_financial_snapshot_document.sql
backend/src/main/resources/db/migration/V3__create_financial_record_snapshot_schema.sql
backend/src/main/resources/db/migration/V4__add_financial_record_app_id_constraints.sql
```

The script is idempotent and can be safely rerun. If the database already
exists, it skips database creation. If the target tables already exist, it skips
the SQL file application and verifies the `financial_snapshot_document` and
`financial_record_snapshot` tables.

After setup completes, start the PostgreSQL-backed backend from the repository
root:

```powershell
.\scripts\start-backend-postgres.ps1
```

Manual pgAdmin and `psql` setup steps below are kept as a fallback for
troubleshooting or learning purposes.

---

## Local server context

| Purpose        | Value                    |
| -------------- | ------------------------ |
| Host           | `localhost`              |
| Port           | `5432`                   |
| Admin database | `postgres`               |
| Admin username | `postgres`               |
| App database   | `financial_app`          |
| App username   | `financial_app_user`     |
| App password   | `financial_app_password` |

Do not store the local PostgreSQL admin password in repository documentation.
The backend does not use the admin account; it connects with the dedicated app
database user.

---

## Test the PostgreSQL setup script

To test the setup script without touching the real `financial_app` database, run
it against a throwaway database:

```powershell
.\scripts\setup-local-postgres.ps1 -AppDatabase financial_app_script_test
```

Run it a second time to verify it is repeatable/idempotent. The second run
should skip database creation and existing migrations.

Clean up the throwaway database:

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U postgres -d postgres -c "DROP DATABASE IF EXISTS financial_app_script_test;"
```

---

## Manual PostgreSQL setup with pgAdmin

Use this path when setting up manually or debugging the setup script.

1. Open pgAdmin.
2. Connect to the local PostgreSQL server as the admin user:
   - Host: `localhost`
   - Port: `5432`
   - Database: `postgres`
   - User: `postgres`
3. Open Query Tool.
4. Run:

```sql
CREATE USER financial_app_user WITH PASSWORD 'financial_app_password';
CREATE DATABASE financial_app OWNER financial_app_user;
GRANT ALL PRIVILEGES ON DATABASE financial_app TO financial_app_user;
```

If the user already exists but the password is wrong:

```sql
ALTER USER financial_app_user WITH PASSWORD 'financial_app_password';
```

If the database already exists:

```sql
ALTER DATABASE financial_app OWNER TO financial_app_user;
GRANT ALL PRIVILEGES ON DATABASE financial_app TO financial_app_user;
```

---

## Verify the app database connection

In pgAdmin, create or open a server connection using:

```text
Server Name: Financial App Local
Host name/address: localhost
Port: 5432
Database: financial_app
User: financial_app_user
Password: financial_app_password
```

Open Query Tool and run:

```sql
SELECT current_database(), current_user;
```

Expected:

```text
financial_app | financial_app_user
```

---

## Manual database migrations

The initial schema is managed in:

```text
src/main/resources/db/migration/V1__create_financials_schema.sql
src/main/resources/db/migration/V2__create_financial_snapshot_document.sql
src/main/resources/db/migration/V3__create_financial_record_snapshot_schema.sql
src/main/resources/db/migration/V4__add_financial_record_app_id_constraints.sql
```

The preferred path is still:

```powershell
.\scripts\setup-local-postgres.ps1
```

If `psql` is available on the PATH, run from the repository root:

```powershell
psql -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V1__create_financials_schema.sql

psql -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V2__create_financial_snapshot_document.sql

psql -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V3__create_financial_record_snapshot_schema.sql

psql -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V4__add_financial_record_app_id_constraints.sql
```

If `psql` is not recognized on Windows, use the full path:

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V1__create_financials_schema.sql

& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V2__create_financial_snapshot_document.sql

& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V3__create_financial_record_snapshot_schema.sql

& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V4__add_financial_record_app_id_constraints.sql
```

When prompted for the password, use:

```text
financial_app_password
```

Verify the schema:

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U financial_app_user -d financial_app
```

Inside `psql`:

```sql
\dt
SELECT count(*) FROM financial_snapshot_document;
SELECT count(*) FROM financial_record_snapshot;
\q
```

Expected tables:

```text
annual_withdrawal
asset_account
debt_account
financial_snapshot
financial_snapshot_document
financial_record_annual_withdrawal
financial_record_asset_account
financial_record_debt_account
financial_record_important_date
financial_record_income_event
financial_record_income_summary_item
financial_record_monthly_bill
financial_record_snapshot
important_date
income_event
income_summary_item
monthly_withdrawal
```

A `financial_snapshot_document` count of `0` is normal on a fresh database.

To inspect the current storage split without exposing snapshot contents, run:

```sql
SELECT 'annual_withdrawal' AS table_name, count(*) AS row_count FROM annual_withdrawal
UNION ALL SELECT 'asset_account', count(*) FROM asset_account
UNION ALL SELECT 'debt_account', count(*) FROM debt_account
UNION ALL SELECT 'financial_snapshot', count(*) FROM financial_snapshot
UNION ALL SELECT 'financial_snapshot_document', count(*) FROM financial_snapshot_document
UNION ALL SELECT 'financial_record_annual_withdrawal', count(*) FROM financial_record_annual_withdrawal
UNION ALL SELECT 'financial_record_asset_account', count(*) FROM financial_record_asset_account
UNION ALL SELECT 'financial_record_debt_account', count(*) FROM financial_record_debt_account
UNION ALL SELECT 'financial_record_important_date', count(*) FROM financial_record_important_date
UNION ALL SELECT 'financial_record_income_event', count(*) FROM financial_record_income_event
UNION ALL SELECT 'financial_record_income_summary_item', count(*) FROM financial_record_income_summary_item
UNION ALL SELECT 'financial_record_monthly_bill', count(*) FROM financial_record_monthly_bill
UNION ALL SELECT 'financial_record_snapshot', count(*) FROM financial_record_snapshot
UNION ALL SELECT 'important_date', count(*) FROM important_date
UNION ALL SELECT 'income_event', count(*) FROM income_event
UNION ALL SELECT 'income_summary_item', count(*) FROM income_summary_item
UNION ALL SELECT 'monthly_withdrawal', count(*) FROM monthly_withdrawal
ORDER BY table_name;
```

Expected for the current JSONB-backed runtime implementation: the inactive V1
tables and inactive V3/V4 `financial_record_*` tables can have `0` rows while
`financial_snapshot_document` has `0` rows before first startup or `1` active
row after the backend seeds/saves a snapshot.

---

## Financial data storage

Financial data is stored in a local JSON file by default:

```text
backend/data/financials.local.json
```

That file is ignored by Git and should contain real local financial data only on
the developer's machine.

The committed safe template lives at:

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

When the `postgres` profile is active, the backend stores the full financial
snapshot in PostgreSQL as a `jsonb` document:

```text
financial_snapshot_document.snapshot_json
```

This intentionally keeps the frontend API contract unchanged. The browser still
loads one snapshot, edits a local draft, and saves one snapshot back to the API.

The active repository implementation reads and writes only
`financial_snapshot_document`. The normalized V1 tables
(`financial_snapshot`, `monthly_withdrawal`, `annual_withdrawal`,
`asset_account`, `debt_account`, `income_summary_item`, `income_event`, and
`important_date`) are inactive historical groundwork. They are not the planned
runtime relational adapter path as-is and may remain empty in a healthy local
database.

V3 adds a clean relational path through the `financial_record_*` table family
and `PostgresFinancialRecordSnapshotAdapter`. V4 adds per-snapshot
`app_record_id` uniqueness indexes for granular updates and deletes. That
adapter can save/load the backend `FinancialSnapshot` domain aggregate in
relational form and perform record-level CRUD operations. It is covered by the
opt-in PostgreSQL integration test. It is not wired into the active runtime
service yet, so `financial_record_*` tables may also remain empty in a healthy
database until the service is intentionally wired to the relational adapter.

The local `financial_app_user` account is intentionally write-capable because
it is the backend application user. For read-only inspection, use `SELECT`
queries or create a separate PostgreSQL role with only `CONNECT`, `USAGE`, and
`SELECT` privileges.

When PostgreSQL is empty, the backend seeds the first active snapshot from:

```text
backend/data/financials.local.json
```

If no local file is available, it falls back to:

```text
backend/data/financials.example.json
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

### Save financial snapshot

```http
PUT /api/v1/financials
```

Persists the full edited financial snapshot in one request. This is the main
endpoint used by the frontend draft/save workflow. The request must include the
current `version` returned by `GET /api/v1/financials`; stale versions return
`409 Conflict`.

### Export saved snapshot backup

```http
GET /api/v1/financials/export
GET /api/v1/financials/export/csv
GET /api/v1/financials/export/xlsx
```

Downloads the saved source snapshot with `Cache-Control: no-store`. The JSON
response includes `format`, `exportedAt`, and a `snapshot` object that mirrors
the full-snapshot save request shape. CSV and XLSX downloads use the same
fixed-column tabular format for records and can be restored through the import
endpoints.

```http
POST /api/v1/financials/import/csv
POST /api/v1/financials/import/xlsx
```

Imports replace the complete snapshot and use the imported `version` as the
same optimistic concurrency token required by `PUT /api/v1/financials`. A stale
file returns `409 Conflict`; a successful import increments the version and
returns the calculated snapshot response. Treat JSON, CSV, and XLSX files as
personal financial data.

Local operators can use the repository scripts without printing financial
contents:

```powershell
.\scripts\export-financial-snapshot.ps1 -Format xlsx -OutputPath "$HOME\Downloads\financial-snapshot.xlsx"
.\scripts\import-financial-snapshot.ps1 -InputPath "$HOME\Downloads\financial-snapshot.xlsx" -ConfirmRestore
```

### Granular record endpoints

```http
POST /api/v1/financials/bills
PUT /api/v1/financials/bills/{id}
DELETE /api/v1/financials/bills/{id}
POST /api/v1/financials/annual-withdrawals
PUT /api/v1/financials/annual-withdrawals/{id}
DELETE /api/v1/financials/annual-withdrawals/{id}
POST /api/v1/financials/asset-accounts
PUT /api/v1/financials/asset-accounts/{id}
DELETE /api/v1/financials/asset-accounts/{id}
POST /api/v1/financials/debt-accounts
PUT /api/v1/financials/debt-accounts/{id}
DELETE /api/v1/financials/debt-accounts/{id}
POST /api/v1/financials/income-summary-items
PUT /api/v1/financials/income-summary-items/{id}
DELETE /api/v1/financials/income-summary-items/{id}
POST /api/v1/financials/income-events
PUT /api/v1/financials/income-events/{id}
DELETE /api/v1/financials/income-events/{id}
POST /api/v1/financials/important-dates
PUT /api/v1/financials/important-dates/{id}
DELETE /api/v1/financials/important-dates/{id}
PUT /api/v1/financials/pay-period
```

These endpoints remain available for direct record and pay period updates, even
though the current UI saves the full snapshot as the source of truth.

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

## Project structure

```text
backend/
|-- data/
|   |-- financials.example.json
|   `-- financials.local.json        # ignored by Git
|-- src/main/java/
|   |-- com/example/backend/api/
|   |-- com/example/backend/domain/
|   |   `-- financials/
|   |-- com/example/backend/dto/
|   |   `-- financials/
|   |-- com/example/backend/repository/
|   |-- com/example/backend/service/
|   `-- com/example/backend/BackendApplication.java
|-- src/main/resources/
|   `-- db/migration/
|       |-- V1__create_financials_schema.sql
|       |-- V2__create_financial_snapshot_document.sql
|       |-- V3__create_financial_record_snapshot_schema.sql
|       `-- V4__add_financial_record_app_id_constraints.sql
|-- src/test/java/
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

### `domain/financials/`

Backend financial domain records.

Responsibilities:

- saved financial snapshot aggregate
- financial record types shared by service and repository code
- package boundary between API DTOs and storage adapters

### `repository/`

Persistence-facing data models and storage adapters.

Responsibilities:

- loading local JSON data
- writing local JSON data
- loading PostgreSQL-backed snapshot data
- writing PostgreSQL-backed snapshot data
- saving/loading and granular CRUD in the V3/V4 relational record adapter path
- assigning IDs for new local rows
- keeping persistence concerns out of controllers
- translating the storage envelope to and from the backend domain aggregate

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
asset accounts, debt accounts, income summary source items, income calendar
events, and important dates. Derived income summary rows are calculated by the
frontend.

The default mode stores this aggregate in a local JSON file. The `postgres`
profile stores the same aggregate as a PostgreSQL `jsonb` document. A tested
V3/V4 relational adapter supports internal granular CRUD, but the runtime
service is not wired to it yet.

---

## Development notes

### Spring Boot DevTools

DevTools are isolated to the `dev` Maven profile to avoid accidental inclusion
in production builds.

Benefits:

- automatic restart
- improved local iteration speed
- enhanced development workflow

Run with DevTools enabled:

```powershell
.\mvnw.cmd -Pdev spring-boot:run
```

### Spotless

Spotless is used to enforce consistent Java formatting.

Format Java source:

```powershell
.\mvnw.cmd spotless:apply
```

Verify formatting:

```powershell
.\mvnw.cmd spotless:check
```

### SortPom

SortPom is used to keep `pom.xml` consistently organized and formatted.

Format `pom.xml`:

```powershell
.\mvnw.cmd sortpom:sort
```

Verify `pom.xml` formatting:

```powershell
.\mvnw.cmd sortpom:verify
```

---

## Build the backend

```powershell
.\mvnw.cmd clean verify
```

## PostgreSQL profile smoke test

After local PostgreSQL setup, this command verifies that the Spring Boot test
context can start with the `postgres` profile and connect to
`financial_app`:

```powershell
$env:SPRING_PROFILES_ACTIVE="postgres"
.\mvnw.cmd -B test
```

---

## PostgreSQL integration test

The PostgreSQL snapshot store and V3/V4 relational record adapter have opt-in
integration tests so normal builds do not require a local database. The tests
create and drop isolated schemas named `financial_snapshot_store_test` and
`financial_record_snapshot_adapter_test` inside the configured database.

PowerShell:

```powershell
$env:RUN_POSTGRES_INTEGRATION_TESTS="true"
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/financial_app"
$env:DATABASE_USERNAME="financial_app_user"
$env:DATABASE_PASSWORD="financial_app_password"

.\mvnw.cmd test "-Dtest=PostgresFinancialsSnapshotStoreIT,PostgresFinancialRecordSnapshotAdapterIT" "-Djacoco.skip=true"
```

The `jacoco.skip` flag keeps this local database smoke test focused and avoids
Java agent noise when running on newer local JDKs.

---

## Troubleshooting

### Backend fails with `password authentication failed for user "financial_app_user"`

The backend reached PostgreSQL, but the password for `financial_app_user` does
not match `DATABASE_PASSWORD`.

Fix in pgAdmin as the `postgres` admin user:

```sql
ALTER USER financial_app_user WITH PASSWORD 'financial_app_password';
```

Then restart the backend.

---

### Backend fails with `database "financial_app" does not exist`

The PostgreSQL server is running, but the app database has not been created.

Preferred fix:

```powershell
.\scripts\setup-local-postgres.ps1
```

Manual fix in pgAdmin as the `postgres` admin user:

```sql
CREATE DATABASE financial_app OWNER financial_app_user;
```

---

### Backend fails with `relation "financial_snapshot_document" does not exist`

The backend connected to PostgreSQL, but the schema migrations have not been
applied.

Preferred fix from the repository root:

```powershell
.\scripts\setup-local-postgres.ps1
```

Manual fallback:

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V1__create_financials_schema.sql

& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V2__create_financial_snapshot_document.sql

& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V3__create_financial_record_snapshot_schema.sql

& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V4__add_financial_record_app_id_constraints.sql
```

---

### `psql is not recognized`

PostgreSQL is installed, but the PostgreSQL `bin` directory is not on the PATH.

Use the full executable path:

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" --version
```

Optional long-term fix: add this folder to PATH:

```text
C:\Program Files\PostgreSQL\18\bin
```

---

### `financial_snapshot_document` exists but has `0` rows

The schema exists, but no active financial snapshot has been stored in
PostgreSQL yet.

Check:

```sql
SELECT count(*) FROM financial_snapshot_document;
```

A count of `0` is expected before the first PostgreSQL-backed backend startup.
After the backend starts with `SPRING_PROFILES_ACTIVE=postgres`, it should seed
one active row from `backend/data/financials.local.json` or fall back to
`backend/data/financials.example.json`.

If data from another machine is required, explicitly migrate it by copying the
ignored local JSON file or exporting/importing the old PostgreSQL data.

---

## CI / Security tooling

The backend participates in repository CI pipelines for:

- Maven builds
- dependency scanning
- Snyk security analysis
- formatting verification
- JaCoCo coverage

---

## Notes

Intentional simplifications:

- JSON remains the default local fallback
- PostgreSQL stores the active runtime snapshot as `jsonb`; granular
  database-backed CRUD is implemented in the V3/V4 relational adapter path, but
  the runtime service is not wired to it yet
- single local Basic-auth application user; no multi-user identity or tenant
  ownership model yet
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
