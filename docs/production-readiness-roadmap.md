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

## Phase A - Make It Real

- [ ] Fix display/edit support for persisted non-primary income summary source
      rows so future income sources are not hidden by derived rows.
- [ ] Decide whether the V1 normalized PostgreSQL tables should become active
      relational persistence or be replaced by a cleaner migration path.
- [ ] Add granular PostgreSQL CRUD persistence for financial records.
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

- [ ] Expand Playwright from synthetic smoke coverage to live-backend
      end-to-end tests for core financial workflows.
- [ ] Add audit/history support for financial changes and projections.
- [ ] Add PR coverage summaries.
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

1. Add snapshot versioning or optimistic concurrency so two sessions cannot
   silently overwrite each other.
2. Fix display/edit handling for persisted non-primary income summary rows.
3. Decide the PostgreSQL relational schema path before adding more migrations.
4. Add export/backup support for the current financial snapshot.
5. Add Playwright end-to-end coverage for load, edit, save, refresh, and delete
   confirmation workflows.
6. Add recurring payday generation so yearly income calendars do not need to be
   manually entered.
