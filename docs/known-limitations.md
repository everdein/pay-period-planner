# Known Limitations Register

This register records deliberate simplifications and current operational gaps.
It prevents accidental assumptions; it does not waive defects within supported
behavior. Revisit an entry when its trigger occurs and record architectural
changes in a new ADR.

## Identity, Security, and Privacy

### LIM-001 — Frontend account-session adoption

- **Status:** Resolved on 2026-07-14
- **Resolution:** The frontend uses PostgreSQL signup, sign-in, session recovery,
  sign-out, fresh CSRF proof before every mutation, and explicit workspace
  selection. It stores no password or Basic token; session identity remains in
  the server-managed `HttpOnly` cookie, and only the selected workspace ID is
  retained in browser session storage.
- **Remaining boundary:** Operator Basic authentication remains for migration
  administration and protected metrics; it grants no financial workspace access.

### LIM-002 — Browser user and workspace isolation

- **Status:** Resolved for the current PostgreSQL browser workflow on 2026-07-15
- **Resolution:** Every PostgreSQL operation resolves a database-derived
  membership and workspace-scoped snapshot. Every financial client call now
  receives an explicit workspace ID instead of reading mutable module-level
  context. Redux records the active workspace and current load, initialization,
  and save request IDs, then ignores completions outside that boundary. Page
  workflow notices also verify that their originating workspace is still
  active.
- **Verification:** Deterministic tests delay loads and saves, switch the active
  workspace, and prove that prior completions cannot replace the new snapshot.
  The live browser test separately proves cross-account database isolation,
  sign-out, recovery, and snapshot persistence.
- **Remaining boundary:** Collaboration and membership-management UX are not
  implemented.

### LIM-003 — Dependency scanning is not a complete security program

- **Status:** Accepted development baseline
- **Impact:** Snyk and npm audit do not cover authorization, business-logic
  abuse, secret scanning, dynamic testing, infrastructure, or manual threat
  modeling.
- **Current mitigation:** Least-privilege CI permissions, review checklists,
  high-severity Snyk and dependency-review gates, hosted CodeQL analysis, and
  local data-handling rules.
- **Revisit when:** Preparing any deployment or handling data beyond the local
  developer.

## API and Concurrency

### LIM-004 — Granular endpoints did not carry snapshot versions

- **Status:** Resolved by ADR 0016
- **Resolution:** The granular record and pay-period routes, request DTOs,
  client helpers, service/repository methods, and relational-adapter CRUD
  helpers were removed. `PUT /api/v1/financials` is now the sole supported
  financial mutation boundary and always requires the current aggregate
  version.

### LIM-005 — Full-snapshot replacement can delete omitted collections

- **Status:** Intentional API behavior
- **Impact:** A stale or incomplete `PUT /api/v1/financials` request replaces
  every collection and can remove records.
- **Current mitigation:** The workspace builds requests from the complete local
  draft; contract documentation and cross-layer tests.
- **Revisit when:** Partial clients, integrations, or granular collaborative
  editing are added.

### LIM-006 — No generated or machine-readable API specification

- **Status:** Accepted
- **Impact:** Frontend TypeScript types and backend DTOs can drift; external
  consumers cannot generate clients from OpenAPI.
- **Current mitigation:** Human-readable contract, controller tests, and
  cross-layer review.
- **Revisit when:** A second client, public integration, or independent release
  cadence appears.

## Domain and Calculation Model

### LIM-007 — Name-based projection anchors

- **Status:** Resolved 2026-07-15 by ADR 0026
- **Resolution:** Every versioned snapshot stores typed projection roles that
  reference stable bill, asset-account, and income-summary record IDs. Display
  labels are editable and no longer determine projection or removal behavior.
- **Compatibility boundary:** V8 backfills historical exact anchor labels.
  Legacy imports without roles may use label matching once to establish roles and
  add missing zero-value defaults without renaming existing records.

### LIM-008 — Local-date and system-time-zone dependence

- **Status:** Resolved 2026-07-15 by ADR 0027
- **Resolution:** Every versioned snapshot stores a validated IANA planning
  time zone and one of four supported pay cadences. The backend derives one
  `currentDate` from the stored zone and uses it for all current-period
  calculations. The frontend uses that returned date for the saved zone and
  performs date-only arithmetic with UTC calendar operations.
- **Compatibility boundary:** Historical rows and legacy imports without
  settings use `BIWEEKLY` and `UTC`. New browser onboarding proposes the
  browser IANA zone, which remains editable.

### LIM-009 — Simplified financial model

- **Status:** Intentional
- **Impact:** The workspace tracks balances and planning flags, not transaction
  history, reconciliation, interest, amortization, taxes, market prices,
  currencies, or financial-account connectivity.
- **Current mitigation:** UI and glossary explicitly frame values as household
  planning estimates rather than accounting, reconciliation, transfer, or
  advisory outputs.
- **Revisit when:** Product scope requires accounting-grade or advisory
  behavior.

### LIM-010 — JavaScript number boundary

- **Status:** Accepted
- **Impact:** The backend uses `BigDecimal`, while the browser consumes JSON
  numbers as JavaScript `number`; very large or highly precise values can lose
  precision.
- **Current mitigation:** Normal household-scale decimal amounts and backend
  decimal arithmetic.
- **Revisit when:** Precision guarantees, multiple currencies, or values beyond
  safe JavaScript precision are required.

## Storage and Recovery

### LIM-011 — JSON was a single-process local store

- **Status:** Resolved 2026-07-14 by ADR 0014
- **Resolution:** The JSON runtime profile and snapshot store were removed.
  PostgreSQL relational workspaces are the only active persistence path.
- **Remaining boundary:** Ignored personal JSON files may remain as explicit,
  read-only migration sources until their owners complete backup and migration.

### LIM-012 — Legacy PostgreSQL JSONB data remains during transition

- **Status:** Runtime JSONB usage resolved; legacy cleanup pending
- **Impact:** `financial_snapshot_document` remains in the schema as a backed-up
  migration source, so operators must distinguish it from active relational
  workspace data.
- **Current mitigation:** PostgreSQL runtime reads and writes only
  V3/V4/V6/V7/V8/V9 `financial_record_*` rows. Inspection and migration documentation
  label V2 JSONB as legacy, and the operator workflow leaves it untouched for
  recovery evidence.
- **Revisit when:** Reporting, granular concurrency, audit history, relational
  integrity, or large snapshots are needed. Use ADR 0010 and ADR 0011's
  V3/V4/V6/V7/V8/V9 relational path, not the inactive V1 tables as-is.
- **Remaining migration boundary:** V6 preserves any preexisting unowned
  relational snapshot without silently assigning it. New and changed rows must
  have a workspace; the explicit ownership migration must backfill legacy rows,
  validate the workspace constraint, and remove its transitional unowned index.
- **Planned resolution:** Remove the legacy JSONB adapter and eventually archive
  the V2 source table only after migration and restore evidence passes.

### LIM-013 — Normalized V1 tables are inactive

- **Status:** Intentional historical groundwork; path decided in ADR 0009
- **Impact:** Their presence can mislead operators into expecting data; they may
  remain empty while the application is healthy.
- **Current mitigation:** Storage guide, inspector, and architecture map
  identify V3/V4/V6/V7/V8/V9 as the PostgreSQL runtime path, V2 JSONB as a legacy
  migration source, and prohibit dual-write/backfill through V1.
- **Revisit when:** Planning a production schema cleanup.

### LIM-014 — Local setup and runtime have dual migration paths

- **Status:** Resolved by ADR 0015
- **Historical impact:** The setup script applied V1/V2/V3/V4 SQL directly,
  while the runtime enabled Flyway. A database could contain schema objects
  without `flyway_schema_history`, weakening migration-state evidence.
- **Resolution:** Local setup now delegates versioned DDL to
  `scripts/migrate-postgres.ps1`; runtime and integration tests use the same
  Flyway chain. Non-empty legacy schemas fail closed unless an explicit,
  signature-checked adoption mode is selected.
- **Remaining boundary:** Personal legacy databases still require a backup and
  explicit approval before adoption. Mismatched schemas require an additive
  repair plan rather than fabricated Flyway history.

### LIM-015 — Broad local application-role privileges

- **Status:** Accepted for local setup only
- **Impact:** `financial_app_user` owns the development database and can create
  and mutate schema objects; it is unsafe for read-only integrations.
- **Current mitigation:** Use it only for the backend and use a separate
  read-only role for MCP/reporting.
- **Revisit when:** Configuring any shared or production database.

### LIM-016 — No automated backup schedule or database-native restore drill

- **Status:** Partially mitigated
- **Impact:** The app can export and restore source-shaped artifacts and now has
  a verified JSON/JSONB-to-workspace transition, but there is still no automated
  backup schedule, PostgreSQL dump automation, off-host retention policy, or
  database-native restore drill.
- **Current mitigation:** Manual `GET /api/v1/financials/export` downloads,
  explicit version-checked `POST /api/v1/financials/restore` operations,
  PowerShell helpers that avoid printing financial contents, plus explicit
  `migrate-financial-snapshot-to-workspace.ps1` and
  `rollback-workspace-snapshot-migration.ps1` commands. The migration command
  requires an external backup and fingerprint before writing, preserves audit
  events, names the owner/workspace, and independently verifies metadata. The
  rollback command refuses changed snapshots.
- **Revisit when:** Personal data becomes irreplaceable, migrations recur, or a
  deployment is planned.
- **Remaining boundary:** Keep the source and external backup until relational
  runtime activation and recovery evidence pass. Production backup automation,
  retention, and restore drills remain roadmap Phase M work.

### LIM-017 — Empty PostgreSQL could seed from personal JSON

- **Status:** Resolved 2026-07-14 by ADR 0014
- **Resolution:** Startup never reads `financials.local.json` or
  `financials.example.json`. Account creation starts with an empty workspace;
  users may initialize an empty relational snapshot from pay-period dates, and
  source data enters relational storage only through the explicit, backed-up
  migration workflow.

### LIM-018 — Audit history is coarse

- **Status:** Query/write scaling resolved by ADR 0019; event detail remains
  intentionally coarse
- **Impact:** The backend records saved-change audit events with action,
  resource type/ID, version movement, timestamp, and aggregate projection
  summaries in `financial_record_audit_event`. It still does not store
  field-level before/after diffs, authenticated user identity, or request
  origin. Manual source-shaped exports do not include audit history.
- **Current mitigation:** Use `GET /api/v1/financials/history` for recent
  history and treat audit events as personal financial data. V7 stores events
  relationally beside their immutable snapshot evidence. ADR 0019 separates
  history from current-state loading, applies limits in SQL, and appends one new
  audit row per successful replacement.
- **Revisit when:** Add field-level history, actor identity, or request origin
  only if collaboration or compliance requirements justify that data and
  complexity.

## Testing and Delivery

### LIM-019 — PostgreSQL integration tests are required locally and in hosted CI

- **Status:** Resolved 2026-07-14
- **Resolution:** The default local verifier runs every `*IT` class through the
  `postgres-integration` Maven profile, and hosted CI runs the same profile
  against an ephemeral PostgreSQL service. Downstream scan jobs depend on that
  result.
- **Remaining risk:** Hosted execution remains authoritative for validating the
  GitHub Actions service-container environment.

### LIM-020 — Browser workflow coverage is smoke-level

- **Status:** Partially mitigated
- **Impact:** The Playwright smoke test proves Vite startup, PostgreSQL/Flyway
  startup in an isolated schema, signup, sign-in, sign-out, CSRF writes,
  cross-user workspace isolation, draft editing, full-snapshot save, refresh
  persistence, delete confirmation, and post-delete persistence. Focused suites
  now audit every financial section for WCAG violations and responsive layout,
  but they do not exercise in-flight account/workspace changes, delayed saves,
  every CRUD action in every section, pixel visual regression, or human
  assistive-technology evidence. Deterministic Redux and draft-hook tests cover
  stale request completions and edits made during delayed saves without adding
  production-only timing controls.
- **Current mitigation:** Live-backend `scripts/run-browser-checks.ps1`,
  deterministic schema cleanup, delayed-response unit tests, Testing Library
  coverage, API/service tests, hosted Accessibility and Responsive jobs, and
  the manual protocols in `docs/accessibility-verification.md` and
  `docs/responsive-verification.md`.
- **Revisit when:** Per-section workflow depth, visual regression, or broader
  release confidence is required.

### LIM-021 — Accessibility automation is partial

- **Status:** Resolved for the current application workflow on 2026-07-14
- **Resolution:** The live PostgreSQL browser suite now runs axe WCAG A/AA
  checks across account access, onboarding, every financial section, and the
  removal dialog. CI gates the final scan job on this focused audit. Account
  tabs and modal focus behavior also have keyboard interaction coverage.
- **Manual boundary:** `docs/accessibility-verification.md` defines the required
  screen-reader and keyboard protocol. Automated scans do not prove usable
  announcements, reading order, or workflow comprehension; record a human run
  when accessibility-critical interaction changes.

### LIM-022 — Snyk CLI version drift

- **Status:** Resolved on 2026-07-13
- **Resolution:** `.snyk-cli-version` is the repository source of truth. CI
  installs and verifies that exact npm package version, and the local security
  script rejects a different installed CLI before scanning.
- **Upgrade rule:** Change the pin intentionally, install the matching local
  CLI or direct binary, run the authenticated local security checks, and
  verify the hosted pull-request scan.

### LIM-023 — Deployment is a placeholder

- **Status:** Intentional
- **Impact:** `workflow_dispatch` proves job orchestration only; it does not
  build an environment, deploy, migrate, roll back, or verify health.
- **Current mitigation:** Do not describe the workflow as a release path.
- **Revisit when:** A concrete hosting target and operational owner exist.

### LIM-024 — No centralized production telemetry or incident workflow

- **Status:** Intentional until deployment exists
- **Impact:** The local observability foundation does not export logs, metrics,
  browser errors, or traces to a shared service. There are no alerts,
  dashboards, retention policy, or incident response integration.
- **Current mitigation:** Correlated request IDs, safe completion logs,
  production JSON formatting, protected local metrics, frontend error
  containment, and `docs/observability-guide.md`.
- **Revisit when:** A persistent shared environment is introduced.

### LIM-025 — Request-size guard depends on declared content length

- **Status:** Partially resolved on 2026-07-15
- **Impact:** The backend rejects requests above `FINANCIALS_MAX_REQUEST_BYTES`
  when `Content-Length` is known. Streaming/chunked requests without a declared
  length still require a production reverse proxy or gateway limit.
- **Resolution:** ADR 0017 first retired XLSX import and its decompression path.
  ADR 0018 then retires all CSV/XLSX application backup behavior and the custom
  tabular codec. JSON restore is parsed by the framework and still passes
  through the declared-length request guard.
- **Remaining boundary:** Enforce streaming request limits at the selected edge
  before roadmap Phase M.

### LIM-026 - Legacy ESLint packages require a scoped transitive override

- **Status:** Accepted temporary development-tool boundary in ADR 0020
- **Impact:** Five current ESLint dependency paths still use `minimatch@3.1.5`
  and its callable CommonJS brace-expansion 1.x contract.
- **Current mitigation:** `frontend/package.json` scopes those paths to
  advisory-fixed `brace-expansion@1.1.13`. A read-only compatibility assertion
  runs locally and in CI, and clean installs do not mutate `node_modules`.
- **Revisit when:** Remove the override and assertion after every checked owner
  leaves minimatch 3 or upstream dependency declarations resolve a secure,
  compatible brace-expansion release without repository intervention.

### LIM-027 - Current-workspace application boundaries remain framework-aware

- **Status:** Resolved 2026-07-15 by ADR 0022
- **Resolution:** The active PostgreSQL store and initialization service depend
  on the framework-neutral `CurrentWorkspace` port. The
  `AuthenticatedRequestWorkspace` HTTP adapter owns servlet headers and Spring
  Security principal inspection. Financial validation and stale-write handling
  raise application exceptions that `ApiExceptionHandler` maps to the existing
  Problem Detail statuses and safe details.
- **Verification:** Focused service and adapter tests invoke application
  behavior without servlet requests. Controller tests preserve the `400` and
  `409` HTTP mappings, and PostgreSQL integration tests continue to preserve
  workspace isolation.
- **Remaining boundary:** `FinancialsRepository` remains Spring request-scoped
  to isolate its mutable aggregate cache. That lifecycle choice no longer owns
  workspace selection or HTTP error semantics.

## Maintaining This Register

- Add an entry when accepting a meaningful limitation.
- Link a defect to an existing entry only when it is truly outside supported
  behavior.
- Remove or supersede an entry only after implementation, verification, and
  documentation are complete.
- Use a new ADR when resolution changes an accepted architectural decision.
- Keep `AGENTS.md`, the architecture map, and production-readiness roadmap
  consistent with this register.
