# End-to-End App Agent Guide

## Scope

Use these instructions for all work in this repository. Read the closest
subproject README before changing frontend, backend, database, or CI behavior.

## Architecture

- `frontend/`: React 19, TypeScript, Redux Toolkit, Vite, and Vitest.
- `backend/`: Java 21, Spring Boot 4, Maven, Spring JDBC, and JaCoCo.
- `backend/data/financials.example.json`: committed mock seed data.
- `backend/data/financials.local.json`: ignored local data; never commit it.
- `backend/src/main/resources/db/migration/`: PostgreSQL schema migrations.
- `.github/workflows/ci.yml`: GitHub Actions and Snyk pipeline.
- `scripts/`: repeatable local setup, verification, and inspection commands.

The application edits and saves one financial snapshot aggregate. The default
profile stores local JSON. The `postgres` profile stores that aggregate in
`financial_snapshot_document.snapshot_json`. The V1 normalized tables are
schema groundwork and are not the active persistence path.

## Directory Ownership

- `frontend/src/features/financials/`: UI, draft editing, projections, and
  client-side financial presentation.
- `frontend/src/api/`: HTTP client behavior and endpoint integration.
- `backend/src/main/java/.../api/` and `dto/`: HTTP boundaries and snapshot
  contract.
- `backend/src/main/java/.../service/`: validation, calculations, and domain
  orchestration.
- `backend/src/main/java/.../repository/`: JSON and PostgreSQL persistence.
- `backend/src/main/resources/db/migration/`: additive schema history.
- `scripts/`: deterministic local setup, inspection, security, and verification.
- `.github/workflows/`: hosted checks, security scans, and deploy placeholders.
- `docs/` and root/subproject READMEs: architecture decisions and operator
  documentation; update them when behavior or commands change.

Use `docs/architecture-map.md` for runtime boundaries, data flow, and
change-routing guidance. Use `docs/domain-glossary.md` for project-specific
financial, application-state, and persistence terminology. Use
`docs/api-contract.md` for HTTP methods, payloads, derived fields, and errors.
Use `docs/database-storage-guide.md` for profile storage, database roles,
migrations, inspection, backup, and recovery boundaries.
Use `docs/verification-matrix.md` to choose targeted and completion checks by
change surface.
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

## PostgreSQL Profiles and Setup

- The default Spring profile is `json`; it requires no database and writes to
  ignored `backend/data/financials.local.json`.
- The `postgres` profile uses `DATABASE_URL`, `DATABASE_USERNAME`, and
  `DATABASE_PASSWORD`, with local defaults documented in `backend/README.md`.
- Initialize local PostgreSQL with
  `.\scripts\bootstrap-local.ps1 -IncludePostgres` or
  `.\scripts\setup-local-postgres.ps1`, then start it with
  `.\scripts\start-backend-postgres.ps1`.
- Flyway owns runtime migration order. Keep migrations additive and never edit
  an applied migration.
- Investigation is read-only. Setup and migration scripts are mutations and
  require an explicit setup task or user approval.

## Working Rules

- Preserve the API snapshot contract unless the task explicitly changes it.
- Treat financial values and local database contents as sensitive.
- Use only read-only SQL for investigation. Ask before changing local data.
- Never expose tokens, database passwords, or other secrets.
- Keep migrations additive. Do not edit an applied migration.
- Keep frontend draft/save and backend persistence behavior aligned.
- Add focused tests for changed behavior and regression tests for bug fixes.
- Do not silently normalize or discard persisted financial records.

## Coding Conventions

- Follow `.editorconfig`, Prettier, ESLint, TypeScript strict checks, Spotless,
  and SortPom; run formatters only on files in scope.
- Keep React components focused, immutable Redux updates explicit, and
  financial/date calculations in named helpers with tests.
- Keep controllers thin, DTOs at the API boundary, business rules in services,
  and storage details behind repository interfaces.
- Prefer existing naming and package patterns. Avoid broad cleanup mixed with a
  behavioral change.
- Never log secrets or full personal financial snapshots.
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
.\scripts\verify-local.ps1 -IncludePostgres
.\scripts\run-security-checks.ps1
.\scripts\inspect-postgres.ps1
.\scripts\setup-postgres-readonly-role.ps1
.\scripts\run-browser-checks.ps1
.\scripts\check-documentation-drift.ps1
.\scripts\triage-dependency-updates.ps1
.\scripts\generate-engineering-status.ps1
```

Run `.\scripts\run-security-checks.ps1` only when authenticated tooling and
network access are available. CI is authoritative for GitHub-hosted behavior.
All required CI jobs must pass; do not bypass checks. Treat high-severity Snyk
findings as blocking unless the repository owner explicitly accepts and records
the risk. A missing `SNYK_TOKEN`, unavailable service, or unauthenticated scan
is not a pass. For Dependabot-triggered workflows, GitHub Actions may withhold
repository secrets; in that case, treat the internal Snyk CLI step as skipped
and rely on the external Snyk PR check or an owner-approved manual rerun for
security evidence.
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
- `backend/data/financials.local.json`, PostgreSQL contents, exports, logs, and
  screenshots may contain personal data. Do not commit, paste, summarize, or
  send them to external services.
- Prefer metadata, counts, keys, and redacted/minimal reproductions when
  diagnosing personal data. Ask before reading full local records.
- Never replace personal local data with mock data, seed over it, or migrate it
  implicitly. Backups and destructive operations require explicit approval.

## Intentional Limitations

- There is no authentication, authorization, production deployment
  infrastructure, external API integration, or multi-user conflict handling.
- Saves replace one complete snapshot; concurrency is last-write-wins.
- PostgreSQL persists one JSONB snapshot. V1 normalized tables are inactive
  groundwork and may remain empty.
- JSON is the default local profile. PostgreSQL setup and integration tests are
  opt-in.
- The deploy workflow is a manual placeholder, not a production release path.

## Repository Skills

- `$bootstrap-end-to-end-app`: check prerequisites, install dependencies, and
  optionally initialize PostgreSQL.
- `$verify-end-to-end-app`: run the complete local verification suite and
  optional isolated PostgreSQL smoke test.
- `$audit-end-to-end-docs`: compare documentation claims with executable
  repository behavior and report drift.
- `$prepare-end-to-end-pr`: verify, document, commit, push, and prepare a draft
  pull request when those publishing actions are requested.
- `$maintain-end-to-end-app`: implement and verify application changes.
- `$review-end-to-end-app`: conduct a findings-first code review.
- `$inspect-financial-postgres`: inspect local PostgreSQL state read-only.
- `$triage-github-ci`: diagnose GitHub Actions and Snyk failures.

## Project Agents

- `frontend-reviewer`: read-only React, TypeScript, Redux, API integration,
  financial/date behavior, and frontend test review.
- `backend-api-reviewer`: read-only Spring controller, DTO, validation,
  service-rule, aggregate-semantics, and backend test review.
- `postgresql-reviewer`: read-only migration, JSONB storage, seed/version,
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
