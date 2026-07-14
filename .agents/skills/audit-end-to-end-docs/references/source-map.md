# Documentation Source Map

Use the most authoritative source available for each claim. Documentation may
summarize these sources but must not override them.

| Claim                             | Primary source                                                                                     | Useful corroboration                                                                                                                                                                 |
| --------------------------------- | -------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Repository scope and rules        | `AGENTS.md`                                                                                        | Root and subproject READMEs                                                                                                                                                          |
| Runtime/dependency versions       | `pom.xml`, `package.json`, lockfiles                                                               | CI setup actions                                                                                                                                                                     |
| npm commands                      | Root and frontend `package.json`                                                                   | PowerShell scripts, CI                                                                                                                                                               |
| Local setup and verification      | `scripts/*.ps1`                                                                                    | `AGENTS.md`, README                                                                                                                                                                  |
| Frontend port/proxy/test behavior | `frontend/vite.config.ts`                                                                          | Frontend README                                                                                                                                                                      |
| Browser workflow smoke tests      | `frontend/playwright.config.ts`                                                                    | `scripts/run-browser-checks.ps1`                                                                                                                                                     |
| API routes and status behavior    | Controllers and exception handler                                                                  | DTOs, controller tests                                                                                                                                                               |
| API payload shape                 | Request/response DTOs and frontend types                                                           | API client tests                                                                                                                                                                     |
| CSV/XLSX snapshot import/export   | `backend/src/main/java/com/example/backend/service/FinancialSnapshotTabularCodec.java`             | Controller and service tests, `scripts/export-financial-snapshot.ps1`, `scripts/import-financial-snapshot.ps1`                                                                       |
| Backend financial record model    | `backend/src/main/java/com/example/backend/domain/financials/*`                                    | Service and repository tests                                                                                                                                                         |
| Business calculations             | Service and frontend helper implementations                                                        | Focused tests                                                                                                                                                                        |
| Default/profile behavior          | `application*.properties`                                                                          | Startup scripts                                                                                                                                                                      |
| JSON paths and seed behavior      | Repository implementation/configuration                                                            | Backend README                                                                                                                                                                       |
| PostgreSQL active storage         | Store implementation and migrations                                                                | Integration test                                                                                                                                                                     |
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
- Distinguish JSON-only setup from PostgreSQL setup and authenticated security
  scans.

### API and Domain

- Compare every documented method/path with controller annotations.
- Compare field names and optionality across frontend types and backend DTOs.
- Verify persisted versus derived fields, financial calculations, and date
  policies against implementation and tests.
- Check that examples use synthetic data only.

### PostgreSQL

- Verify table names, migration order, Flyway behavior, active storage adapter,
  seeding, versioning, and expected empty tables.
- Describe V1 normalized tables as inactive historical groundwork, V2 JSONB as
  a legacy migration source, and V3/V4/V6/V7 financial-record tables as active
  workspace runtime persistence.
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
