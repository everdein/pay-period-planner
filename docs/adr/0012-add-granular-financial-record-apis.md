# 0012 Add granular financial record APIs

## Status

Accepted

## Context

The application already supports full-snapshot load/save under
`/api/v1/financials` and bill-specific create/update/delete endpoints. The UI
still uses the full snapshot as its primary draft/save boundary, but external
clients and future UI work need consistent API access to the other financial
record families.

PostgreSQL runtime persistence remains JSONB-backed. ADR 0011 added relational
adapter CRUD support, but switching the active runtime store is a separate
decision.

## Decision

Add backward-compatible v1 granular CRUD endpoints for:

- annual withdrawals
- asset accounts
- debt accounts
- income summary items
- income events
- important dates

Use the existing `FinancialsRepository` aggregate and snapshot store for these
endpoints so JSON and PostgreSQL runtime profiles continue to behave the same.
Requests omit `id`, creates assign a positive ID, updates preserve the path ID,
and missing update/delete IDs return `404`.

Asset-account CRUD responses are flat and include category key/label because a
single asset account cannot otherwise identify its parent category. Snapshot
responses remain grouped by `assetCategories`.

Do not add client-supplied snapshot version checks to these granular endpoints
yet; keep that limitation explicit until broader multi-client concurrency is
designed.

## Consequences

- Non-bill financial records can now be managed without replacing the complete
  snapshot.
- The current UI can continue using full-snapshot save while future clients can
  use granular endpoints.
- Direct granular writes still mutate the single aggregate immediately and can
  race with full-snapshot saves; `LIM-004` remains open for future concurrency
  hardening.
- PostgreSQL relational runtime activation remains separate from API exposure.
