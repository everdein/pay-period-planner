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

Use targeted commands while iterating, then run the aggregate gate before
declaring implementation work complete. Report checks as passed, failed, or
skipped; never silently omit a relevant row.

## Change-to-Check Matrix

| Change surface                  | Targeted iteration checks                             | Completion checks                                         | Additional evidence                                                |
| ------------------------------- | ----------------------------------------------------- | --------------------------------------------------------- | ------------------------------------------------------------------ |
| Markdown/docs                   | `npm run spell`                                       | Spell check plus referenced paths/commands                | State whether runtime claims were executed or statically verified  |
| Repository skill                | YAML metadata and linked-reference validation         | `npm run spell`, `git diff --check`                       | Confirm UI metadata names the skill                                |
| Frontend helper/calculation     | Relevant Vitest file                                  | Full local verification                                   | Boundary/date/financial cases and coverage                         |
| React component/workflow        | Relevant Testing Library tests, typecheck, lint       | Full local verification                                   | Keyboard, labels, focus, error/empty/loading states                |
| Redux/API client                | Relevant frontend tests, typecheck                    | Full local verification                                   | Rejected requests, stale state, save/load behavior                 |
| Backend service/domain          | Focused Maven test                                    | Full local verification                                   | Validation, boundaries, regression test                            |
| Controller/DTO/API              | Controller/service tests plus frontend typecheck      | Full local verification                                   | Request/response compatibility and Problem Detail behavior         |
| Audit/history                   | Repository/service/controller tests                   | Full local verification                                   | Version movement, newest-first order, no request-body logging      |
| CSV/XLSX import/export          | Controller/service tests plus frontend typecheck      | Full local verification                                   | Stale version rejection, fixed columns, no personal data in output |
| JSON store                      | Store/repository tests                                | Full local verification                                   | Seed, backup, atomic replacement, malformed data                   |
| PostgreSQL store/config/adapter | Focused integration test                              | `verify-local.ps1 -IncludePostgres`                       | Read-only metadata inspection afterward                            |
| Migration SQL                   | Review ordered migration and constraints              | Full verification with PostgreSQL                         | Fresh and upgraded isolated schema; Flyway history behavior        |
| PowerShell scripts              | PowerShell parser plus safest applicable execution    | Full local verification when orchestration changed        | Exit codes, working directory, cleanup, mutation scope             |
| Dependency/lockfile             | Clean install and affected build/tests                | Full local verification and authenticated security checks | Direct/transitive path, compatibility, both lock files             |
| GitHub workflow                 | Run exact local equivalents                           | Hosted PR run required                                    | Events, permissions, job dependencies, cache paths, secrets        |
| Hosted AI workflow              | Review workflow YAML and docs                         | Hosted PR run required                                    | Copilot policy, billing, permissions, non-blocking behavior        |
| PR/failure summary workflow     | Review workflow YAML, template, and docs              | Hosted PR/run evidence required                           | Summary packets are context, not pass/fail evidence                |
| Issue workflow                  | Review issue forms and implementation guide           | Hosted issue form rendering                               | Scope, data-safety, acceptance criteria, write boundaries          |
| Documentation drift             | `scripts/check-documentation-drift.ps1`               | Hosted documentation-drift workflow                       | Drift packets are hints; verify source claims before acting        |
| Dependency triage               | `scripts/triage-dependency-updates.ps1`               | Dependabot PR plus hosted triage workflow                 | Release notes, lockfiles, security status, compatibility risk      |
| Scheduled maintenance           | `scripts/generate-engineering-status.ps1`             | Weekly maintenance workflow                               | Packets are advisory; external writes require user intent          |
| Security configuration          | Focused configuration inspection                      | Authenticated Snyk scan                                   | Tool/auth state, severity threshold, fixed versions                |
| Accessibility                   | JSX accessibility lint plus focused interaction tests | Full local verification                                   | Manual/browser keyboard and focus review when behavior changed     |
| Browser workflow                | `scripts/run-browser-checks.ps1`                      | Full local verification plus browser smoke when relevant  | Synthetic data, screenshots/traces only when intentionally shared  |
| Cross-layer feature             | Narrow checks in every affected layer                 | Full local verification; add PostgreSQL if applicable     | End-to-end contract and persistence parity                         |

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
code. Opt-in PostgreSQL adapter paths that require a live database, including
the V3/V4 relational CRUD adapter, are verified with `-IncludePostgres`
instead of the default coverage gate.

### PostgreSQL

```powershell
.\scripts\verify-local.ps1 -IncludePostgres
.\scripts\inspect-postgres.ps1
```

The verification option runs `PostgresFinancialsSnapshotStoreIT` and
`PostgresFinancialRecordSnapshotAdapterIT` against fixed isolated schemas,
truncates or recreates their test tables, and drops those schemas afterward. Do
not use it where those schema names belong to another application. Inspection
is read-only and reports metadata rather than financial values. Set
`DATABASE_USERNAME` and `DATABASE_PASSWORD` in the current shell before running
the PostgreSQL verification; the integration tests intentionally have no
embedded credential defaults.

PostgreSQL verification is required for changes to:

- Migrations
- PostgreSQL configuration or profile selection
- SQL or JDBC behavior
- Snapshot serialization/deserialization
- Seed behavior
- Database role assumptions
- JSON/PostgreSQL parity

### Browser workflow

```powershell
.\scripts\run-browser-checks.ps1
.\scripts\run-browser-checks.ps1 -InstallBrowsers
```

The browser smoke test starts Spring Boot and Vite together. Spring Boot runs
with the `json` profile and a disposable data path under `test-results/`, seeded
from committed synthetic example data. The browser test covers load, edit,
save, refresh persistence, delete confirmation, and post-delete refresh. It is
required for changes to browser workflows, Vite proxy behavior, save/load
interaction paths, or the Playwright harness. It does not replace backend API,
unit/service coverage, PostgreSQL verification, or full local verification.

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
| `verify-local.ps1`                  | Build, test, and coverage output; may create ignored local JSON if absent | None by default                                    | Maven metadata/dependencies may need network |
| `verify-local.ps1 -IncludePostgres` | Same as above                                                             | Creates/truncates/drops isolated test schema       | Local database credentials                   |
| `inspect-postgres.ps1`              | None                                                                      | Explicit read-only transactions                    | Local database credentials                   |
| `export-financial-snapshot.ps1`     | Writes the requested export file outside the repository by default        | None                                               | Running backend may read local data          |
| `import-financial-snapshot.ps1`     | None                                                                      | Replaces the saved snapshot through the backend    | Running backend credential/profile           |
| `run-browser-checks.ps1`            | Playwright reports/traces in ignored paths                                | None                                               | May install browser binaries with flag       |
| `run-security-checks.ps1`           | Tool caches/reporting side effects                                        | None                                               | Network and Snyk token                       |
| `write-coverage-summary.ps1`        | Optional GitHub job summary output                                        | None                                               | None                                         |
| `check-documentation-drift.ps1`     | Optional GitHub job summary output                                        | None                                               | None                                         |
| `triage-dependency-updates.ps1`     | Optional GitHub job summary output                                        | None                                               | None                                         |
| `generate-engineering-status.ps1`   | Optional GitHub job summary output                                        | None                                               | None                                         |
| `setup-local-postgres.ps1`          | None                                                                      | Creates/updates role, database, and schema         | PostgreSQL administrator credential          |
| `setup-postgres-readonly-role.ps1`  | None                                                                      | Creates/updates read-only role and grants          | PostgreSQL administrator credential          |
| `start-backend-postgres.ps1`        | Logs/runtime output                                                       | Seeds active snapshot when empty; later app writes | Application database credential              |

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

Current CI does not run the opt-in PostgreSQL integration test. Local
PostgreSQL evidence must therefore be reported separately for persistence
changes.

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
