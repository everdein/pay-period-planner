# Production Readiness Roadmap

This roadmap tracks the code review findings that should not get lost between
feature work. The current strategy is organized by product maturity rather than
by isolated technical concern.

## Completed Foundations

- [x] Make Snyk fail CI on high-severity issues instead of reporting only.
- [x] Add frontend coverage thresholds so test coverage cannot quietly regress.
- [x] Add backend JaCoCo coverage reporting and verification.
- [x] Document the financials API as a versioned snapshot aggregate.
- [x] Extract projection logic from `FinancialsPage.tsx` into a pure domain
      module.
- [x] Add projection unit tests for rent reserves, debt payoff, HYSA transfer,
      annual withdrawals, next pay period math, and date edge cases.
- [x] Replace fragile label guessing with protected projection anchors for rent,
      rent reserve, and primary paycheck rows.
- [x] Replace backend floating-point money values with `BigDecimal`.
- [x] Add DTO validation with `@Valid` and Bean Validation annotations.
- [x] Centralize API error handling with consistent problem responses.
- [x] Rename API routes to snapshot-oriented endpoints under `/api/v1/financials`.
- [x] Make local JSON writes atomic and keep a last-known-good backup.
- [x] Add controller and persistence tests.
- [x] Improve modal accessibility with focus management, Escape handling, and
      focus return.

## Phase A - Make It Real

- [ ] Add PostgreSQL persistence.
- [ ] Introduce a clearer backend domain model around financial records.
- [ ] Add CRUD APIs for financial records beyond the existing bill endpoints.
- [ ] Add recurring payday generation.
- [ ] Add CSV/XLSX import and export tooling.

## Phase B - Make It Safe

- [ ] Add snapshot versioning or optimistic concurrency.
- [ ] Harden and complete validation/error handling across all future endpoints.
- [ ] Add authentication and authorization for all financial APIs.
- [ ] Add production configuration guardrails for CORS, actuator exposure,
      logging, request size limits, profile-specific settings, and secure
      defaults.

## Phase C - Make It Impressive

- [ ] Add Playwright end-to-end tests for core financial workflows.
- [ ] Add audit/history support for financial changes and projections.
- [ ] Add PR coverage summaries.
- [ ] Add CodeQL and GitHub dependency review.

## Future Scaling

- [ ] Add observability with structured logs, request IDs, frontend error
      reporting, and basic metrics.
- [ ] Split frontend draft state by domain feature.
- [ ] Add multi-user support after auth and database ownership foundations are
      mature.

## Current Priority

Start Phase A with a narrow PostgreSQL foundation:

1. Add database dependencies and profile-based configuration.
2. Add migrations for the current financial snapshot/domain tables.
3. Keep the existing JSON repository available as a local fallback until the
   database-backed repository is ready.
4. Move one read/write path at a time so the current UI keeps working during
   the transition.
