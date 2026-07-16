# Documentation Source Map

Use the most authoritative source available for each claim. Documentation may
summarize these sources but must not override them.

| Claim                             | Primary source                                                                                     | Useful corroboration                                                                                                                                                                 |
| --------------------------------- | -------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Repository scope and rules        | `AGENTS.md`                                                                                        | Root and subproject READMEs                                                                                                                                                          |
| Runtime/dependency versions       | `pom.xml`, `package.json`, lockfiles                                                               | CI setup actions                                                                                                                                                                     |
| Frontend dependency overrides     | `frontend/package.json` and `frontend/package-lock.json`                                           | Compatibility checker, dependency triage guide, ADR 0020, CI                                                                                                                         |
| npm commands                      | Root and frontend `package.json`                                                                   | PowerShell scripts, CI                                                                                                                                                               |
| Local setup and verification      | `scripts/*.ps1`                                                                                    | `AGENTS.md`, README                                                                                                                                                                  |
| Frontend port/proxy/test behavior | `frontend/vite.config.ts`                                                                          | Frontend README                                                                                                                                                                      |
| Browser workflow smoke tests      | `frontend/playwright.config.ts`                                                                    | `scripts/run-browser-checks.ps1`                                                                                                                                                     |
| Portfolio visual evidence         | `frontend/playwright.portfolio.config.ts`, `frontend/portfolio/portfolio-evidence.spec.ts`         | `scripts/capture-portfolio-evidence.ps1`, `docs/portfolio-case-study.md`, `docs/images/portfolio/*.png`                                                                              |
| Public portfolio corpus           | `docs/public-corpus.json`, `scripts/check-public-corpus.ps1`                                       | `docs/README.md`, approved documents, selected source/tests/workflows                                                                                                                |
| API routes and status behavior    | Controllers and exception handler                                                                  | DTOs, controller tests                                                                                                                                                               |
| Current workspace selection       | `CurrentWorkspace` and `AuthenticatedRequestWorkspace`                                             | Security configuration, adapter tests, PostgreSQL API integration tests                                                                                                              |
| Workspace snapshot initialization | `WorkspaceFinancialSnapshotInitializer`, its service, presenter, and controller                    | Controller tests, account-session PostgreSQL API integration test, API contract                                                                                                      |
| Financial application errors      | Financial service exceptions and `ApiExceptionHandler`                                             | Service and controller tests, API contract                                                                                                                                           |
| API payload shape                 | Request/response DTOs and frontend types                                                           | API client tests                                                                                                                                                                     |
| Frontend API failure propagation  | `frontend/src/api/client.ts` and `frontend/src/features/financials/financialsSlice.ts`             | API client, Redux slice, workflow notice, onboarding, and application tests                                                                                                          |
| Frontend financial draft state    | `frontend/src/features/financials/financialsDraftReducer.ts` and `useFinancialsDraftWorkspace.ts`  | Domain-hook facade tests, reducer tests, workspace tests, and ADR 0025                                                                                                               |
| Frontend workspace route state    | `FinancialsWorkspaceState.tsx`, `FinancialsWorkflowFeedback.tsx`, and `FinancialsPage.tsx`         | Focused component tests, application tests, and browser workflow                                                                                                                     |
| JSON snapshot backup and restore  | `FinancialsController`, financial workspace query/command services, and `FinancialSnapshotBackup`  | Controller/service tests, `scripts/export-financial-snapshot.ps1`, `scripts/restore-financial-snapshot.ps1`                                                                          |
| Backend financial record model    | `backend/src/main/java/com/example/backend/domain/financials/*`                                    | Service and repository tests                                                                                                                                                         |
| Business calculations             | `FinancialSnapshotCalculator` and frontend projection/date helpers                                 | Focused tests                                                                                                                                                                        |
| Projection role identity          | `FinancialProjectionRoles`, V8 migration, reducer, and projection settings                         | Normalizer, repository, adapter integration, workspace, and projection tests                                                                                                         |
| Pay cadence and planning date     | `FinancialPlanningSettings`, V9 migration, calculator, and frontend date/projection helpers        | Request/response mapping, repository, adapter/API integration, and focused recurrence tests                                                                                          |
| Default/profile behavior          | `application*.properties`                                                                          | Startup scripts                                                                                                                                                                      |
| Seed and initialization behavior  | Repository implementation/configuration                                                            | Backend README                                                                                                                                                                       |
| PostgreSQL active storage         | Store and relational-adapter implementations plus migrations                                       | Integration tests for current snapshot, bounded history, replacement, and isolation                                                                                                  |
| PostgreSQL V3/V4 relational path  | `backend/src/main/java/com/example/backend/repository/PostgresFinancialRecordSnapshotAdapter.java` | `backend/src/main/resources/db/migration/V3__create_financial_record_snapshot_schema.sql`, `backend/src/main/resources/db/migration/V4__add_financial_record_app_id_constraints.sql` |
| Schema state                      | Ordered migration files                                                                            | Read-only inspector output                                                                                                                                                           |
| PostgreSQL MCP/read-only role     | `scripts/setup-postgres-readonly-role.ps1`                                                         | Storage guide, MCP guide                                                                                                                                                             |
| CI jobs and dependencies          | `.github/workflows/*.yml`                                                                          | Local verification scripts                                                                                                                                                           |
| Hosted AI review workflow         | `.github/workflows/copilot-review.yml`                                                             | GitHub AI workflow guide                                                                                                                                                             |
| GitHub/Codex configuration        | Live repository settings, rulesets, installed-app permissions, and Codex approvals                 | `docs/github-codex-configuration.md`, MCP guide, GitHub AI workflow guide                                                                                                            |
| Copilot review instructions       | `.github/copilot-instructions.md`                                                                  | GitHub AI workflow guide                                                                                                                                                             |
| PR summary structure              | `.github/PULL_REQUEST_TEMPLATE.md`                                                                 | GitHub AI workflow guide                                                                                                                                                             |
| PR/failure summary packets        | `.github/workflows/*summary*.yml`                                                                  | GitHub AI workflow guide                                                                                                                                                             |
| Issue forms and implementation    | `.github/ISSUE_TEMPLATE/*.yml`                                                                     | Issue-to-implementation guide                                                                                                                                                        |
| Documentation drift workflow      | `scripts/check-documentation-drift.ps1`                                                            | Workflow and source map                                                                                                                                                              |
| Dependency update triage          | `.github/dependabot.yml`                                                                           | Dependency triage guide                                                                                                                                                              |
| Weekly maintenance/status         | `scripts/generate-engineering-status.ps1`                                                          | Maintenance workflow guide                                                                                                                                                           |
| Coverage thresholds               | Vite config and JaCoCo configuration                                                               | CI commands                                                                                                                                                                          |
| Published engineering evidence    | Coverage reports, Surefire XML, Playwright specs, security script, and hosted workflow definitions | `docs/engineering-evidence.md`, verification matrix                                                                                                                                  |
| Logs, metrics, and request IDs    | `RequestObservabilityFilter`, `application*.properties`, and `ApiSecurityConfig`                   | `frontend/src/observability/*`, API client, observability guide, focused tests                                                                                                       |
| Security gates                    | `.snyk-cli-version`, CI scan step, and security script                                             | Snyk output when authenticated                                                                                                                                                       |
| Snyk MCP/API feasibility          | `docs/snyk-integration-assessment.md`                                                              | MCP guide, official Snyk docs                                                                                                                                                        |
| MCP and connector boundaries      | `docs/mcp-integration-guide.md`                                                                    | `AGENTS.md`, GitHub state                                                                                                                                                            |
| Intentional architecture          | Current code plus accepted ADRs                                                                    | Architecture documentation                                                                                                                                                           |

## Audit Checks

### Commands and Environment

- Verify the documented working directory, shell, flags, and prerequisites.
- Confirm commands exist and defaults do not mutate data unexpectedly.
- Check environment variable names, defaults, secret handling, and whether a
  variable is required or optional.
- Distinguish PostgreSQL setup from authenticated security scans.

### API and Domain

- Compare every documented method/path with controller annotations.
- Compare field names and optionality across frontend types and backend DTOs.
- Verify persisted versus derived fields, financial calculations, and date
  policies against implementation and tests.
- Check that examples use synthetic data only.

### PostgreSQL

- Verify table names, migration order, Flyway behavior, active storage adapter,
  seeding, versioning, and expected empty tables.
- Describe V1 normalized tables as inactive historical groundwork,
  V3/V4/V6-V11 financial-record tables as active workspace persistence, and
  V2/V7 transition storage as retired by V10/V11.
- Distinguish the write-capable application role from a recommended read-only
  inspection or MCP role.

### CI and Security

- Compare documented checks with actual jobs, dependencies, events, and
  permissions.
- Confirm local equivalents include coverage gates where CI does.
- Do not label `npm audit`, missing authentication, or a skipped scan as a
  successful Snyk result.
- Identify deployment placeholders as limitations rather than working release
  infrastructure.

### Links and Ownership

- Check local file links and command paths exist.
- Identify conflicting duplicate instructions and nominate one canonical home.
- Ensure behavioral changes update the owning README, `AGENTS.md`, relevant
  operational docs, and a new ADR when an architectural decision changes.

## Finding Priorities

- **High:** A command risks secrets/personal data, corrupts state, or blocks
  setup/release.
- **Medium:** A false API, database, CI, or verification claim causes incorrect
  engineering decisions.
- **Low:** Ambiguity, stale terminology, duplication, or a broken noncritical
  link.
