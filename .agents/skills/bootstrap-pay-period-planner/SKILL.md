---
name: bootstrap-pay-period-planner
description: Prepare the Pay Period Planner repository for local development by checking Java, Node.js, npm, PostgreSQL, Git, and environment variables; installing locked dependencies; and optionally initializing the local PostgreSQL database. Use after cloning, when onboarding a workstation, when prerequisites may be broken, or when local startup fails before application code runs.
---

# Bootstrap Pay Period Planner

1. Read `AGENTS.md`, the root `README.md`, and `backend/README.md`.
2. Treat PostgreSQL as the only runtime persistence target. Ask whether local
   database initialization is intended before running any mutating setup.
3. Run `.\scripts\check-environment.ps1 -IncludePostgres`.
4. Explain missing prerequisites without changing machine-wide configuration.
   Do not install runtimes, change execution policy, or edit global environment
   variables without explicit approval.
5. Run `.\scripts\bootstrap-local.ps1`. Add `-IncludePostgres` only after
   confirming that database initialization is intended because it creates or
   updates a local role, database, and schema.
6. If PostgreSQL setup is included, run `.\scripts\inspect-postgres.ps1` after
   setup and report connectivity, schema presence, and snapshot metadata
   without printing snapshot contents.
7. Report detected tool versions, dependency installation results, PostgreSQL
   setup state, skipped optional work, and the exact next start command.

Never expose database passwords or personal financial data. Do not treat
Codex's bundled runtime as evidence that the user's terminal is configured.
