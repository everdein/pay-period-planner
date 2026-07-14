---
name: verify-end-to-end-app
description: Run and interpret the complete end-to-end-app local verification suite, including spelling, TypeScript checks, ESLint, frontend tests and coverage, frontend build, Java formatting, backend tests and JaCoCo coverage, and required isolated PostgreSQL integration tests. Use before declaring work complete, preparing a pull request, reproducing CI failures, or assessing repository health.
---

# Verify End-to-End App

1. Read `AGENTS.md` and inspect `git status` so verification results are tied to
   the current worktree.
2. Set `DATABASE_USERNAME` and `DATABASE_PASSWORD` for the dedicated local
   application role. The verifier checks PostgreSQL availability before
   running any quality gates.
3. Run `.\scripts\verify-local.ps1`. It runs the database-independent checks
   first, then the `postgres-integration` Maven profile against isolated test
   schemas.
4. For focused PostgreSQL iteration, set
   `RUN_POSTGRES_INTEGRATION_TESTS=true` and run
   `.\mvnw.cmd -B test "--activate-profiles=postgres-integration"
   "-Djacoco.skip=true"` from
   `backend/`. Never point it at a database where the test schema names are
   owned by another application.
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
