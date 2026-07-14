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
- [x] Add the initial PostgreSQL snapshot store integration test.
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

- [x] Add CodeQL and GitHub dependency review.
- [x] Pin or otherwise standardize the Snyk CLI/action used by CI so scan
      failures are reproducible.

Phase C is complete.

## Phase D - Make It Operable

- [x] Add a vendor-neutral observability foundation with structured logs,
      request IDs, frontend error containment, and safe application metrics.

Phase D is complete for local operational readiness. Hosted telemetry remains
part of the production-operations phase because provider selection depends on
hosting, privacy, and retention decisions.

## Phase E - Consolidate Persistence, Identity, and Ownership

ADR 0014 defines one PostgreSQL-only target architecture. Complete these items
in order without changing current startup documentation until the runtime
transition is verified:

- [x] Split financial draft state, actions, validation, coordination, and tests
      by domain feature while preserving the versioned snapshot save contract.
- [x] Decide the target persistence and ownership architecture in ADR 0014.
- [x] Establish Flyway as the single migration authority before adding the
      identity and ownership schema.
- [x] Add user, workspace, workspace-membership, and server-managed session
      tables through additive migrations.
- [x] Replace global active-snapshot constraints with workspace-scoped
      ownership and update every repository operation accordingly.
- [x] Add PostgreSQL-backed signup, sign-in, sign-out, session recovery, secure
      credential hashing, default workspace creation, and database-derived
      workspace principals without granting them global financial access.
- [x] Replace frontend Basic authentication with signup, sign-in, session
      recovery, sign-out, per-mutation CSRF proof, and workspace selection.
- [x] Activate the V3/V4/V6/V7 relational adapter as the PostgreSQL runtime financial
      store while preserving optimistic versioning and the snapshot API.
- [x] Add live PostgreSQL browser cross-user isolation coverage for distinct
      account sessions, workspace snapshots, saves, sign-out, and recovery.
- [x] Add an explicit, backed-up JSON/JSONB-to-workspace migration workflow
      with metadata-only verification and a documented rollback path.
- [x] Make PostgreSQL-backed tests required locally and in hosted CI.
- [x] Make PostgreSQL the only startup path, then remove the JSON runtime
      profile, JSON snapshot store, automatic personal-JSON seeding, and
      duplicate startup scripts and instructions.

Do not silently seed or migrate personal financial data. Keep
`financials.example.json` as synthetic test/demo input and keep explicit
JSON/CSV/XLSX backup formats after JSON runtime persistence is retired.

## Phase F - Make the Application Product-Quality

- [ ] Improve onboarding, navigation, dashboards, and financial workflows.
- [ ] Complete clear empty, loading, success, conflict, and error states.
- [ ] Expand accessibility automation and manual assistive-technology checks.
- [ ] Complete responsive and mobile behavior across financial workflows.

## Phase G - Complete Production Operations

- [ ] Select hosting and telemetry providers with approved privacy and data
      retention policies.
- [ ] Export logs, metrics, and browser errors to centralized telemetry without
      exposing financial data.
- [ ] Configure deployment, health verification, rollback, automated backups,
      restore drills, alerting, and incident/recovery procedures.

## Phase H - Add Financial-Product Enhancements

- [ ] Prioritize new planning, reporting, forecasting, and collaboration
      features only after identity, ownership, privacy, and recovery boundaries
      are proven.

## Current Priority

Next highest-value items:

1. Improve product UX and accessibility, including onboarding, workflow states,
   responsive behavior, and assistive-technology coverage.
2. Complete hosting, telemetry,
   backups, retention, and incident recovery.
3. Add planning and reporting features only after the PostgreSQL-only runtime
   and operational recovery path are authoritative.
