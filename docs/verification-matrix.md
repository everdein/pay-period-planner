# Verification Matrix

## Default Completion Gate

From the repository root:

```powershell
.\scripts\verify-local.ps1
```

This runs:

1. Environment checks
2. Spell checking
3. Frontend TypeScript checking
4. Frontend linting
5. Frontend tests with coverage thresholds
6. Frontend production build
7. Backend formatting and POM ordering checks
8. Backend clean build, tests, JaCoCo coverage, and packaging
9. Required isolated PostgreSQL integration tests

Use targeted commands while iterating, then run the aggregate gate before
declaring implementation work complete. Report checks as passed, failed, or
skipped; never silently omit a relevant row. Set `DATABASE_USERNAME` and
`DATABASE_PASSWORD` for the dedicated local application role before running the
gate.

## Change-to-Check Matrix

| Change surface                  | Targeted iteration checks                              | Completion checks                                         | Additional evidence                                                |
| ------------------------------- | ------------------------------------------------------ | --------------------------------------------------------- | ------------------------------------------------------------------ |
| Markdown/docs                   | `npm run spell`                                        | Spell check plus referenced paths/commands                | State whether runtime claims were executed or statically verified  |
| Repository skill                | YAML metadata and linked-reference validation          | `npm run spell`, `git diff --check`                       | Confirm UI metadata names the skill                                |
| Frontend helper/calculation     | Relevant Vitest file                                   | Full local verification                                   | Boundary/date/financial cases and coverage                         |
| React component/workflow        | Relevant Testing Library tests, typecheck, lint        | Full local verification                                   | Keyboard, labels, focus, error/empty/loading states                |
| Redux/API client                | Relevant frontend tests, typecheck                     | Full local verification                                   | Rejected requests, stale state, save/load behavior                 |
| Backend service/domain          | Focused Maven test                                     | Full local verification                                   | Validation, boundaries, regression test                            |
| Controller/DTO/API              | Controller/service tests plus frontend typecheck       | Full local verification                                   | Request/response compatibility and Problem Detail behavior         |
| Audit/history                   | Repository/service/controller tests                    | Full local verification                                   | Version movement, newest-first order, no request-body logging      |
| CSV/XLSX import/export          | Controller/service tests plus frontend typecheck       | Full local verification                                   | Stale version rejection, fixed columns, no personal data in output |
| Legacy JSON migration           | Migration service/API tests                           | Full local verification                                   | Backup fingerprint, explicit owner/workspace, source unchanged     |
| PostgreSQL store/config/adapter | Focused integration profile                            | Full local verification                                   | Read-only metadata inspection afterward                            |
| Migration SQL                   | Review ordered migration and constraints               | Full verification with PostgreSQL                         | Fresh and upgraded isolated schema; Flyway history behavior        |
| Workspace data migration        | Service/controller tests plus script parser             | Full local verification                                   | External backup fingerprint, ownership, counts, audit, rollback    |
| PowerShell scripts              | PowerShell parser plus safest applicable execution     | Full local verification when orchestration changed        | Exit codes, working directory, cleanup, mutation scope             |
| Dependency/lockfile             | Clean install and affected build/tests                 | Full local verification and authenticated security checks | Direct/transitive path, compatibility, both lock files             |
| GitHub workflow                 | Run exact local equivalents                            | Hosted PR run required                                    | Events, permissions, job dependencies, cache paths, secrets        |
| Hosted AI workflow              | Review workflow YAML and docs                          | Hosted PR run required                                    | Copilot policy, billing, permissions, non-blocking behavior        |
| PR/failure summary workflow     | Review workflow YAML, template, and docs               | Hosted PR/run evidence required                           | Summary packets are context, not pass/fail evidence                |
| Issue workflow                  | Review issue forms and implementation guide            | Hosted issue form rendering                               | Scope, data-safety, acceptance criteria, write boundaries          |
| Documentation drift             | `scripts/check-documentation-drift.ps1`                | Hosted documentation-drift workflow                       | Drift packets are hints; verify source claims before acting        |
| Dependency triage               | `scripts/triage-dependency-updates.ps1`                | Dependabot PR plus hosted triage workflow                 | Release notes, lockfiles, security status, compatibility risk      |
| Scheduled maintenance           | `scripts/generate-engineering-status.ps1`              | Weekly maintenance workflow                               | Packets are advisory; external writes require user intent          |
| Security configuration          | Focused configuration inspection                       | Authenticated Snyk scan                                   | Tool/auth state, severity threshold, fixed versions                |
| Observability/correlation       | Filter, security, API-client, and error-boundary tests | Full local verification                                   | No sensitive fields; bounded metric tags; protected Actuator       |
| Accessibility                   | JSX accessibility lint plus focused interaction tests  | Full local verification                                   | Manual/browser keyboard and focus review when behavior changed     |
| Browser workflow                | `scripts/run-browser-checks.ps1`                       | Full local verification plus browser smoke when relevant  | Synthetic data, screenshots/traces only when intentionally shared  |
| Cross-layer feature             | Narrow checks in every affected layer                  | Full local verification                                   | End-to-end contract and persistence parity                         |

## Canonical Commands

### Environment and setup

```powershell
.\scripts\check-environment.ps1
.\scripts\check-environment.ps1 -IncludePostgres
.\scripts\bootstrap-local.ps1
.\scripts\bootstrap-local.ps1 -IncludePostgres
.\scripts\setup-postgres-readonly-role.ps1
```

Bootstrap installs dependencies. The PostgreSQL option also creates or updates
a local role, database, and schema; it is setup, not verification.

### Financial snapshot import/export

```powershell
$env:FINANCIALS_ACCOUNT_EMAIL = "<account email>"
$env:FINANCIALS_ACCOUNT_PASSWORD = "<account password>"
.\scripts\export-financial-snapshot.ps1 -Format json -OutputPath "$HOME\Downloads\financial-snapshot.json"
.\scripts\export-financial-snapshot.ps1 -Format csv -OutputPath "$HOME\Downloads\financial-snapshot.csv"
.\scripts\export-financial-snapshot.ps1 -Format xlsx -OutputPath "$HOME\Downloads\financial-snapshot.xlsx"
.\scripts\import-financial-snapshot.ps1 -InputPath "$HOME\Downloads\financial-snapshot.csv" -ConfirmRestore
.\scripts\import-financial-snapshot.ps1 -InputPath "$HOME\Downloads\financial-snapshot.xlsx" -ConfirmRestore
```

Exports and import files can contain personal financial data. Keep them outside
the repository unless using synthetic/mock data and an explicit override.

### Frontend

```powershell
npm --prefix frontend run type-check
npm --prefix frontend run lint
npm --prefix frontend run test
npm --prefix frontend run test -- --coverage
npm --prefix frontend run test:e2e
npm --prefix frontend run build
```

Frontend coverage thresholds:

| Counter    | Minimum |
| ---------- | ------: |
| Statements |     45% |
| Branches   |     45% |
| Functions  |     35% |
| Lines      |     46% |

The HTML report is generated under `frontend/coverage/`. Treat it as generated
output unless a task explicitly updates tracked coverage artifacts.

### Backend

From `backend/`:

```powershell
.\mvnw.cmd -B spotless:check sortpom:verify
.\mvnw.cmd -B test "-Dtest=ClassName"
.\mvnw.cmd -B clean verify
```

`clean verify` compiles, tests, packages, checks formatting, creates the JaCoCo
report, and enforces at least 80% bundle line coverage for default production
code. The aggregate verifier then runs every PostgreSQL `*IT` class through the
`postgres-integration` profile with JaCoCo disabled for that isolated database
run.

### PostgreSQL

```powershell
.\scripts\verify-local.ps1
.\scripts\inspect-postgres.ps1
```

The default verifier runs `PostgresFinancialsSnapshotStoreIT`,
`PostgresFinancialRecordSnapshotAdapterIT`, `PostgresIdentitySchemaIT`, and
`PostgresWorkspaceOwnershipSchemaIT`, `AccountSessionServiceIT`,
`AccountSessionApiIT`, `WorkspaceSnapshotMigrationApiIT`, and
`WorkspaceFinancialRuntimeApiIT` against isolated
schemas, recreates their test tables,
and drops those schemas afterward. The identity
test checks normalized-email, membership-role, single-owner, and
session-lifetime constraints. The ownership tests exercise a V5-to-V6 upgrade,
new-row ownership constraints, one active snapshot per workspace, and
cross-workspace isolation for every relational record family. The account tests
inspect password/token hashing, session recovery/revocation, two-user
membership isolation, and Flyway-before-repository startup. They also verify
that PostgreSQL rejects legacy Basic credentials for financial access. The
runtime API test verifies session authorization, CSRF enforcement, relational
audit continuity, and two-user/two-workspace isolation. The migration API test
runs Flyway
through V7 and checks both source types, Basic/session authorization, backup
fingerprints, destination ownership, record/audit preservation, metadata-only
verification, overwrite refusal, successful rollback, and changed-version
rollback refusal. Do not use it
where those schema names belong to another application. Inspection is
read-only and reports metadata rather than financial values. Set
`DATABASE_USERNAME` and `DATABASE_PASSWORD` in the current shell before running
the PostgreSQL verification; the integration tests intentionally have no
embedded credential defaults.

PostgreSQL verification is required for changes to:

- Migrations
- Datasource, Flyway, or PostgreSQL runtime configuration
- SQL or JDBC behavior
- Snapshot serialization/deserialization
- Empty-workspace and no-implicit-seed behavior
- Database role assumptions
- Identity, workspace, membership, or session constraints
- Legacy JSON/JSONB migration behavior

### Browser workflow

```powershell
.\scripts\run-browser-checks.ps1
.\scripts\run-browser-checks.ps1 -InstallBrowsers
```

The browser smoke test starts Spring Boot and Vite together against a unique
PostgreSQL schema that the wrapper drops in its `finally` block. The test uses
only committed synthetic example data and covers
signup, sign-in, sign-out, CSRF writes, two-user workspace isolation, load,
edit, save, refresh persistence, delete confirmation, and post-delete refresh.
It is required for changes to browser workflows, identity/session behavior,
Vite proxy behavior, save/load interaction paths, or the Playwright harness. It
does not replace backend API, unit/service, PostgreSQL integration, or full
local verification.

### Security

```powershell
.\scripts\run-security-checks.ps1
```

This requires network access, the Snyk CLI version recorded in
`.snyk-cli-version`, and `SNYK_TOKEN`. It runs root and frontend npm audits plus
authenticated Snyk discovery across all projects. A missing or mismatched CLI,
missing authentication, or an unavailable service is a skipped/failed check,
not a pass.

CI reads the same pin, installs that exact npm package version, verifies
`snyk --version`, and then scans. For a Snyk upgrade, update the pin, install
the matching local CLI or direct binary, run this command, and verify the
hosted pull-request scan. Do not replace the pin with `latest`.

Dependabot-triggered GitHub Actions runs may not receive repository secrets.
For those runs, the hosted CI scan job skips the internal Snyk CLI step with a
warning when `SNYK_TOKEN` is unavailable. Treat that as skipped CLI evidence,
not a pass; use the external Snyk PR check or an owner-approved manual rerun
when dependency risk requires authenticated CLI confirmation.

Snyk MCP/API usage is supporting triage only. It can explain findings or
propose upgrades, but it does not replace the security script or hosted CI
gate. When MCP/API is used, report the Snyk tool, profile, auth state, scanned
manifest, advisory identifier, fixed version, and skipped/unavailable checks
without exposing token values.

GitHub CodeQL analyzes Java and JavaScript/TypeScript on pull requests, pushes
to `main`, a weekly schedule, and manual dispatches. Review the uploaded code
scanning alerts as well as the workflow result: successful analysis confirms
that results were uploaded, not that there are no alerts. GitHub Dependency
Review runs only on pull requests and blocks newly introduced high- or
critical-severity vulnerabilities across runtime, development, and unknown
dependency scopes. These hosted checks depend on GitHub repository data and do
not have complete local equivalents.

### Documentation and diff hygiene

```powershell
npm run spell
git diff --check
git status --short
.\scripts\write-coverage-summary.ps1
.\scripts\check-documentation-drift.ps1
.\scripts\triage-dependency-updates.ps1
.\scripts\generate-engineering-status.ps1
```

Also validate that documented paths exist and commands match their owning
scripts/configuration. Documentation-drift packets are advisory; resolve them
against the source map and executable sources before posting or changing docs.

## Mutation and External-Dependency Matrix

| Command                             | Local writes                                                              | Database writes                                    | Network/credentials                          |
| ----------------------------------- | ------------------------------------------------------------------------- | -------------------------------------------------- | -------------------------------------------- |
| `check-environment.ps1`             | None                                                                      | None                                               | None                                         |
| `bootstrap-local.ps1`               | Dependency directories/hooks                                              | Only with `-IncludePostgres`                       | Package installation may need network        |
| `verify-local.ps1`                  | Build, test, and coverage output                                      | Creates and drops isolated test schemas            | Local database credentials; Maven may need network |
| `inspect-postgres.ps1`              | None                                                                      | Explicit read-only transactions                    | Local database credentials                   |
| `export-financial-snapshot.ps1`     | Writes the requested export file outside the repository by default        | Creates and revokes a temporary account session    | Account credential and selected workspace    |
| `import-financial-snapshot.ps1`     | None                                                                      | Replaces the saved snapshot; creates/revokes session | Account credential and selected workspace  |
| `run-browser-checks.ps1`            | Playwright reports/traces in ignored paths                                | None                                               | May install browser binaries with flag       |
| `run-security-checks.ps1`           | Tool caches/reporting side effects                                        | None                                               | Network and Snyk token                       |
| `write-coverage-summary.ps1`        | Optional GitHub job summary output                                        | None                                               | None                                         |
| `check-documentation-drift.ps1`     | Optional GitHub job summary output                                        | None                                               | None                                         |
| `triage-dependency-updates.ps1`     | Optional GitHub job summary output                                        | None                                               | None                                         |
| `generate-engineering-status.ps1`   | Optional GitHub job summary output                                        | None                                               | None                                         |
| `setup-local-postgres.ps1`          | None                                                                      | Creates/updates role/database and invokes Flyway   | PostgreSQL administrator credential          |
| `migrate-postgres.ps1`              | Maven output/cache                                                        | Applies and validates Flyway migrations            | Application database credential              |
| `migrate-financial-snapshot-to-workspace.ps1` | External JSON backup and API response metadata                  | Creates workspace relational snapshot/history      | Financial API credential and personal source |
| `rollback-workspace-snapshot-migration.ps1` | Metadata-only API response                                             | Deactivates unchanged migrated snapshot             | Financial API credential and migration UUID  |
| `setup-postgres-readonly-role.ps1`  | None                                                                      | Creates/updates read-only role and grants          | PostgreSQL administrator credential          |
| `start-backend.ps1`                 | Logs/runtime output                                                       | Applies Flyway migrations; later app writes        | Application database credential              |

Do not run mutating or credentialed checks solely to make a checklist look
complete. Explain why they were required and what target they used.

## CI Mapping

| GitHub job           | Local equivalent                     | Hosted-only concern                                                       |
| -------------------- | ------------------------------------ | ------------------------------------------------------------------------- |
| Code Coverage        | Frontend test with `--coverage`      | Artifact upload                                                           |
| Code Quality         | Frontend lint                        | Linux runner behavior                                                     |
| Spell Check          | Root spell command                   | Clean checkout/install                                                    |
| Type Check           | Frontend typecheck                   | Clean checkout/install                                                    |
| Build & Test Backend | Formatting plus Maven `clean verify` | Linux/JDK action/cache                                                    |
| PostgreSQL Integration | Maven `postgres-integration` profile | Ephemeral PostgreSQL service container and Linux/JDK action/cache         |
| Build Frontend       | Frontend build                       | Linux/Node action/cache                                                   |
| Coverage Summary     | Coverage summary script              | Artifact download and GitHub job summary                                  |
| Scans                | Security script                      | Repository secret, Dependabot secret restrictions, and hosted Snyk access |
| Copilot Review       | No local equivalent                  | Copilot policy, budget, and reviewer API                                  |
| PR Summary Packet    | No local equivalent                  | Pull request event and job summary                                        |
| CI Failure Summary   | No local equivalent                  | Workflow-run event and Actions metadata                                   |
| Issue Forms          | No local equivalent                  | GitHub issue form rendering                                               |
| Documentation Drift  | Documentation drift script           | Pull request diff and job summary                                         |
| Dependency Triage    | Dependency triage script             | Dependabot and pull request metadata                                      |
| Weekly Maintenance   | Engineering status script            | Schedule actor, audits, recent CI runs                                    |
| Deploy               | No real local equivalent             | Manual placeholder only                                                   |

CI runs the same `postgres-integration` Maven profile as the default local gate.
The downstream `Scans` job depends on the PostgreSQL job, so hosted checks do
not advance past a failed integration result. A hosted run is still required to
validate service-container behavior.

## Verification Evidence

A completion report must include:

- Exact commands run
- Pass/fail result
- Relevant test counts and coverage gate result
- PostgreSQL target/scope without credentials or financial values
- Security authentication status without secret values
- Checks skipped and why
- Remaining hosted, manual, visual, or production-only risk

“Tests passed” is insufficient when lint, builds, coverage, PostgreSQL, or
security checks were also required.
