---
name: maintain-pay-period-planner
description: Implement, refactor, debug, or verify changes in the Pay Period Planner React, Spring Boot, JSON, and PostgreSQL codebase. Use for feature work, bug fixes, dependency changes, API changes, persistence changes, and full-stack maintenance in this repository.
---

# Maintain Pay Period Planner

1. Read `AGENTS.md` and the README for every affected subproject.
2. Inspect the current implementation and tests before choosing a design.
3. Preserve the single-snapshot API and draft/save workflow unless the request
   explicitly changes them.
4. Keep changes within the owning layer:
   - React presentation and draft logic in `frontend/src/features/financials/`.
   - HTTP integration in `frontend/src/api/`.
   - API boundaries in the backend `api/` package and DTOs.
   - financial record and aggregate types in the backend `domain/financials/`
     package.
   - business rules in the backend `service/` package.
   - storage behavior in the backend `repository/` package.
5. Add focused regression tests for defects and contract tests for boundary
   changes.
6. Run targeted checks while iterating, then run
   `.\scripts\verify-local.ps1`.
7. The default verifier requires PostgreSQL and runs the isolated integration
   suite. Use the `postgres-integration` Maven profile directly only for
   focused database iteration.
8. Report skipped checks and residual risks explicitly.

Never commit local financial data or expose credentials. Use additive database
migrations and read-only SQL during investigation.
