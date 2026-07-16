# Pay Period Planner Agent Guide

## Scope

Use these instructions for all work in this repository. Read the closest
subproject README before changing frontend, backend, database, or CI behavior.

## Architecture

- `frontend/`: React 19, TypeScript, Redux Toolkit, Vite, and Vitest.
- `backend/`: Java 21, Spring Boot 4, Maven, Spring JDBC, and JaCoCo.
- `backend/data/financials.example.json`: committed synthetic test/demo input.
- `backend/data/*.local.json` plus `.bak`/`.tmp` siblings: ignored obsolete
  local artifacts; never commit them or treat them as supported input.
- `backend/src/main/resources/db/migration/`: PostgreSQL schema migrations.
- `.github/workflows/ci.yml`: build, test, coverage, and Snyk pipeline.
- `.github/workflows/codeql.yml`: hosted Java and JavaScript/TypeScript code
  scanning.
- `.github/workflows/dependency-review.yml`: pull-request dependency-diff
  vulnerability gate.
- `scripts/`: repeatable local setup, verification, and inspection commands.

The application edits and saves one financial snapshot aggregate in the
V3/V4/V6-V11 PostgreSQL `financial_record_*` relational tables, scoped to the
workspace selected from a V5 account session. The V1 normalized tables are
inactive historical groundwork and are not the runtime persistence path. V10
removes the retired V2 JSONB store, V7 migration ledger, and source linkage;
V11 removes unowned compatibility rows and requires workspace ownership. The
runtime loads current snapshots separately from SQL-limited audit history and
appends one audit event while batch-writing each aggregate replacement. The
snapshot stores typed projection roles for its rent bill, rent-reserve asset
account, and primary-paycheck income-summary item; mutable labels are not role
identity. It also stores pay cadence and IANA planning time zone so one
zone-derived date drives current-period calculations. The PostgreSQL financial
API requires `WORKSPACE` session authority, enforces CSRF
on writes, and isolates every operation by current database-derived membership.
The HTTP adapter resolves that membership through the framework-neutral
`CurrentWorkspace` application port, and `ApiExceptionHandler` alone maps
financial application exceptions to HTTP statuses.
Workspace initialization returns its successfully created aggregate for shared
response presentation without a follow-up persistence read.
The frontend uses signup, sign-in, session recovery, sign-out, CSRF bootstrap,
and explicit workspace selection against that PostgreSQL API. Basic
authentication remains only for protected metrics routes.

## Directory Ownership

- `frontend/src/features/financials/`: UI, draft editing, projections, and
  client-side financial presentation.
- `frontend/src/api/`: HTTP client behavior and endpoint integration.
- `backend/src/main/java/.../api/` and `dto/`: HTTP boundaries and snapshot
  contract.
- `backend/src/main/java/.../domain/financials/`: financial record types and
  the saved snapshot aggregate used inside the backend.
- `backend/src/main/java/.../service/`: current-workspace query and command
  boundaries, validation, normalization, calculations, API mapping, and domain
  orchestration.
- `backend/src/main/java/.../repository/`: PostgreSQL persistence and storage
  boundaries.
- `backend/src/main/resources/db/migration/`: additive schema history.
- `scripts/`: deterministic local setup, inspection, security, and verification.
- `.github/workflows/`: hosted checks, security scans, and deploy placeholders.
- `docs/` and root/subproject READMEs: architecture decisions and operator
  documentation; update them when behavior or commands change.

Use `docs/architecture-map.md` for runtime boundaries, data flow, and
change-routing guidance. Use `docs/domain-glossary.md` for project-specific
financial, application-state, and persistence terminology. Use
`docs/api-contract.md` for HTTP methods, payloads, derived fields, and errors.
Use `docs/README.md` for canonical documentation ownership and the approved
public portfolio corpus. Validate `docs/public-corpus.json` before adding files
to portfolio retrieval or chatbot ingestion.
Use `docs/database-storage-guide.md` for storage, database roles,
migrations, inspection, backup, and recovery boundaries.
Use `docs/verification-matrix.md` to choose targeted and completion checks by
change surface.
Use `docs/engineering-evidence.md` for the latest portfolio-facing test,
coverage, browser, accessibility, and security results and their limitations.
Use `docs/accessibility-verification.md` for automated WCAG browser audits,
manual screen-reader and keyboard checks, and safe accessibility evidence.
Use `docs/responsive-verification.md` for supported viewport behavior,
contained table scrolling, and manual responsive checks.
Use `docs/known-limitations.md` before changing an intentional simplification;
update its status and add an ADR when a limitation is resolved architecturally.
Use `docs/troubleshooting-decision-tree.md` for non-destructive, symptom-driven
diagnosis and escalation evidence.
Use `docs/mcp-integration-guide.md` for GitHub MCP, external connector,
hosted-action, PostgreSQL MCP, browser/Playwright, Snyk MCP/API,
branch-cleanup, and future integration boundaries.
Use `docs/github-ai-workflows.md` for hosted Copilot review request behavior,
severity-categorized AI review comments, PR/failure summary packets, and AI
review boundaries.
Use `docs/issue-to-implementation-workflow.md` when turning a GitHub issue into
a branch, implementation, verification report, or draft PR.

## PostgreSQL Setup

- The application always uses PostgreSQL with `DATABASE_URL`, `DATABASE_USERNAME`, and
  `DATABASE_PASSWORD`, with local defaults documented in `backend/README.md`.
- Initialize local PostgreSQL with
  `.\scripts\bootstrap-local.ps1 -IncludePostgres` or
  `.\scripts\setup-local-postgres.ps1`, then start it with
  `.\scripts\start-backend.ps1`.
- Flyway is the only migration authority. Setup delegates versioned DDL to
  `.\scripts\migrate-postgres.ps1`; never execute migration files directly.
  Keep migrations additive and never edit an applied migration.
- Investigation is read-only. Setup and migration scripts are mutations and
  require an explicit setup task or user approval.

## Working Rules

- Preserve the API snapshot contract unless the task explicitly changes it.
- Treat financial values and local database contents as sensitive.
- Use only read-only SQL for investigation. Ask before changing local data.
- Never expose tokens, database passwords, or other secrets.
- Keep migration history additive. Do not edit an applied migration.
- Keep frontend draft/save and backend persistence behavior aligned.
- Add focused tests for changed behavior and regression tests for bug fixes.
- Do not silently normalize or discard persisted financial records; destructive
  retirement requires an explicit owner decision and an additive migration.

## Coding Conventions

- Follow `.editorconfig`, Prettier, ESLint, TypeScript strict checks, Spotless,
  and SortPom; run formatters only on files in scope.
- Keep React components focused, immutable Redux updates explicit, and
  financial/date calculations in named helpers with tests.
- Keep controllers thin, DTOs at the API boundary, financial record models in
  the domain package, business rules in services, and storage details behind
  repository interfaces.
- Prefer existing naming and package patterns. Avoid broad cleanup mixed with a
  behavioral change.
- Never log secrets or full personal financial snapshots.
- Do not add install lifecycle scripts that modify `node_modules`. Keep any
  transitive security override scoped, lockfile-backed, documented in
  `docs/dependency-update-triage.md`, and covered by a read-only assertion with
  an explicit removal condition.
- Use GitHub MCP or `gh` for scoped repository reads when live PR, CI, issue, or
  branch state is required. External writes require explicit user intent.
- Use `financial_app_reader` or another dedicated read-only PostgreSQL role for
  MCP/reporting tools; never connect external tools with the application owner.
- Keep Snyk MCP/API setup user-scoped or CI-scoped. Do not commit Snyk tokens,
  personal MCP config, or policy ignores without an explicit owner decision.

## Commands

From the repository root:

```powershell
.\scripts\check-environment.ps1
.\scripts\bootstrap-local.ps1
.\scripts\verify-local.ps1
.\scripts\run-security-checks.ps1
.\scripts\inspect-postgres.ps1
.\scripts\migrate-postgres.ps1
.\scripts\setup-postgres-readonly-role.ps1
.\scripts\run-browser-checks.ps1
.\scripts\capture-portfolio-evidence.ps1
.\scripts\export-financial-snapshot.ps1 -OutputPath <outside-repo-path.json>
.\scripts\restore-financial-snapshot.ps1 -InputPath <outside-repo-path.json> -ConfirmRestore
.\scripts\write-coverage-summary.ps1
.\scripts\check-public-corpus.ps1
.\scripts\check-documentation-drift.ps1
.\scripts\triage-dependency-updates.ps1
.\scripts\generate-engineering-status.ps1
npm --prefix frontend run check:dependency-compat
```

Run `.\scripts\run-security-checks.ps1` only when authenticated tooling and
network access are available. CI is authoritative for GitHub-hosted behavior.
`.snyk-cli-version` is the single source of truth for the Snyk CLI version used
locally and in CI. Update that file intentionally, install the matching CLI,
and rerun authenticated scans when upgrading Snyk; do not use `snyk@latest` in
repository security gates.
All required CI jobs must pass; do not bypass checks. Treat high-severity Snyk
findings as blocking unless the repository owner explicitly accepts and records
the risk. A missing `SNYK_TOKEN`, unavailable service, or unauthenticated scan
is not a pass. For Dependabot-triggered workflows, GitHub Actions may withhold
repository secrets; in that case, treat the internal Snyk CLI step as skipped
and rely on the external Snyk PR check or an owner-approved manual rerun for
security evidence.
CodeQL is a hosted Java and JavaScript/TypeScript scan; inspect uploaded alerts
in GitHub rather than treating workflow completion as proof that no alerts
exist. Dependency Review blocks pull requests that introduce high- or
critical-severity vulnerabilities in runtime, development, or unknown scopes.
Both checks require GitHub's repository security features and cannot be
reproduced fully by the local verification scripts.
Copilot review requests are assistive and non-blocking. Copilot comments must
be validated against code, tests, docs, and data-safety rules before action.
PR and CI failure summary packets are context only; they do not prove hosted
checks passed or identify root cause without log/code verification.
GitHub issues define implementation scope only when objective, acceptance
criteria, affected area, verification, and data-safety state are clear. Ask for
clarification before coding if those are missing.
Documentation-drift packets are deterministic hints. Verify drift against the
source map and executable sources before changing docs or posting findings.
Dependency triage and weekly engineering-status packets are advisory. Do not
auto-merge dependency updates or mutate GitHub issues, labels, runs, settings,
or security policy from a packet alone.

## Financial Data Policy

- `backend/data/financials.example.json` is synthetic mock data and is the only
  financial dataset intended for source control, fixtures, screenshots, and
  shared bug reports.
- Ignored local JSON, PostgreSQL contents, exports, logs, and screenshots may
  contain personal data. Do not commit, paste, summarize, or send them to
  external services.
- JSON snapshot exports are application backup/restore artifacts. Store them
  outside the repository unless the data is synthetic and
  `-AllowRepositoryPath` is intentional.
- Prefer metadata, counts, keys, and redacted/minimal reproductions when
  diagnosing personal data. Ask before reading full local records.
- Never replace personal local data with mock data or seed over it implicitly.
  Backups and destructive operations require explicit approval.

## Intentional Limitations

- The financial API uses database-backed sessions and relational workspace
  ownership, and the browser verifies cross-user isolation. Production
  deployment infrastructure and external API integrations remain incomplete.
- Full-snapshot saves use optimistic version checks and are the sole supported
  financial mutation boundary.
- PostgreSQL persists active runtime data in the V3/V4/V6-V11
  `financial_record_*` tables with relational audit history and optimistic
  workspace writes. V1 normalized tables remain inactive; V2 JSONB and its
  transition administration are retired by V10/V11.
- PostgreSQL setup is explicit, while local and hosted completion gates require
  isolated PostgreSQL integration tests.
- The deploy workflow is a manual placeholder, not a production release path.

## Repository Skills

- `$bootstrap-pay-period-planner`: check prerequisites, install dependencies, and
  optionally initialize PostgreSQL.
- `$verify-pay-period-planner`: run the complete local verification suite,
  including required isolated PostgreSQL integration tests.
- `$audit-pay-period-planner-docs`: compare documentation claims with executable
  repository behavior and report drift.
- `$prepare-pay-period-planner-pr`: verify, document, commit, push, and prepare a draft
  pull request when those publishing actions are requested.
- `$maintain-pay-period-planner`: implement and verify application changes.
- `$review-pay-period-planner`: conduct a findings-first code review.
- `$inspect-financial-postgres`: inspect local PostgreSQL state read-only.
- `$triage-github-ci`: diagnose GitHub Actions and Snyk failures.

## Project Agents

- `frontend-reviewer`: read-only React, TypeScript, Redux, API integration,
  financial/date behavior, and frontend test review.
- `backend-api-reviewer`: read-only Spring controller, DTO, validation,
  service-rule, aggregate-semantics, and backend test review.
- `postgresql-reviewer`: read-only migration, relational storage, seed/version,
  privilege, transaction, parity, and integration-test review.
- `security-dependency-reviewer`: read-only dependency, lockfile,
  GitHub Actions, Snyk/audit, secrets, and supply-chain review.
- `accessibility-reviewer`: read-only semantic HTML, labels, keyboard, focus,
  modal, announcement, visual-state, and accessibility-test review.
- `test-coverage-reviewer`: read-only test depth, assertion quality, branch
  execution, coverage threshold, and CI-equivalence review.
- `documentation-reviewer`: read-only documentation drift, command accuracy,
  source-of-truth, data-safety, links, and ADR-alignment review.
- `architecture-reviewer`: read-only architectural boundary, aggregate
  ownership, cross-layer contract, profile, ADR, and limitation review.
- `review-coordinator`: read-only consolidation of specialist findings,
  duplicate removal, severity normalization, ownership, and review gaps.

## Completion

Report changed files, checks run, skipped checks, and remaining risk. Do not
claim Snyk passed unless an authenticated Snyk scan completed successfully.
