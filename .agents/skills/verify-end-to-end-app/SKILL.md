---
name: verify-end-to-end-app
description: Run and interpret the complete end-to-end-app local verification suite, including spelling, TypeScript checks, ESLint, frontend tests and coverage, frontend build, Java formatting, backend tests and JaCoCo coverage, and optional isolated PostgreSQL smoke tests. Use before declaring work complete, preparing a pull request, reproducing CI failures, or assessing repository health.
---

# Verify End-to-End App

1. Read `AGENTS.md` and inspect `git status` so verification results are tied to
   the current worktree.
2. Run `.\scripts\check-environment.ps1`; add `-IncludePostgres` only when the
   PostgreSQL smoke test is requested or relevant.
3. Run `.\scripts\verify-local.ps1` for the complete database-independent
   suite.
4. Add `-IncludePostgres` when SQL, migrations, PostgreSQL configuration,
   serialization, or the PostgreSQL store changed. This option uses the
   integration test's isolated schema; never point it at a database where that
   schema name is owned by another application.
5. Run `.\scripts\run-security-checks.ps1` separately only when authenticated
   Snyk and network access are available. A skipped or unauthenticated scan is
   not a pass.
6. On failure, preserve the first actionable error, identify the failing
   command and layer, and distinguish code defects from missing prerequisites
   or external-service failures. Do not hide failures by weakening thresholds.
7. Report every check as passed, failed, or skipped. Include coverage threshold
   failures, PostgreSQL scope, and residual CI-only risk.

Do not print secrets or financial records. Do not initialize PostgreSQL or
change data as part of ordinary verification.
