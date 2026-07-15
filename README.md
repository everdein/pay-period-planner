# End-to-End Reference Application

![CI](https://github.com/everdein/end-to-end-app/actions/workflows/ci.yml/badge.svg)

Modern full-stack reference application demonstrating a React + TypeScript
frontend communicating with a Spring Boot REST API.

This repository is intentionally designed as a **learning, experimentation, and
architecture reference project** focused on modern engineering workflows,
tooling, and developer experience.

The goal is not production complexity. The goal is to establish clean
engineering patterns and workflows that can scale over time.

---

## Tech stack

### Frontend

- React 19
- TypeScript
- Vite
- Redux Toolkit
- Vitest
- ESLint
- Prettier

### Backend

- Spring Boot 4
- Java 21
- Maven
- Spring JDBC
- PostgreSQL foundation with migration SQL

### Tooling / Quality

- Husky
- lint-staged
- cspell
- Snyk
- GitHub Actions
- Vitest coverage
- JaCoCo coverage
- Spotless
- SortPom

---

## Project structure

```text
end-to-end-app/
|-- .agents/              # Repository-scoped AI workflow skills
|-- backend/              # Spring Boot API
|   |-- data/             # Example and local financial snapshot data
|   `-- README.md         # Backend-specific setup and architecture notes
|-- frontend/             # React + TypeScript frontend
|-- docs/                 # Architecture docs and ADRs
|-- scripts/              # Local development scripts
|-- .github/workflows/    # CI pipelines
|-- .husky/               # Git hooks
|-- AGENTS.md             # Coding-agent architecture and safety guidance
`-- README.md
```

---

## Requirements

Install the following for local development:

- Git
- Java 21+
- Node.js 24+
- npm 10+
- PostgreSQL 18+
- pgAdmin or `psql` for local PostgreSQL setup
- Insomnia or another API client for manual API testing

For backend commands, make sure `JAVA_HOME` points to a Java 21+ JDK. Maven uses
`JAVA_HOME`, even when `java -version` on the PATH reports a different version.

Verify tools are available:

```powershell
java -version
javac -version
node --version
npm --version
git --version
cd backend
.\mvnw.cmd -v
```

Expected versions:

```text
Java: 21+
Node: 24+
npm: 10+
Maven: available through backend/mvnw.cmd
```

If `npm` is blocked in Windows PowerShell with a script execution policy error,
run:

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

Then close and reopen PowerShell.

---

## Install dependencies

The repeatable Windows bootstrap checks required tools and installs both root
and frontend dependencies:

```powershell
.\scripts\bootstrap-local.ps1
```

Add `-IncludePostgres` to run the interactive local database setup after
installing dependencies.

Manual installation remains available:

```powershell
npm ci
```

Install frontend dependencies:

```powershell
npm --prefix frontend ci
```

The frontend install is lockfile-defined and does not modify dependency source
files. Verify its temporary ESLint compatibility override with:

```powershell
npm --prefix frontend run check:dependency-compat
```

---

## PostgreSQL persistence

PostgreSQL relational workspaces are the only runtime persistence path.

The backend stores the active financial snapshot as relational PostgreSQL rows
in:

```text
financial_record_snapshot and financial_record_*
```

The application requires:

- local PostgreSQL running on `localhost:5432`
- database: `financial_app`
- database user: `financial_app_user`
- password: `financial_app_password`
- schema migrations applied from `backend/src/main/resources/db/migration`

Important: `financial_app_user` is a database user used by the backend. It is not
a frontend login user.

The backend reads and writes the
V3/V4/V6/V7/V8/V9 `financial_record_*` relational model. V5 account sessions select
a current workspace membership, writes use optimistic versions under a
workspace lock, record families are written in batches, and V7 preserves
relational audit history through a separately limited query. The V2 JSONB table
is retained only as an explicit migration/rollback source. The normalized V1
tables remain inactive historical groundwork. The frontend session conversion
is complete: the browser now uses account sessions, fresh CSRF proof for writes,
and workspace selection against this PostgreSQL financial API.

---

## Running the application

A running backend should keep its terminal open. If the command
returns to the PowerShell prompt, the backend stopped during startup; check the
last Spring Boot log lines before `BUILD SUCCESS` or `BUILD FAILURE`.

Local operator credentials:

```text
Username: financial_app
Password: financial_app_local_password
```

Override these with `FINANCIALS_API_USERNAME` and `FINANCIALS_API_PASSWORD`
before starting the backend. They protect migration-admin and metrics routes;
financial workspace routes use account sessions.

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
- activating `prod` requires non-default operator credentials, secure session
  cookies, and no wildcard CORS origin

See `docs/observability-guide.md` for request correlation, protected metric
inspection, production JSON logs, frontend error containment, and data-safety
rules.

---

## Run the application

The preferred setup path is the repository setup script:

```powershell
cd C:\Users\<you>\dev\end-to-end-app
.\scripts\setup-local-postgres.ps1
```

The script will:

- find `psql.exe`
- ask for the local PostgreSQL admin password for user `postgres`
- create or update `financial_app_user`
- create the `financial_app` database if it does not exist
- assign database ownership and privileges
- invoke Flyway as the only versioned migration executor
- validate `flyway_schema_history` and verify the snapshot tables

The script is safe to rerun on a Flyway-managed database. Flyway validates the
history and applies only pending migrations.

After setup completes, start the backend from the repository root:

```powershell
cd C:\Users\<you>\dev\end-to-end-app
.\scripts\start-backend.ps1
```

The script starts Spring Boot with the dedicated database user
`financial_app_user`; it does not run the application as the PostgreSQL admin
user.

Backend URL:

```text
http://localhost:8080
```

Then start the frontend in a second terminal:

```powershell
cd frontend
npm run dev
```

Frontend URL:

```text
http://localhost:3000
```

Manual role/database commands are available below for troubleshooting. Do not
execute versioned migration files directly with `psql`.

---

## Manual PostgreSQL setup fallback

Use this section if the setup script fails or if you want to perform the setup
manually for learning/debugging.

### 1. Create the local PostgreSQL database and user

Using pgAdmin, connect to the local PostgreSQL server as the admin user:

```text
Host: localhost
Port: 5432
Database: postgres
User: postgres
```

Do not commit or document the local admin password.

Run:

```sql
CREATE USER financial_app_user WITH PASSWORD 'financial_app_password';
CREATE DATABASE financial_app OWNER financial_app_user;
GRANT ALL PRIVILEGES ON DATABASE financial_app TO financial_app_user;
```

If the user already exists but the password is wrong:

```sql
ALTER USER financial_app_user WITH PASSWORD 'financial_app_password';
```

### 2. Run database migrations

Run the Flyway-owned migration command from the repository root:

```powershell
.\scripts\migrate-postgres.ps1
```

Explicit `-DatabaseUrl`, `-DatabaseUsername`, and `-DatabasePassword`
parameters take precedence; otherwise the command reads the matching
`DATABASE_*` variables and falls back to the documented local defaults. It
runs `flyway:migrate` followed by `flyway:validate`. A non-empty schema without
Flyway history is not a normal migration target; inspect and back it up before
using one of the legacy adoption modes documented in
`docs/database-storage-guide.md`.

Verify tables:

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U financial_app_user -d financial_app
```

Inside `psql`:

```sql
\dt
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
SELECT count(*) FROM financial_snapshot_document;
SELECT count(*) FROM financial_record_snapshot;
\q
```

Expected tables include:

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
financial_record_projection_role
financial_record_snapshot
important_date
income_event
income_summary_item
monthly_withdrawal
```

A count of `0` in `financial_snapshot_document` is acceptable on a fresh
database because that table is now a legacy migration source.

The PostgreSQL runtime uses V3/V4/V6/V7/V8/V9 `financial_record_*` tables. They
remain empty until a snapshot is explicitly migrated or created for a
workspace. V8 stores projection-input role IDs with each snapshot. V1 tables
are inactive historical groundwork. V5 identity, workspace, membership, and
session tables remain empty until account flows are used.

---

## Testing the PostgreSQL setup script

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

## Request flow

### Local development flow

```text
Browser                Vite Dev Server                 Spring Boot
  |                          |                             |
  |  GET http://localhost:3000/                            |
  |------------------------->|                             |
  |    (serves React app)    |                             |
  |<-------------------------|                             |
  |                          |                             |
  |  GET /api/v1/financials                               |
  |------------------------->|                             |
  |        (proxy)           |  GET http://localhost:8080/api/v1/financials
  |                          |---------------------------->|
  |                          |        JSON response        |
  |                          |<----------------------------|
  |        JSON response     |                             |
  |<-------------------------|                             |
  |     render JSON in UI    |                             |
```

Because the Vite proxy is used:

- frontend code does not hard-code backend URLs
- no CORS configuration is required during local development

---

## API contract

The financials API behaves as a single snapshot aggregate. The versioned route
names use `financials` as the primary resource because the read and save
endpoints load and persist the full financial workspace.

The backend exposes `/api/v1/auth/csrf`, `signup`, `signin`, `session`, and
`signout` as the account/session foundation. Account `POST` requests require the CSRF token and
cookie bootstrapped by the `csrf` endpoint.
Passwords use Spring Security's adaptive hash format; opaque session tokens are
stored only as SHA-256 hashes and sent in `HttpOnly`, `SameSite=Strict` cookies.
Each signup creates a `Personal` workspace and owner membership. These sessions
authorize `/api/v1/financials/**`; the sole
membership is automatic, while accounts with multiple memberships send
`X-Workspace-ID`. Financial writes require the CSRF token. A workspace without
an explicitly migrated or created relational snapshot returns `404`. The
browser can create an empty version-1 snapshot from selected pay-period dates;
existing data continues to use the explicit, backed-up migration workflow.

With the PostgreSQL backend running, an operator can explicitly back up and
migrate one legacy JSON file or active JSONB document into an empty owned
workspace:

```powershell
.\scripts\migrate-financial-snapshot-to-workspace.ps1 `
    -Source json-file `
    -InputPath .\backend\data\financials.local.json `
    -BackupPath C:\protected-backups\financials-before-workspace-migration.json `
    -DestinationEmail owner@example.com `
    -WorkspaceId 1 `
    -ConfirmMigration
```

Use `-Source jsonb-document` without `-InputPath` for the active legacy
PostgreSQL document. The command refuses repository backup paths by default,
records the backup SHA-256 fingerprint, preserves audit history, and verifies
only versions and record counts. Roll back an unchanged migration with:

```powershell
.\scripts\rollback-workspace-snapshot-migration.ps1 `
    -MigrationId <uuid> `
    -ConfirmRollback
```

See `docs/database-storage-guide.md` for prerequisites, exact safety boundaries,
and recovery semantics.

Financial snapshot endpoints:

```http
GET /api/v1/financials
GET /api/v1/financials/history
GET /api/v1/financials/export
POST /api/v1/financials
POST /api/v1/financials/restore?expectedVersion=<current-version>
PUT /api/v1/financials
```

The Financials UI currently uses a draft/save workflow:

- one request loads the financial snapshot when the app opens
- edits are made locally in the browser
- one save request persists the full snapshot to the backend with the current
  snapshot version

If another tab or client saves first, the backend returns `409 Conflict` rather
than silently overwriting the newer snapshot.

The history endpoint returns recent saved-change audit events with version
movement, coarse resource metadata, and aggregate projection summaries. Treat
audit history as personal financial data.

The export endpoint downloads the currently saved source snapshot as a JSON
backup envelope. Restore accepts that envelope unchanged and requires the
target workspace's current version separately. The backup's embedded version
remains source metadata, so an older backup can be restored deliberately while
a concurrent target write still returns `409 Conflict`. Application backups do
not include complete relational audit history and should be handled as personal
financial data.

PowerShell helpers are available for local operators:

```powershell
$env:FINANCIALS_ACCOUNT_EMAIL = "owner@example.com"
$env:FINANCIALS_ACCOUNT_PASSWORD = "<account password>"
.\scripts\export-financial-snapshot.ps1 -OutputPath "$HOME\Downloads\financial-snapshot.json"
.\scripts\restore-financial-snapshot.ps1 -InputPath "$HOME\Downloads\financial-snapshot.json" -ConfirmRestore
```

The export helper refuses repository output paths unless `-AllowRepositoryPath`
is supplied for synthetic/mock data. The restore helper reads the current
target version immediately before submitting the backup. Both helpers create
an account session, use `-WorkspaceId` when explicitly supplied, and revoke the
session afterward.

The versioned full-snapshot save is the sole supported financial mutation
boundary. Record and pay-period edits stay local until the user saves the
complete workspace.

---

## Financials feature

The application includes a household cash-flow planning workspace with sidebar
sections for:

- overview totals, including assets, debt, net worth, and disposable income
- configurable record-ID projection inputs for paycheck income, rent, and rent reserve
- next pay period projections for paycheck income, bills, housing set-asides,
  possible debt payments, and possible savings transfers
- monthly withdrawals with pay period planning
- annual withdrawals that can be included in the active pay period
- income summary source rows plus derived net/disposable income values
- income calendar events with recurring payday generation and
  received/current/upcoming status
- retirement accounts
- investments
- cash and savings
- insurance and benefits
- debt balances
- important dates with passed/next/upcoming status

Editable tables support adding, editing, removing, warning before removal,
resetting unsaved changes, and saving the full draft snapshot. Displayed dates
use `MM/DD/YYYY`; browser date inputs use native date controls for editing.

Pay period dates are automatically derived from the saved schedule and today's
date when the app opens. Manually changing the pay period dates updates that
schedule anchor on the next save.

The Income Calendar generates weekly, biweekly, semimonthly, or monthly
paycheck rows for a selected year from cadence-appropriate anchor dates,
starting check number, label, and type. Calendar-month dates clamp to shorter
months. By default it replaces existing numbered income rows in that year
while preserving one-time income events such as tax returns or bonuses.

The Projection view is derived from the saved snapshot and current draft state.
It focuses on the next pay period, using primary paycheck income annualized
from the workspace cadence, bills due, annual withdrawals due, the selected
housing payment, the housing reserve account, and current debt to estimate a
possible debt payment. If debt is covered, remaining cash is shown as a
possible savings transfer. The current period is shown only as supporting
context.

This product is scoped to household cash-flow and pay-period planning. Its
projections are estimates, not accounting records, transaction reconciliation,
transfer instructions, or financial advice.

Each versioned snapshot stores its pay cadence and IANA planning time zone.
The backend returns one zone-derived `currentDate` for active-period decisions,
while date-only inputs remain stable across browser and server time zones.

---

## Frontend quality tooling

### Linting

```powershell
npm run lint
```

The default verifier also runs the frontend dependency compatibility assertion
documented in `docs/dependency-update-triage.md`.

### Auto-fix lint issues

```powershell
npm run code-quality:fix
```

### Formatting

```powershell
npm run format
```

### Spell checking

```powershell
npm run spell
```

### Frontend tests

```powershell
cd frontend
npm run test
```

### Coverage

```powershell
cd frontend
npm run test -- --coverage
```

---

## Backend quality tooling

### Format Java source

```powershell
cd backend
.\mvnw.cmd spotless:apply
```

### Verify Java formatting

```powershell
cd backend
.\mvnw.cmd spotless:check
```

### Format pom.xml

```powershell
cd backend
.\mvnw.cmd sortpom:sort
```

### Verify pom.xml formatting

```powershell
cd backend
.\mvnw.cmd sortpom:verify
```

### Required PostgreSQL integration gate

After local PostgreSQL setup, the default verifier runs the snapshot-store, V3/V4/V6/V7/V8/V9
workspace-scoped record-adapter, V5 identity-schema, V6 legacy-upgrade,
account/session, and backed-up migration/rollback API integration tests in
dedicated isolated schemas. Set the dedicated local
application-role credentials in the current shell first; the tests do not
contain fallback credentials:

```powershell
$env:DATABASE_USERNAME = "<local app database user>"
$env:DATABASE_PASSWORD = "<local app database password>"
.\scripts\verify-local.ps1
```

---

## Repeatable verification

Run the local equivalent of the non-security CI checks from the repository
root. The default gate requires a configured local PostgreSQL application role
and includes all isolated PostgreSQL integration tests:

```powershell
$env:DATABASE_USERNAME = "<local app database user>"
$env:DATABASE_PASSWORD = "<local app database password>"
.\scripts\verify-local.ps1
```

Run networked security checks separately:

```powershell
.\scripts\run-security-checks.ps1
```

This runs npm audits and an authenticated Snyk scan. It requires the Snyk CLI
version recorded in `.snyk-cli-version`, `SNYK_TOKEN`, and network access; CI
installs the same exact CLI version. A local version mismatch fails before the
audits begin. CI remains the canonical hosted Snyk environment.
The older `.\scripts\verify.ps1` entry point remains as a compatibility wrapper.

Run the opt-in browser workflow smoke test with Playwright:

```powershell
.\scripts\run-browser-checks.ps1
```

On a new machine, install the local Playwright Chromium browser first:

```powershell
.\scripts\run-browser-checks.ps1 -InstallBrowsers
```

The current browser smoke starts Spring Boot and Vite together against all seven
Flyway migrations in a uniquely named schema that is dropped after the run.
Using only committed synthetic data, the
browser test covers signup, sign-in, sign-out, CSRF writes, two-user workspace
isolation, load, edit, save, refresh persistence, deletion, and post-delete
refresh without touching personal local data.

Run focused accessibility and responsive browser gates with:

```powershell
.\scripts\run-browser-checks.ps1 -TestPath e2e/accessibility.spec.ts
.\scripts\run-browser-checks.ps1 -TestPath e2e/responsive.spec.ts
```

The responsive gate traverses every financial section at 320, 390, 768, and
1024 pixels wide. See `docs/accessibility-verification.md` and
`docs/responsive-verification.md` for automated contracts and manual protocols.

Inspect the local PostgreSQL schema and snapshot metadata without modifying
data:

```powershell
.\scripts\inspect-postgres.ps1
```

The inspection script wraps its queries in an explicit read-only transaction
and prints aggregate metadata rather than the full financial snapshot.

Create or update a dedicated read-only PostgreSQL role for MCP servers,
reporting, and inspection tools:

```powershell
.\scripts\setup-postgres-readonly-role.ps1
```

The script prompts for the PostgreSQL administrator password and the read-only
role password. Do not commit or paste those credentials. Use
`financial_app_reader` for external inspection tools, not the write-capable
application role.

Generate a deterministic documentation-drift packet:

```powershell
.\scripts\check-documentation-drift.ps1
```

The script compares the local working tree with `HEAD`, checks source-map path
references, and reports source changes that may need documentation owners. In
GitHub Actions, `.github/workflows/documentation-drift.yml` writes the same
packet to the job summary for pull requests and manual runs.

Generate a deterministic coverage summary packet after frontend coverage and
backend `clean verify` reports exist:

```powershell
.\scripts\write-coverage-summary.ps1
```

In GitHub Actions, the `Coverage Summary` job downloads the frontend Vitest and
backend JaCoCo coverage artifacts and writes the packet to the job summary.

Generate dependency and weekly maintenance packets:

```powershell
.\scripts\triage-dependency-updates.ps1
.\scripts\generate-engineering-status.ps1
```

Dependabot is configured in `.github/dependabot.yml`. The dependency triage
workflow writes review context for dependency-related PRs, and
`.github/workflows/weekly-maintenance.yml` runs scheduled dependency, CI,
documentation, security, and repository-health review packets.

---

## AI-assisted workflows

`AGENTS.md` gives coding agents repository architecture, commands, data-safety
rules, and completion criteria. Repository-scoped skills under
`.agents/skills/` provide focused workflows:

- `$maintain-end-to-end-app` for implementation and verification
- `$review-end-to-end-app` for findings-first code reviews
- `$inspect-financial-postgres` for read-only database diagnosis
- `$triage-github-ci` for GitHub Actions and Snyk failures

These workflows complement deterministic scripts; they do not replace tests,
review, or authenticated security scans.

Use `docs/mcp-integration-guide.md` for GitHub MCP, connector, PR, CI,
PostgreSQL MCP, browser/Playwright, Snyk MCP/API, and branch-cleanup
boundaries. Use `docs/snyk-integration-assessment.md` for the Snyk MCP/API
feasibility decision and token-handling rules.

Use `docs/github-ai-workflows.md` for hosted GitHub AI assistance. The
repository includes a non-blocking Copilot review request workflow and
repository-level Copilot instructions for severity-categorized review comments.
Use `docs/issue-to-implementation-workflow.md` and the GitHub issue forms for
turning bugs, features, and implementation tasks into scoped branches and draft
PRs without guessing requirements.
Use `.\scripts\check-documentation-drift.ps1` and the documentation-drift
workflow to spot likely documentation updates after source, script, workflow,
or agent changes.
Use `docs/dependency-update-triage.md` for Dependabot PRs and
`docs/maintenance-review-workflow.md` for scheduled maintenance and weekly
engineering-status reports.

---

## Troubleshooting

### `npm.ps1 cannot be loaded because running scripts is disabled`

PowerShell is blocking npm scripts.

Fix:

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

Close and reopen PowerShell.

---

### `psql is not recognized`

PostgreSQL is installed, but its `bin` folder is not on the PATH.

Use the full path:

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" --version
```

Optional long-term fix: add this folder to the Windows PATH:

```text
C:\Program Files\PostgreSQL\18\bin
```

---

### `password authentication failed for user "financial_app_user"`

The backend reached PostgreSQL, but the app user's password does not match
`DATABASE_PASSWORD`.

Fix in pgAdmin as the `postgres` admin user:

```sql
ALTER USER financial_app_user WITH PASSWORD 'financial_app_password';
```

---

### `database "financial_app" does not exist`

Create the app database as the `postgres` admin user:

```sql
CREATE DATABASE financial_app OWNER financial_app_user;
```

---

### `relation "financial_snapshot_document" does not exist`

The backend connected to PostgreSQL, but the migrations have not been applied.

Preferred fix:

```powershell
.\scripts\setup-local-postgres.ps1
```

Run the migration command directly if the role and database already exist:

```powershell
.\scripts\migrate-postgres.ps1
```

Do not repair this error by executing versioned SQL files with `psql -f`.

---

### `financial_snapshot_document` count is `0`

The legacy JSONB table exists but has no pre-activation snapshot. This is
normal: PostgreSQL startup does not seed personal or example data. Create the
destination account and use the explicit workspace migration workflow for
existing data.

---

## CI pipeline

GitHub Actions currently validates:

- linting
- spell checking
- TypeScript type safety
- frontend test coverage
- frontend builds
- backend builds
- Snyk dependency/security scans
- CodeQL analysis for Java and JavaScript/TypeScript
- dependency review for pull-request dependency changes

The scan job expects `SNYK_TOKEN` to be configured for both Actions and
Dependabot secrets. `NVD_API_KEY` is not used by the current workflow. If a
restricted event or misconfiguration prevents the workflow from receiving
`SNYK_TOKEN`, Dependabot-triggered runs skip the internal Snyk CLI step with a
warning and should be evaluated against the external Snyk PR check or a manual
rerun.

The scan job reads `.snyk-cli-version`, installs that exact npm package
version, verifies the installed version, and only then runs `snyk test`. To
upgrade the scanner, change the pin intentionally, install the matching local
CLI or direct binary, run `.\scripts\run-security-checks.ps1`, and verify the
hosted pull-request scan.

CodeQL runs for pull requests, pushes to `main`, a weekly schedule, and manual
dispatches. It uploads Java and JavaScript/TypeScript findings to GitHub code
scanning; a completed analysis means results were uploaded, not necessarily
that the repository has no alerts. Dependency Review runs on pull requests and
fails when a dependency change introduces a high- or critical-severity
vulnerability in runtime, development, or unknown scope. These are hosted
GitHub checks and have no complete local equivalent.

---

## Documentation

Additional documentation:

- `docs/adr/README.md`
- `docs/accessibility-verification.md`
- `docs/responsive-verification.md`
- `backend/README.md`
- `frontend/README.md`

Each subproject README is intentionally self-contained.

---

## Notes

Current intentional limitations:

- PostgreSQL is the only runtime persistence path; V2 JSONB and local JSON are
  retained only as explicit legacy migration sources
- financial routes require account sessions and workspace membership; operator
  Basic auth is limited to migration administration and protected metrics
- no routing
- no deployment infrastructure
- no external financial website integrations

Focus areas:

- architecture clarity
- frontend/backend communication
- draft/save state management
- local data persistence boundaries
- modern tooling
- developer experience
- CI/CD workflows
- engineering standards
