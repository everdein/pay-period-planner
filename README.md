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
- PostgreSQL 18+ only if running the `postgres` backend profile
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

---

## Local persistence modes

The backend supports two local persistence modes.

### Mode 1: JSON-backed local file

This is the default mode.

Use this when you want the fastest local startup and do not want to configure
PostgreSQL.

```text
backend/data/financials.local.json
```

This file is ignored by Git so personal financial data stays local.

If the local file does not exist, the backend creates it from:

```text
backend/data/financials.example.json
```

### Mode 2: PostgreSQL-backed snapshot

Use this when you want database-backed local persistence.

The backend stores the full financial snapshot as a PostgreSQL `jsonb` document
in:

```text
financial_snapshot_document.snapshot_json
```

This mode requires:

- local PostgreSQL running on `localhost:5432`
- database: `financial_app`
- database user: `financial_app_user`
- password: `financial_app_password`
- schema migrations applied from `backend/src/main/resources/db/migration`

Important: `financial_app_user` is a database user used by the backend. It is not
a frontend login user.

Current implementation note: the active PostgreSQL persistence path stores the
whole financial snapshot in `financial_snapshot_document.snapshot_json`. The
normalized V1 tables also exist, but ADR 0009 keeps them inactive rather than
using them as the runtime relational adapter path. The V3/V4
`financial_record_*` tables and adapter are the tested relational path for
granular persistence, but they are not active runtime storage yet.

---

## Running the application

There are two supported local ways to start the app. Choose one backend storage
mode, then run the frontend in a separate terminal.

| Path | Storage                   | Best for                            |
| ---- | ------------------------- | ----------------------------------- |
| A    | Local JSON file           | Fastest startup after a fresh clone |
| B    | PostgreSQL JSONB snapshot | Testing database-backed persistence |

In both paths, a running backend should keep its terminal open. If the command
returns to the PowerShell prompt, the backend stopped during startup; check the
last Spring Boot log lines before `BUILD SUCCESS` or `BUILD FAILURE`.

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

## Path A: Run with default JSON storage

This is the simplest path after a fresh clone.

Terminal 1, backend:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Backend URL:

```text
http://localhost:8080
```

Terminal 2, frontend:

```powershell
cd frontend
npm run dev
```

Frontend URL:

```text
http://localhost:3000
```

---

## Path B: Run with PostgreSQL storage

Use this when developing or testing the database-backed persistence path.

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
- apply the local migration SQL files when the target tables do not already
  exist
- verify the `financial_snapshot_document` and `financial_record_snapshot`
  tables exist

The script is safe to rerun. If the database and tables already exist, it skips
the create/migration steps and verifies the setup.

After setup completes, start the PostgreSQL-backed backend from the repository
root:

```powershell
cd C:\Users\<you>\dev\end-to-end-app
.\scripts\start-backend-postgres.ps1
```

The script starts Spring Boot with the `postgres` profile and the dedicated
database user `financial_app_user`; it does not run the application as the
PostgreSQL admin user.

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

Manual `psql` setup commands are available below as a fallback for
troubleshooting.

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

If `psql` is on the PATH:

```powershell
psql -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V1__create_financials_schema.sql
psql -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V2__create_financial_snapshot_document.sql
psql -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V3__create_financial_record_snapshot_schema.sql
psql -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V4__add_financial_record_app_id_constraints.sql
```

On Windows, if `psql` is not recognized, use the full PostgreSQL path:

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V1__create_financials_schema.sql

& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V2__create_financial_snapshot_document.sql

& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V3__create_financial_record_snapshot_schema.sql

& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V4__add_financial_record_app_id_constraints.sql
```

When prompted, use:

```text
financial_app_password
```

Verify tables:

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
financial_record_snapshot
important_date
income_event
income_summary_item
monthly_withdrawal
```

A count of `0` in `financial_snapshot_document` is acceptable on a fresh
database. The backend can seed the first snapshot from local JSON data.

The current JSONB-backed runtime implementation uses
`financial_snapshot_document`. The V1 tables are inactive historical
groundwork and may remain empty. The V3/V4 `financial_record_*` tables are the
tested relational adapter path and may also remain empty until the runtime
service is intentionally wired to that adapter.

---

## Testing the PostgreSQL setup script

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

Every `/api/v1/financials/**` endpoint requires HTTP Basic authentication. The
frontend sign-in form stores the Basic token in browser session storage for the
current tab/session only after an authenticated snapshot request succeeds. If
the backend is not reachable, sign-in remains on the form and reports a backend
connection error. Local scripts read `FINANCIALS_API_USERNAME` and
`FINANCIALS_API_PASSWORD` or fall back to the local defaults above.

Financial snapshot endpoints:

```http
GET /api/v1/financials
GET /api/v1/financials/history
GET /api/v1/financials/export
GET /api/v1/financials/export/csv
GET /api/v1/financials/export/xlsx
POST /api/v1/financials/import/csv
POST /api/v1/financials/import/xlsx
PUT /api/v1/financials
PUT /api/v1/financials/pay-period
```

Granular record endpoints:

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

The export endpoints download the currently saved source snapshot as JSON, CSV,
or XLSX. The JSON file preserves the full request-shaped backup envelope; CSV
and XLSX use one tabular exchange format that can also be imported back through
the version-checked restore endpoints. Imports replace the complete snapshot,
so stale files return `409 Conflict` instead of silently overwriting newer
data. Source-shaped exports do not currently include audit history. Export and
import files should be handled as personal financial data.

PowerShell helpers are available for local operators:

```powershell
.\scripts\export-financial-snapshot.ps1 -Format csv -OutputPath "$HOME\Downloads\financial-snapshot.csv"
.\scripts\import-financial-snapshot.ps1 -InputPath "$HOME\Downloads\financial-snapshot.csv" -ConfirmRestore
```

The export helper refuses repository output paths unless `-AllowRepositoryPath`
is supplied for synthetic/mock data.

The individual record endpoints remain available as granular API options, but
the current UI treats the snapshot as the source of truth.

---

## Financials feature

The application includes a personal financial snapshot area with sidebar
sections for:

- overview totals, including assets, debt, net worth, and disposable income
- next pay period projections for paycheck income, bills, rent set-asides, debt
  payoff, and possible HYSA transfer
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

The Income Calendar can generate bi-weekly paycheck rows for a selected year
from the first payday, starting check number, label, and type. By default it
replaces existing numbered income rows in that year while preserving one-time
income events such as tax returns or bonuses.

The Projection view is derived from the saved snapshot and current draft state.
It focuses on the next pay period, using bi-weekly net income, bills due, annual
withdrawals due, the rent bill, the rent savings account, and current debt to
estimate what can go toward credit card debt. If debt is covered, remaining cash
is shown as a possible Apple HYSA transfer. The current period is shown only as
supporting context.

---

## Frontend quality tooling

### Linting

```powershell
npm run lint
```

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

### PostgreSQL profile smoke test

After local PostgreSQL setup, this command runs the snapshot-store and V3/V4
record-adapter integration tests in dedicated isolated schemas. Set the
dedicated local application-role credentials in the current shell first; the
tests do not contain fallback credentials:

```powershell
$env:DATABASE_USERNAME = "<local app database user>"
$env:DATABASE_PASSWORD = "<local app database password>"
.\scripts\verify-local.ps1 -IncludePostgres
```

---

## Repeatable verification

Run the local equivalent of the non-security CI checks from the repository
root:

```powershell
.\scripts\verify-local.ps1
```

When a change affects PostgreSQL configuration, serialization, migrations, or
storage behavior, set `DATABASE_USERNAME` and `DATABASE_PASSWORD` for the local
application role before running:

```powershell
.\scripts\verify-local.ps1 -IncludePostgres
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

The current browser smoke starts Spring Boot and Vite together. Spring Boot uses
the `json` profile with a disposable data path under `test-results/`, seeded
from committed synthetic example data. The browser test covers load, edit,
save, refresh persistence, delete confirmation, and post-delete refresh without
touching personal local data.

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

Manual fallback:

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V1__create_financials_schema.sql

& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V2__create_financial_snapshot_document.sql

& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V3__create_financial_record_snapshot_schema.sql

& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U financial_app_user -d financial_app -f .\backend\src\main\resources\db\migration\V4__add_financial_record_app_id_constraints.sql
```

---

### `financial_snapshot_document` count is `0`

The schema exists, but the database does not have an active snapshot row yet.

This is normal on a fresh machine. The backend should seed from:

```text
backend/data/financials.local.json
```

or fall back to:

```text
backend/data/financials.example.json
```

If real local data from another machine is needed, export/import the old local
PostgreSQL database or copy the ignored local JSON file intentionally.

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
- `backend/README.md`
- `frontend/README.md`

Each subproject README is intentionally self-contained.

---

## Notes

Current intentional limitations:

- JSON remains the default local fallback
- PostgreSQL stores the active runtime snapshot as `jsonb`; V3/V4 relational
  tables and adapter support tested database-backed CRUD, but the runtime
  service is not wired to them yet
- single local Basic-auth application user; no multi-user identity or tenant
  ownership model yet
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
