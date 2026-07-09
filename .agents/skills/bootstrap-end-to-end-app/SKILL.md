---
name: bootstrap-end-to-end-app
description: Prepare the end-to-end-app repository for local development by checking Java, Node.js, npm, PostgreSQL, Git, and environment variables; installing locked dependencies; and optionally initializing the local PostgreSQL database. Use after cloning, when onboarding a workstation, when prerequisites may be broken, or when local startup fails before application code runs.
---

# Bootstrap End-to-End App

1. Read `AGENTS.md`, the root `README.md`, and `backend/README.md`.
2. Ask whether PostgreSQL is needed only when the request does not make the
   desired persistence profile clear. Default to the JSON profile for the
   cheapest usable bootstrap.
3. Run `.\scripts\check-environment.ps1`. Add `-IncludePostgres` when preparing
   the PostgreSQL profile.
4. Explain missing prerequisites without changing machine-wide configuration.
   Do not install runtimes, change execution policy, or edit global environment
   variables without explicit approval.
5. Run `.\scripts\bootstrap-local.ps1`. Add `-IncludePostgres` only after
   confirming that database initialization is intended because it creates or
   updates a local role, database, and schema.
6. If PostgreSQL setup is included, run `.\scripts\inspect-postgres.ps1` after
   setup and report connectivity, schema presence, and snapshot metadata
   without printing snapshot contents.
7. Report detected tool versions, dependency installation results, selected
   persistence profile, skipped optional work, and the exact next start
   command.

Never expose database passwords or personal financial data. Do not treat
Codex's bundled runtime as evidence that the user's terminal is configured.
