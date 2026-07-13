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
- [x] Add PostgreSQL snapshot persistence.
- [x] Add opt-in PostgreSQL snapshot store integration test.
- [x] Add documented PostgreSQL backend startup helper.
- [x] Add snapshot versioning and optimistic concurrency for full-snapshot
      saves.
- [x] Fix display/edit support for persisted non-primary income summary source
      rows.
- [x] Decide the PostgreSQL relational schema path: keep V1 normalized tables
      inactive and use a future additive migration path.
- [x] Add JSON export/download support for the saved financial snapshot.
- [x] Expand Playwright from synthetic mocked coverage to live-backend
      end-to-end coverage for load, edit, save, refresh, and delete
      confirmation.
- [x] Add recurring payday generation for yearly income calendars.
- [x] Introduce a clearer backend domain model around financial records.
- [x] Add a clean relational PostgreSQL migration/adapter path for financial
      records.
- [x] Add granular PostgreSQL CRUD persistence for financial records.
- [x] Add CRUD APIs for financial records beyond the existing bill endpoints.
- [x] Add CSV/XLSX import and export tooling.
- [x] Harden and complete validation/error handling across financial endpoints.
- [x] Add authentication and authorization for all financial APIs.
- [x] Add production configuration guardrails for CORS, actuator exposure,
      logging, request size limits, profile-specific settings, and secure
      defaults.
- [x] Add audit/history support for financial changes and projections.
- [x] Add PR coverage summaries.

## Phase A - Make It Real

Phase A is complete.

## Phase B - Make It Safe

Phase B is complete.

## Phase C - Make It Impressive

- [ ] Add CodeQL and GitHub dependency review.
- [ ] Pin or otherwise standardize the Snyk CLI/action used by CI so scan
      failures are reproducible.

## Future Scaling

- [ ] Add observability with structured logs, request IDs, frontend error
      reporting, and basic metrics.
- [ ] Split frontend draft state by domain feature.
- [ ] Add multi-user support after auth and database ownership foundations are
      mature.

## Current Priority

Next highest-value items:

1. Add CodeQL and GitHub dependency review.
2. Pin or otherwise standardize the Snyk CLI/action used by CI.
