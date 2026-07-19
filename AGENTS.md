# Pay Period Planner Agent Guide

## Scope

Use these instructions for all repository work. Read the closest subproject
README and inspect the implementation before changing behavior.

## Repository Map

- `frontend/`: React, TypeScript, Redux Toolkit, Vite, Vitest, and Playwright.
- `backend/`: Java, Spring Boot, Maven, Spring JDBC, and PostgreSQL.
- `backend/src/main/resources/db/migration/`: additive Flyway migrations.
- `scripts/`: local setup, verification, inspection, backup, and maintenance.
- `.github/workflows/`: hosted CI, security, review, and maintenance checks.
- `docs/`: current architecture, contracts, operations, evidence, and ADRs.

Route changes with [docs/architecture-map.md](docs/architecture-map.md). Use
[docs/README.md](docs/README.md) to find the canonical document for a claim;
do not copy detailed architecture or command inventories into this file.

## Invariants

- Preserve the versioned full-snapshot API and frontend draft/save workflow
  unless the task explicitly changes that contract.
- PostgreSQL `financial_record_*` tables are the active financial persistence
  path. Flyway V12 removed the retired V1 schema; V10 and V11 removed legacy
  JSONB transition storage and unowned compatibility data.
- Keep controllers at the HTTP boundary, business rules in services, domain
  types in `domain/financials`, and storage behind repository interfaces.
- Keep frontend draft state, API types, backend DTOs, persistence, backup, and
  projection behavior aligned.
- Use stable record IDs for projection roles. Labels are presentation, not
  identity.
- Use the stored pay cadence and IANA planning time zone for period logic.
- Add focused regression tests for defects and boundary tests for contract,
  date, money, authorization, and persistence changes.
- Keep migrations additive. Never edit an applied migration.
- Avoid unrelated refactors and generated-output churn.

## Data Safety

- Treat local PostgreSQL data, exports, logs, traces, screenshots, and ignored
  JSON files as personal financial data.
- Use `backend/data/financials.example.json` and other synthetic values for
  tests, screenshots, documentation, and shared reports.
- Never expose credentials, tokens, database rows, or full financial
  snapshots. Prefer metadata, counts, and minimal synthetic reproductions.
- Use read-only SQL for investigation. Setup, restore, migration, and data
  changes require explicit user intent.
- Store exports outside the repository unless synthetic repository output is
  explicitly requested.

## Verification

Use [docs/verification-matrix.md](docs/verification-matrix.md) to select focused
checks. The default completion gate is:

```powershell
.\scripts\verify-local.ps1
```

It includes spelling, corpus validation, frontend checks, backend checks, and
isolated PostgreSQL integration tests. Run browser, accessibility, responsive,
or authenticated security checks when the changed surface requires them.
Report every relevant check as passed, failed, or skipped; a missing credential
or unavailable service is not a pass.

## Documentation And Decisions

- Update the canonical owner only when behavior or an operational claim
  changes. A source edit does not automatically require an architecture update.
- Add an ADR for a meaningful new or superseding architectural decision. Do
  not rewrite accepted ADR history.
- Keep current behavior in architecture, contract, storage, verification, and
  limitation documents; keep historical tradeoffs in ADRs.
- Treat documentation-drift and maintenance packets as advisory evidence.
  Verify their claims against executable sources before editing documentation.
- Follow [docs/automation-operations.md](docs/automation-operations.md) for
  GitHub, connector, browser, security, scheduled-maintenance, and external
  write boundaries.

## Repository Skills

- `$bootstrap-pay-period-planner`: check prerequisites and initialize local
  development dependencies.
- `$verify-pay-period-planner`: run and interpret the complete local gate.
- `$inspect-financial-postgres`: inspect PostgreSQL state without mutation.
- `$triage-github-ci`: diagnose hosted CI and Snyk failures.
- `$audit-pay-period-planner-docs`: compare documentation with executable
  repository behavior.
- `$review-pay-period-planner`: perform a findings-first cross-layer review.

These skills should contain only project-specific workflow or domain knowledge.
Ordinary implementation and GitHub publication use the agent's standard
engineering and publishing workflows.

## Project Reviewers

- `frontend-reviewer`: React, state, API integration, accessibility,
  responsive behavior, and frontend tests.
- `backend-api-reviewer`: Spring/API boundaries, domain rules, PostgreSQL,
  migrations, architecture, and backend/integration tests.
- `security-dependency-reviewer`: dependencies, workflows, credentials,
  permissions, and supply-chain risk.
- `documentation-reviewer`: current documentation, ADR alignment, commands,
  links, evidence, and source-of-truth ownership.

Use only reviewers relevant to the change. The parent agent owns deduplication,
severity normalization, and the final report.

## Completion

Report changed files, checks run, skipped checks, documentation or migration
impact, and remaining risk. Do not claim hosted or authenticated checks passed
without their actual result.
