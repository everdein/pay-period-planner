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

## Working Rules

- Preserve the API snapshot contract unless the task explicitly changes it.
- Treat financial values and local database contents as sensitive.
- Use only read-only SQL for investigation. Ask before changing local data.
- Never expose tokens, database passwords, or other secrets.
- Keep migrations additive. Do not edit an applied migration.
- Keep frontend draft/save and backend persistence behavior aligned.
- Add focused tests for changed behavior and regression tests for bug fixes.
- Do not silently normalize or discard persisted financial records.

## Commands

From the repository root:

```powershell
.\scripts\check-environment.ps1
.\scripts\bootstrap-local.ps1
.\scripts\verify.ps1
.\scripts\verify.ps1 -IncludePostgres
.\scripts\inspect-postgres.ps1
```

## Repository Skills

- `$maintain-end-to-end-app`: implement and verify application changes.
- `$review-end-to-end-app`: conduct a findings-first code review.
- `$inspect-financial-postgres`: inspect local PostgreSQL state read-only.
- `$triage-github-ci`: diagnose GitHub Actions and Snyk failures.

## Completion

Report changed files, checks run, skipped checks, and remaining risk. Do not
claim Snyk passed unless an authenticated Snyk scan completed successfully.
