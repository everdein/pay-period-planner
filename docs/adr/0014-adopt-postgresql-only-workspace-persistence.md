# 0014 Adopt PostgreSQL-only workspace persistence

## Status

Accepted - implemented 2026-07-14

## Context

The application currently supports two runtime persistence paths: file-backed
JSON by default and a PostgreSQL profile backed by one active JSONB document.
ADR 0007 introduced that dual model as a temporary migration strategy while a
clean relational path was designed.

ADR 0010 and ADR 0011 subsequently added and tested the V3/V4
`financial_record_*` relational adapter. V5-V7 then added identity, workspace
ownership, explicit migration, and audit history. The relational adapter is now
active under the PostgreSQL profile; JSON remains the temporary default profile
until client authorization, required PostgreSQL verification, and recovery
evidence complete the transition.

The next product phase needs one production-like startup path, durable account
and session state, explicit ownership, and a safe migration path for existing
local financial data.

## Decision

Adopt PostgreSQL as the only application runtime persistence target when the
identity and ownership phase is complete.

- Add user, workspace, workspace-membership, and server-managed session data
  through additive migrations. A new user receives a default personal
  workspace; financial data belongs to a workspace rather than directly to a
  browser session or global application user.
- Scope active financial snapshots and every repository/API operation to an
  authenticated workspace membership. Replace global active-snapshot
  constraints with workspace-scoped constraints through new migrations; never
  edit an applied migration.
- Activate the V3/V4/V6/V7 relational financial-record adapter as the single runtime
  persistence path. Preserve the versioned full-snapshot API and frontend
  draft/save contract initially by assembling the aggregate at the service
  boundary.
- Replace the shared local Basic-auth identity with signup, sign-in, sign-out,
  session recovery, secure password hashing, and server-managed browser
  sessions. Authorization must derive workspace access from the authenticated
  user and must include cross-user isolation tests.
- Retire `financials.local.json`, the `json` runtime profile, the JSON snapshot
  store, automatic personal-JSON seeding, and duplicate startup paths only
  after migration and recovery checks pass.
- Keep `financials.example.json` as synthetic test/demo input. Keep JSON, CSV,
  and XLSX export/import formats as explicit backup and migration artifacts;
  retiring JSON persistence does not retire JSON API payloads or exports.
- Migrate existing local data only through an explicit, backed-up workflow that
  names the destination user/workspace and verifies counts and versions without
  exposing financial values. Never silently import personal local JSON during
  account creation or PostgreSQL startup.
- Make PostgreSQL-backed verification part of normal local and hosted checks,
  and establish Flyway as the single migration authority before adding the new
  schema.

Implementation outcome (2026-07-14): PostgreSQL is the only runtime persistence
and startup path. V5-V7 provide account/session ownership, relational workspace
storage, and explicit migration history. The financial API always authorizes
`WORKSPACE` sessions, resolves a sole membership or explicit `X-Workspace-ID`,
enforces CSRF on writes, and reads and writes only relational workspace
snapshots. The JSON runtime store, profile switch, implicit personal-data seed,
and duplicate startup instructions are removed. Operator Basic auth remains
limited to migration-admin and metrics routes. Required local and hosted
PostgreSQL tests plus browser-level cross-user isolation verify the result.

## Consequences

- Local development and deployed environments converge on one persistence and
  startup model.
- Account, ownership, authorization, concurrency, and audit behavior have one
  durable database boundary instead of profile-specific implementations.
- PostgreSQL becomes a required local and CI dependency, so bootstrap,
  disposable test databases, migration evidence, and failure messages must be
  reliable and ergonomic.
- Existing personal JSON and JSONB data require deliberate backup, migration,
  rollback, and metadata-only verification before old stores are removed.
- The application keeps its aggregate API while gaining relational ownership
  and record storage; a later API version can adopt more granular concurrency
  without blocking this runtime transition.
- ADR 0002 and the JSON-fallback portion of ADR 0007 remain historical records
  of the current migration path. They are superseded as target architecture
  only when this ADR's runtime-transition criteria are implemented and
  verified.
