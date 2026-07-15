# Production Readiness Roadmap

This roadmap tracks the code review findings that should not get lost between
feature work. The current strategy is organized by product maturity and the
application's role as a flagship portfolio project. Correctness, coherent
boundaries, and a defensible engineering story come before hosted operational
breadth.

Completed items remain historical evidence even when a later ADR deliberately
supersedes or removes the resulting implementation.

## Completed Foundations

- [x] Make Snyk fail CI on high-severity issues instead of reporting only.
- [x] Add frontend coverage thresholds so test coverage cannot quietly regress.
- [x] Add backend JaCoCo coverage reporting and verification.
- [x] Document the financials API as a versioned snapshot aggregate.
- [x] Extract projection logic from `FinancialsPage.tsx` into a pure domain
      module.
- [x] Add projection unit tests for housing reserves, debt payoff, savings transfer,
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
- [x] Add CSV import and CSV/XLSX export tooling.
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
part of the portfolio deployment phase because provider selection depends on
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
- [x] Activate the V3/V4/V6/V7/V8/V9 relational adapter as the PostgreSQL runtime financial
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
`financials.example.json` as synthetic test/demo input. Preserve explicit
backup and migration evidence until the owner confirms recovery needs are met;
Phase H will consolidate the long-term backup/restore format.

## Phase F - Make the Application Product-Quality

- [x] Add first-run onboarding from signup or sign-in through empty workspace
      initialization and the first versioned save, without synthetic seeding.
- [x] Improve navigation and dashboard entry points across the existing
      financial workflows, including a grouped compact-viewport section menu.
- [x] Complete clear empty, loading, success, conflict, and error states,
      including explicit collection empties and deliberate stale-draft reload.
- [x] Expand accessibility automation and establish repeatable manual
      assistive-technology checks.
- [x] Complete responsive and mobile behavior across financial workflows.

Phase F is complete.

## Phase G - Make Runtime Behavior Trustworthy

- [x] Carry explicit workspace identity through financial requests and Redux
      actions, then abort or ignore responses that no longer belong to the
      active account, workspace, or request generation.
- [x] Preserve edits made while a save is in flight by tracking the draft
      revision submitted with each save and accepting the returned baseline
      only when no newer local edits exist.
- [x] Add deterministic delayed-request tests for account/workspace switching
      and save-time editing, plus focused live-browser coverage where it adds
      confidence beyond the unit tests.
- [x] Retire XLSX import and its custom decompression code while preserving CSV
      restore and XLSX export.

## Phase H - Subtract Duplicate Mutation and Recovery Paths

ADR 0016 keeps the versioned financial workspace as the sole mutation
aggregate. Implement that decision before splitting the remaining services:

- [x] Remove public granular record and pay-period mutation endpoints and the
      controller, DTO, service, repository, relational-adapter, test, and
      documentation surface used only by those endpoints.
- [x] Select one coherent, version-checked backup and restore workflow. Prefer a
      matching JSON export/restore path unless spreadsheet editing becomes a
      demonstrated product requirement; then retire unused tabular formats and
      their custom codec.
- [ ] Retire legacy JSON/JSONB migration administration only after the owner
      confirms that personal source data is migrated, independently backed up,
      and outside the required rollback window.
- [x] Split current-snapshot loading from audit-history queries, apply history
      limits in SQL, append only new audit events, and batch relational record
      writes where whole-snapshot replacement remains.
- [x] Replace post-install mutation of dependency source files and document the
      reason, advisory, introduction date, and removal condition for each
      framework-managed dependency override.

## Phase I - Decompose the Surviving Backend

- [x] Separate current-workspace queries, versioned commands, financial
      calculations, and API response mapping only after duplicate mutation and
      import/export responsibilities are removed.
- [x] Replace servlet-aware application services with a framework-neutral
      current-workspace boundary and domain/application exceptions mapped at
      the HTTP boundary.
- [x] Make workspace initialization return its created aggregate deliberately
      instead of coordinating a write followed by an unrelated service read.
- [x] Preserve structured API problem status and request identity through the
      frontend error path without parsing presentation messages.

## Phase J - Consolidate Frontend Draft and Route State

- [x] Introduce one canonical financial draft with committed baseline,
      snapshot version, local revision, pending removal, reducer commands, and
      derived selectors.
- [x] Keep domain-focused hooks as selector/command facades where they improve
      readability instead of maintaining independent draft state machines.
- [x] Extract workspace loading/onboarding/failure composition and workflow
      feedback from `FinancialsPage` after the draft boundary is stable.

## Phase K - Generalize the Financial Planning Product

- [x] Replace name-based projection identity with configurable workspace roles
      that reference financial records independently of mutable display names.
- [x] Support the pay cadence and date/time-zone rules required by the target
      household-planning audience before hosting the frontend and backend in
      different environments.
- [x] Remove institution-specific and personal assumptions from product copy
      while keeping the scope explicit: household cash-flow and pay-period
      planning, not accounting, financial advice, or transaction reconciliation.

## Phase L - Build Portfolio Evidence

- [x] Reframe the project name, Maven metadata, and root README around the
      problem, intended users, solution, architecture, tradeoffs, and verified
      behavior rather than a generic reference application.
- [x] Create synthetic screenshots or a short walkthrough, a concise
      architecture diagram, and a STAR case study that explains the migration
      from local JSON to authenticated PostgreSQL workspaces.
- [x] Report unit coverage, PostgreSQL integration coverage, browser coverage,
      accessibility evidence, and security gates with clear qualifications.
- [x] Audit and consolidate documentation so ADRs, architecture maps, selected
      source, and test narratives form a trustworthy public corpus.

Phase L is complete.

## Phase M - Deploy a Portfolio-Grade Demo

- [ ] Select hosting and managed PostgreSQL providers with approved privacy,
      retention, cost, and shutdown policies. Use only synthetic demonstration
      data in the public environment.
- [ ] Configure HTTPS, secrets, least-privilege database roles, migrations,
      health verification, rollback, automated backups, and a proved restore
      path.
- [ ] Export safe logs, metrics, and browser errors with basic alerting while
      excluding financial contents. Defer enterprise-scale incident machinery
      that does not improve the portfolio demonstration.
- [ ] Provide a repeatable demo-account reset so reviewers can explore the
      product without encountering another visitor's state.

## Phase N - Add the Portfolio Chatbot and Future Product Enhancements

- [ ] Build the static portfolio and citation-first chatbot only after the
      public documentation corpus is accurate. Ingest approved public files,
      exclude secrets and personal data, and cite repository/file context in
      every architecture answer.
- [ ] Prioritize new planning, reporting, forecasting, and collaboration
      features only after the core workflow, portfolio evidence, and hosted
      privacy/recovery boundaries are proven.

## Current Priority

Next highest-value items:

1. Confirm whether personal legacy JSON/JSONB sources are migrated,
   independently backed up, and outside the rollback window before retiring
   migration administration.
2. Decide when to begin Phase M provider and deployment-boundary evaluation;
   keep the application local-first until those decisions are approved.
