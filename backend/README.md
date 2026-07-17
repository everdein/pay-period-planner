# Pay Period Planner API

Spring Boot API for the Pay Period Planner household cash-flow workspace. It
owns account sessions, workspace authorization, the versioned financial
snapshot contract, planning calculations, PostgreSQL persistence, and safe
operational boundaries.

The backend demonstrates:

- thin HTTP controllers and explicit request/response mapping
- framework-neutral workspace query, command, normalization, and calculation
  services
- PostgreSQL-backed account, membership, session, snapshot, and audit data
- optimistic concurrency around the complete financial workspace aggregate
- additive Flyway migrations, including explicit retirement of obsolete storage
- focused unit, controller, security, and isolated PostgreSQL integration tests

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
- PostgreSQL 18 with the local application database and role

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

The backend always uses PostgreSQL relational workspace persistence. Run the
frontend separately after the backend is healthy.

A running backend should keep its terminal open. If the command returns to the
PowerShell prompt, the backend stopped during startup; check the last Spring Boot
log lines before `BUILD SUCCESS` or `BUILD FAILURE`.

Local operator credential defaults:

```text
Username: financial_app
Password: financial_app_local_password
```

Override these with `FINANCIALS_API_USERNAME` and `FINANCIALS_API_PASSWORD`
before starting the backend. They protect metrics routes. Financial workspace
routes use account sessions, and the retired `/api/v1/admin/**` namespace is
denied.

Runtime guardrails:

- `/actuator/health` and `/actuator/info` are public; `/actuator/metrics`
  requires the financial API credentials, and other Actuator endpoints are
  denied
- API calls and responses carry a safe `X-Request-ID`; completion logs contain
  operational metadata only, never financial values or request bodies
- backend error responses do not include stack traces or binding internals
- request bodies above `FINANCIALS_MAX_REQUEST_BYTES` are rejected with `413`
  before controller handling; default is `1048576`
- cross-origin browser calls are denied unless `FINANCIALS_ALLOWED_ORIGINS`
  names exact allowed origins
- activating `prod` requires non-default operator credentials, no wildcard
  CORS origin, and
  `FINANCIALS_SESSION_COOKIE_SECURE=true`

The backend exposes account and session endpoints under `/api/v1/auth`.
`FINANCIALS_SESSION_DURATION` controls the session lifetime and
defaults to `7d`; `FINANCIALS_SESSION_COOKIE_SECURE` defaults to `false` for
local HTTP and must be `true` with the `prod` profile. These sessions authorize
financial routes through database-derived workspace memberships.

See `../docs/observability-guide.md` for request correlation, protected metric
inspection, production JSON logs, frontend error containment, and data-safety
rules.

---

## PostgreSQL-backed startup

The backend uses the dedicated database user:

```text
financial_app_user
```

Important: `financial_app_user` is a PostgreSQL database user. It is not a
frontend application login.

Connection settings:

```properties
DATABASE_URL=jdbc:postgresql://localhost:5432/financial_app
DATABASE_USERNAME=financial_app_user
DATABASE_PASSWORD=financial_app_password
```

From the repository root, prepare PostgreSQL with:

```powershell
cd C:\Users\<you>\dev\pay-period-planner
.\scripts\setup-local-postgres.ps1
```

Then start the backend from the repository root with:

```powershell
.\scripts\start-backend.ps1
```

The script starts Spring Boot with the dedicated database user
`financial_app_user`; it does not run the application as the PostgreSQL admin
user.

Or run manually from the `backend` directory:

```powershell
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/financial_app"
$env:DATABASE_USERNAME="financial_app_user"
$env:DATABASE_PASSWORD="financial_app_password"

.\mvnw.cmd spring-boot:run
```

To include the Maven `dev` profile and Spring Boot DevTools:

```powershell
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

PostgreSQL is the only runtime persistence path. The application requires a
reachable migrated database before startup completes.

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

It delegates the ordered migration files under this directory to Flyway:

```text
backend/src/main/resources/db/migration/V1__create_financials_schema.sql
backend/src/main/resources/db/migration/V2__create_financial_snapshot_document.sql
backend/src/main/resources/db/migration/V3__create_financial_record_snapshot_schema.sql
backend/src/main/resources/db/migration/V4__add_financial_record_app_id_constraints.sql
backend/src/main/resources/db/migration/V5__create_identity_workspace_session_schema.sql
backend/src/main/resources/db/migration/V6__scope_financial_record_snapshots_to_workspace.sql
backend/src/main/resources/db/migration/V7__add_workspace_snapshot_migration_history.sql
backend/src/main/resources/db/migration/V8__add_financial_projection_roles.sql
backend/src/main/resources/db/migration/V9__add_financial_planning_settings.sql
backend/src/main/resources/db/migration/V10__retire_legacy_snapshot_migration.sql
backend/src/main/resources/db/migration/V11__require_workspace_owned_financial_snapshots.sql
backend/src/main/resources/db/migration/V12__retire_inactive_v1_financial_schema.sql
```

The script is idempotent on a Flyway-managed database. If the database already
exists, it skips database creation. Flyway validates migration history, applies
only pending migrations, and the script verifies `flyway_schema_history` plus
the snapshot tables.

After setup completes, start the backend from the repository root:

```powershell
.\scripts\start-backend.ps1
```

Manual pgAdmin role/database steps below are kept for troubleshooting or
learning purposes. Versioned migration files must still be executed by Flyway.

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
should skip database creation and report that the Flyway schema is up to date.

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

## Database migrations

The initial schema is managed in:

```text
src/main/resources/db/migration/V1__create_financials_schema.sql
src/main/resources/db/migration/V2__create_financial_snapshot_document.sql
src/main/resources/db/migration/V3__create_financial_record_snapshot_schema.sql
src/main/resources/db/migration/V4__add_financial_record_app_id_constraints.sql
src/main/resources/db/migration/V5__create_identity_workspace_session_schema.sql
src/main/resources/db/migration/V6__scope_financial_record_snapshots_to_workspace.sql
src/main/resources/db/migration/V7__add_workspace_snapshot_migration_history.sql
src/main/resources/db/migration/V8__add_financial_projection_roles.sql
src/main/resources/db/migration/V9__add_financial_planning_settings.sql
src/main/resources/db/migration/V10__retire_legacy_snapshot_migration.sql
src/main/resources/db/migration/V11__require_workspace_owned_financial_snapshots.sql
src/main/resources/db/migration/V12__retire_inactive_v1_financial_schema.sql
```

The full setup path creates the role/database and invokes Flyway:

```powershell
.\scripts\setup-local-postgres.ps1
```

When the role and database already exist, run Flyway directly from the
repository root:

```powershell
.\scripts\migrate-postgres.ps1
```

The runner invokes `flyway:migrate` and `flyway:validate` with the same
migration directory used at application startup. Do not execute these files
directly with `psql -f`. Explicit `-DatabaseUrl`, `-DatabaseUsername`, and
`-DatabasePassword` parameters take precedence over matching `DATABASE_*`
variables and the documented local defaults.

A non-empty schema without `flyway_schema_history` fails closed. Legacy
baseline switches are retired; back up and inspect such a database, then plan
an explicit additive recovery on a copy or replace it when the database is
disposable. Never fabricate Flyway history.

Verify the schema:

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U financial_app_user -d financial_app
```

Exit `psql` with `\q`, then use the metadata-only inspector from the repository
root:

```powershell
.\scripts\inspect-postgres.ps1
```

Expected for the relational PostgreSQL runtime: `financial_record_*` rows exist
only after workspace initialization, and V5 identity/session tables remain
empty until the account API is used. V10 removes the V2 JSONB document table,
V7 migration ledger, and source linkage; V11 removes unowned compatibility
rows and requires relational workspace ownership; V12 removes the inactive V1
tables. Creating an account does not silently assign or seed financial data.

---

## Financial data storage

The backend stores financial snapshots in workspace-scoped PostgreSQL
relational tables:

```text
financial_record_snapshot and financial_record_*
```

The ignored `backend/data/financials.local.json` pattern is an obsolete local
artifact, not a supported input. Startup never reads, creates, or updates it.
The committed synthetic example remains available for tests and demos:

```text
backend/data/financials.example.json
```

This intentionally keeps the frontend API contract unchanged. The browser still
loads one snapshot, edits a local draft, and saves one snapshot back to the API.

The active PostgreSQL repository reads and writes only workspace-owned
`financial_record_*` tables. V12 removes the normalized V1 tables after they
never became a runtime path; their definitions remain in immutable migration
history.

V3 adds a clean relational path through the `financial_record_*` table family
and `PostgresFinancialRecordSnapshotAdapter`. V4 adds per-snapshot
`app_record_id` uniqueness indexes that preserve stable record identity within
each snapshot. ADR 0016 retired the adapter's record-level CRUD methods while
retaining the additive constraints and relational aggregate storage. V6 links
relational snapshots to a workspace, permits one active snapshot per workspace,
and makes every adapter operation require a workspace ID. The runtime store
obtains that ID through the framework-neutral `CurrentWorkspace` port. The
`AuthenticatedRequestWorkspace` HTTP adapter resolves the selected authenticated
membership; persistence and application services do not receive servlet
requests. V8 adds snapshot-versioned projection roles for the rent bill,
rent-reserve asset account, and primary-paycheck income-summary item. The
adapter persists those IDs with the aggregate, and request normalization
validates that each role references the expected record type. V9 adds the pay
cadence and validated IANA planning time zone to each versioned snapshot. The
backend derives one `currentDate` in that zone and uses it for the active pay
period; historical input without settings defaults to `BIWEEKLY` and `UTC`.
The store loads
current state separately from SQL-limited audit
history and performs optimistic replacement writes under a workspace-row lock.
Replacement writes batch each relational record family and append exactly one
new audit event. Relational and HTTP integration tests cover cross-workspace
isolation and audit continuity.

The local `financial_app_user` account is intentionally write-capable because
it is the backend application user. For read-only inspection, use `SELECT`
queries or create a separate PostgreSQL role with only `CONNECT`, `USAGE`, and
`SELECT` privileges.

An empty PostgreSQL workspace is not seeded implicitly. Initialize it through
the application, or deliberately restore a supported application export. A
financial request returns `404` until a relational snapshot exists.

---

## Available endpoints

The current API is organized around a versioned financial snapshot aggregate.
The main read and save endpoints operate on the full financial workspace.

### Account and session foundation

These endpoints are part of the required runtime:

```http
GET  /api/v1/auth/csrf
POST /api/v1/auth/signup
POST /api/v1/auth/signin
GET  /api/v1/auth/session
POST /api/v1/auth/signout
```

Signup creates an application user, a default `Personal` workspace, an owner
membership, and a server-managed session in one transaction. Sign-in verifies
the adaptive password hash. The raw opaque session token is returned only in
an `HttpOnly`, `SameSite=Strict` cookie; PostgreSQL stores its SHA-256 hash.
Session recovery returns account metadata and current workspace memberships,
and sign-out revokes the server-side session before expiring the cookie.
Clients first bootstrap CSRF protection with `GET /api/v1/auth/csrf` and send
its token in `X-XSRF-TOKEN` for every account or financial write.

The account session has `WORKSPACE` authority for `/api/v1/financials/**`. A
sole membership is selected automatically; multi-workspace accounts send
`X-Workspace-ID`. The frontend signs up, signs in, restores the session, and
selects its workspace through this flow.

### Initialize an empty financial snapshot

```http
POST /api/v1/financials
```

Accepts `startDate` and `endDate`, creates a version-1 relational snapshot with
zero-value projection input records and typed role references for the
authenticated workspace, and returns `201 Created` with the calculated
response. It does not import financial values. Duplicate or concurrent
initialization returns `409 Conflict`. It never imports example, personal JSON,
or retired JSONB data. The initializer returns the created aggregate for response
presentation; the controller does not reload it after the write.

### Get financial snapshot

```http
GET /api/v1/financials
```

Returns the current pay period, withdrawals, income summaries, income calendar
events, asset category totals, debt balances, important dates, and calculated
overview totals.

### Get audit history

```http
GET /api/v1/financials/history?limit=50
```

Returns recent saved-change audit events newest first. Each event includes the
action, coarse resource type/ID, version movement, timestamp, and aggregate
projection summary after the committed write. Audit history is personal
financial data; it intentionally does not include request bodies or field-level
before/after diffs. PostgreSQL applies the requested limit without loading the
current snapshot aggregate.

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
```

Downloads the saved source snapshot with `Cache-Control: no-store`. The JSON
response includes `format`, `exportedAt`, and a `snapshot` object that mirrors
the full-snapshot save request shape. Source-shaped backups do not include
complete relational audit history.

```http
POST /api/v1/financials/restore?expectedVersion=<current-version>
```

Restore accepts the exported JSON envelope unchanged. Its embedded snapshot
version describes the backup source; `expectedVersion` must match the current
target workspace. A concurrent target write returns `409 Conflict`; a
successful restore increments the target version and returns the calculated
snapshot response. Treat JSON backup files as personal financial data.

Local operators can use the repository scripts without printing financial
contents. Set account credentials first; the scripts establish and revoke a
workspace session for each operation. Use `-WorkspaceId` when the account has
more than one membership:

```powershell
$env:FINANCIALS_ACCOUNT_EMAIL="owner@example.com"
$env:FINANCIALS_ACCOUNT_PASSWORD="<account-password>"

.\scripts\export-financial-snapshot.ps1 -OutputPath "$HOME\Downloads\financial-snapshot.json"
.\scripts\restore-financial-snapshot.ps1 -InputPath "$HOME\Downloads\financial-snapshot.json" -ConfirmRestore
```

Record and pay-period edits are persisted only through the version-checked
`PUT /api/v1/financials` aggregate boundary. The former granular routes were
retired by ADR 0016.

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
|   `-- financials.example.json      # synthetic test/demo fixture
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
|       |-- V4__add_financial_record_app_id_constraints.sql
|       |-- V5__create_identity_workspace_session_schema.sql
|       |-- V6__scope_financial_record_snapshots_to_workspace.sql
|       |-- V7__add_workspace_snapshot_migration_history.sql
|       |-- V8__add_financial_projection_roles.sql
|       |-- V9__add_financial_planning_settings.sql
|       |-- V10__retire_legacy_snapshot_migration.sql
|       |-- V11__require_workspace_owned_financial_snapshots.sql
|       `-- V12__retire_inactive_v1_financial_schema.sql
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
- status code handling
- API boundary definition
- delegation to explicit financial workspace query and command boundaries

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

- loading and writing PostgreSQL-backed snapshot data
- workspace-scoped aggregate saving/loading in the relational record adapter
  path
- assigning IDs for new relational rows
- keeping persistence concerns out of controllers
- translating the storage envelope to and from the backend domain aggregate

### `service/`

Business/service layer.

Responsibilities:

- current-workspace query and versioned-command orchestration
- API request validation and domain conversion
- required-record normalization
- pay-period and financial calculations
- API response mapping from calculated domain state
- shared presentation of supplied domain snapshots for current reads and
  initialization responses
- orchestration between API and repository layers through focused interfaces
- framework-neutral current-workspace access and application exceptions; HTTP
  status mapping remains in `ApiExceptionHandler`

---

## Financials domain behavior

`FinancialSnapshotCalculator` calculates:

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
calculator advances or rewinds the stored pay period window to include the current
date while preserving the original period length.

Withdrawal due dates are derived from each row's due day and the active pay
period month. Days beyond the end of a month are clamped to the last valid day
of that month.

Annual withdrawals are stored as recurring month/day values. The calculator
projects them into the active pay period year so they can be displayed as
`MM/DD/YYYY` and included in pay period planning.

The persisted snapshot currently includes monthly bills, annual withdrawals,
asset accounts, debt accounts, income summary source items, income calendar
events, and important dates. Derived income summary rows are calculated by the
frontend.

The runtime stores this aggregate in workspace-owned relational tables and
authorizes access from account-session memberships.

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

## Required PostgreSQL integration tests

After local PostgreSQL setup, this command runs the required integration tests
against `financial_app`:

```powershell
$env:RUN_POSTGRES_INTEGRATION_TESTS="true"
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/financial_app"
$env:DATABASE_USERNAME="financial_app_user"
$env:DATABASE_PASSWORD="financial_app_password"

.\mvnw.cmd -B test "--activate-profiles=postgres-integration" "-Djacoco.skip=true"
```

---

## PostgreSQL integration test

The PostgreSQL snapshot store, workspace-scoped relational record adapter, V5
identity schema, V10-V12 retirement boundary, and account/session flows
have a required repository integration gate. Focused Maven unit builds remain
database-independent, while
`.\scripts\verify-local.ps1` and hosted CI run the integration profile. The tests
create and drop isolated schemas named `financial_snapshot_store_test`,
`financial_record_snapshot_adapter_test`, `identity_schema_test`, and
`workspace_ownership_schema_test`, plus isolated account/session and workspace
runtime schemas, inside the configured database.

PowerShell:

```powershell
$env:RUN_POSTGRES_INTEGRATION_TESTS="true"
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/financial_app"
$env:DATABASE_USERNAME="financial_app_user"
$env:DATABASE_PASSWORD="financial_app_password"

.\mvnw.cmd -B test "--activate-profiles=postgres-integration" "-Djacoco.skip=true"
```

The `postgres-integration` profile discovers every `*IT` class. The
`jacoco.skip` flag keeps this database integration run focused and avoids Java
agent noise when running on newer local JDKs.

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

### Backend fails with a missing application relation

The backend connected to PostgreSQL, but the schema migrations have not been
applied.

Preferred fix from the repository root:

```powershell
.\scripts\setup-local-postgres.ps1
```

Run the Flyway migration command directly if the role and database already
exist:

```powershell
.\scripts\migrate-postgres.ps1
```

Do not repair this error by executing versioned SQL files with `psql -f`.

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

- PostgreSQL relational tables are the only runtime persistence path; V10-V12
  retire V2 JSONB, the V7 transition ledger, unowned compatibility rows, and
  the inactive V1 table family
- financial routes require account sessions and workspace membership; operator
  Basic auth is limited to protected metrics
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
