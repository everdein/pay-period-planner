# Known Limitations Register

This register records deliberate simplifications and current operational gaps.
It prevents accidental assumptions; it does not waive defects within supported
behavior. Revisit an entry when its trigger occurs and record architectural
changes in a new ADR.

## Identity, Security, and Privacy

### LIM-001 — Single local Basic-auth application user

- **Status:** Partially resolved for isolated local development
- **Impact:** Financial APIs require HTTP Basic credentials, but the application
  still has one global user, one global workspace, no tenant ownership, no
  password rotation workflow, and no production session management. Basic auth
  credentials are only appropriate for trusted local development unless served
  over a hardened TLS deployment.
- **Current mitigation:** Protect every `/api/v1/financials/**` endpoint with
  the `FINANCIALS` role, keep the backend local, override default credentials
  with `FINANCIALS_API_USERNAME` and `FINANCIALS_API_PASSWORD` when needed, and
  do not expose port `8080`.
- **Revisit when:** Any shared, remote, hosted, or multi-user access is planned.

### LIM-002 — No user or tenant isolation

- **Status:** Accepted
- **Impact:** The application has one global active workspace and cannot
  separate records by person, household, or tenant.
- **Current mitigation:** One local operator and one storage target.
- **Revisit when:** Authentication, collaboration, or multiple workspaces are
  introduced.

### LIM-003 — Dependency scanning is not a complete security program

- **Status:** Accepted development baseline
- **Impact:** Snyk and npm audit do not cover authorization, business-logic
  abuse, secret scanning, dynamic testing, infrastructure, or manual threat
  modeling.
- **Current mitigation:** Least-privilege CI permissions, review checklists,
  high-severity Snyk gate, and local data-handling rules.
- **Revisit when:** Preparing any deployment or handling data beyond the local
  developer.

## API and Concurrency

### LIM-004 — Granular endpoints do not carry snapshot versions

- **Status:** Partially resolved for full-snapshot saves
- **Impact:** `PUT /api/v1/financials` rejects stale versions with `409`, but
  granular record and pay-period endpoints still mutate immediately without a
  client-supplied aggregate version.
- **Current mitigation:** The current browser workspace primarily uses the
  versioned full-snapshot save boundary; ADR 0012 records that granular
  endpoints remain direct API utilities.
- **Revisit when:** Multiple tabs/users, remote access, background writes,
  synchronization, or broader granular editing are supported.

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

- **Status:** Intentional current invariant
- **Impact:** Renaming `Rent`, `Rent Reserve`, or `Net Income` / `Bi-Weekly`
  changes projection behavior. Normalization may add or rename zero-valued
  anchor records.
- **Current mitigation:** Case-insensitive matching, documented glossary, and
  focused projection tests.
- **Revisit when:** Users need configurable anchors, multiple rent obligations,
  or localization.

### LIM-008 — Local-date and system-time-zone dependence

- **Status:** Accepted
- **Impact:** The backend uses its system default time zone and the frontend
  uses browser-local dates. Different zones near midnight can disagree about
  current periods or statuses.
- **Current mitigation:** Local frontend/backend usage on one workstation and
  date-only API fields.
- **Revisit when:** Frontend and backend run in different zones or scheduled
  processing is introduced.

### LIM-009 — Simplified financial model

- **Status:** Intentional
- **Impact:** The workspace tracks balances and planning flags, not transaction
  history, reconciliation, interest, amortization, taxes, market prices,
  currencies, or financial-account connectivity.
- **Current mitigation:** UI and glossary describe values as planning inputs.
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

### LIM-011 — JSON is a single-process local store

- **Status:** Intentional default
- **Impact:** File-backed persistence is unsuitable for concurrent writers,
  remote filesystems, or production availability.
- **Current mitigation:** Atomic replacement when supported and one `.bak`
  recovery copy.
- **Revisit when:** More than one backend process or production persistence is
  required.

### LIM-012 — PostgreSQL stores one JSONB document

- **Status:** Accepted transitional design
- **Impact:** The active runtime path still rewrites the aggregate JSONB
  document. Relational per-record writes are implemented in the inactive V3/V4
  adapter path but are not used by the service yet.
- **Current mitigation:** One active document, version metadata, serialization
  parity tests, simple load/save behavior, and a tested but inactive V3/V4
  relational adapter path with granular CRUD operations.
- **Revisit when:** Reporting, granular concurrency, audit history, relational
  integrity, or large snapshots are needed. Use ADR 0010 and ADR 0011's
  V3/V4 relational path, not the inactive V1 tables as-is.

### LIM-013 — Normalized V1 tables are inactive

- **Status:** Intentional historical groundwork; path decided in ADR 0009
- **Impact:** Their presence can mislead operators into expecting data; they may
  remain empty while the application is healthy.
- **Current mitigation:** Storage guide, inspector, and architecture map
  identify `financial_snapshot_document` as authoritative, identify V3/V4 as
  the future runtime relational path, and prohibit dual-write/backfill through
  V1.
- **Revisit when:** Planning a production schema cleanup or activating the
  V3/V4 adapter in the runtime service.

### LIM-014 — Local setup and runtime have dual migration paths

- **Status:** Known development limitation
- **Impact:** The setup script applies V1/V2/V3/V4 SQL directly, while the
  runtime enables Flyway. A database can contain schema objects without
  `flyway_schema_history`, weakening migration-state evidence.
- **Current mitigation:** Inspect both object presence and Flyway history; keep
  migrations additive.
- **Revisit when:** Before adding more migrations or treating PostgreSQL as a
  production target. Establish one migration authority.

### LIM-015 — Broad local application-role privileges

- **Status:** Accepted for local setup only
- **Impact:** `financial_app_user` owns the development database and can create
  and mutate schema objects; it is unsafe for read-only integrations.
- **Current mitigation:** Use it only for the backend and use a separate
  read-only role for MCP/reporting.
- **Revisit when:** Configuring any shared or production database.

### LIM-016 — No automated backup schedule or profile migration

- **Status:** Partially mitigated
- **Impact:** The app can export the saved snapshot as JSON, CSV, or XLSX and
  can restore from the CSV/XLSX tabular format, but there is still no automated
  backup schedule, PostgreSQL dump flow, or verified cross-profile migration
  workflow.
- **Current mitigation:** Manual `GET /api/v1/financials/export*` downloads,
  explicit `POST /api/v1/financials/import/{csv,xlsx}` restores, PowerShell
  helpers that avoid printing financial contents, operator backups before risky
  changes, and metadata-only verification afterward.
- **Revisit when:** Personal data becomes irreplaceable, migrations recur, or a
  deployment is planned.

### LIM-017 — Empty PostgreSQL can seed from personal JSON

- **Status:** Intentional but sensitive
- **Impact:** First PostgreSQL-backed startup is mutating and may copy
  `financials.local.json` into the database.
- **Current mitigation:** Explicit profile startup, documented seed order, and
  local-only data custody.
- **Revisit when:** Databases are shared, automated, or remotely provisioned.

## Testing and Delivery

### LIM-018 — PostgreSQL smoke tests are not in hosted CI

- **Status:** Known gap
- **Impact:** CI can pass while PostgreSQL-specific integration behavior is
  broken.
- **Current mitigation:** Required local isolated-schema test for persistence
  changes.
- **Revisit when:** CI receives an ephemeral PostgreSQL service or Testcontainers
  strategy.

### LIM-019 — Browser workflow coverage is smoke-level

- **Status:** Partially mitigated
- **Impact:** The Playwright smoke test now proves Vite startup, proxying to a
  live JSON-profile backend, browser navigation, draft editing, full-snapshot
  save, refresh persistence, delete confirmation, and post-delete persistence.
  It still does not cover every tab, PostgreSQL-backed browser workflows,
  visual regression, authentication, or assistive-technology behavior.
- **Current mitigation:** Live-backend `scripts/run-browser-checks.ps1`,
  Testing Library coverage, API/service tests, and manual browser verification
  for UI changes.
- **Revisit when:** PostgreSQL browser parity, visual regression, or broader
  release confidence is required.

### LIM-020 — Accessibility automation is partial

- **Status:** Known gap
- **Impact:** JSX accessibility linting is limited and some rules are warnings;
  there is no automated browser audit or assistive-technology test.
- **Current mitigation:** Review checklist, semantic components, interaction
  tests, and manual keyboard/focus checks.
- **Revisit when:** Browser testing is added or the application is prepared for
  broader use.

### LIM-021 — Snyk CLI version is not pinned in CI

- **Status:** Known reproducibility gap
- **Impact:** A new global CLI release can change project discovery, output, or
  failure behavior without a repository change.
- **Current mitigation:** Hosted high-severity gate, CI triage workflow, local
  security script, and documented Snyk MCP/API boundaries.
- **Revisit when:** The scan job is next modified or a Snyk API automation is
  added; pin or otherwise standardize the scanner.

### LIM-022 — Deployment is a placeholder

- **Status:** Intentional
- **Impact:** `workflow_dispatch` proves job orchestration only; it does not
  build an environment, deploy, migrate, roll back, or verify health.
- **Current mitigation:** Do not describe the workflow as a release path.
- **Revisit when:** A concrete hosting target and operational owner exist.

### LIM-023 — No production observability or incident workflow

- **Status:** Intentional until deployment exists
- **Impact:** There are no centralized logs, metrics, traces, alerts,
  dashboards, retention policy, or incident response integration.
- **Current mitigation:** Local logs, test output, and a minimal actuator
  surface.
- **Revisit when:** A persistent shared environment is introduced.

### LIM-024 — Request-size guard depends on declared content length

- **Status:** Accepted operational guardrail
- **Impact:** The backend rejects requests above `FINANCIALS_MAX_REQUEST_BYTES`
  when `Content-Length` is known. Streaming/chunked requests without a declared
  length should still be limited by a production reverse proxy or gateway.
- **Current mitigation:** Local request-size filter, API tests, and documented
  production edge requirement.
- **Revisit when:** A concrete production hosting target is selected.

## Maintaining This Register

- Add an entry when accepting a meaningful limitation.
- Link a defect to an existing entry only when it is truly outside supported
  behavior.
- Remove or supersede an entry only after implementation, verification, and
  documentation are complete.
- Use a new ADR when resolution changes an accepted architectural decision.
- Keep `AGENTS.md`, the architecture map, and production-readiness roadmap
  consistent with this register.
