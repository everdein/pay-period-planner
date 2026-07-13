# 0008 Add snapshot version concurrency control

## Status

Accepted

## Context

The financial workspace saves one aggregate snapshot. Before this decision, two
open tabs or clients could load the same data, edit independently, and let the
later save silently overwrite the earlier committed change. PostgreSQL already
had a `financial_snapshot_document.version` column, but that value was treated
as storage metadata rather than an API-level concurrency token.

The project is still single-user and local-first, so the goal is lightweight
lost-update protection for full-snapshot saves, not a collaborative merge
system.

## Decision

Expose a numeric `version` on `GET /api/v1/financials` responses and require
clients to echo it in `PUT /api/v1/financials` requests.

`FinancialsRepository` owns the current version in memory. It increments the
version before every successful persisted mutation, including granular bill or
pay-period changes and full-snapshot replacement. A full-snapshot replacement
checks the client-supplied expected version before mutating the aggregate. A
mismatch raises a domain conflict that the service maps to `409 Conflict`.

Local JSON snapshots persist the version in the document and older JSON without
a version loads as version 1. PostgreSQL loads the active row version into the
aggregate and writes the repository-assigned next version back to both the row
metadata and JSON document.

## Consequences

- Full-snapshot saves no longer silently overwrite newer committed snapshots.
- Frontend and external clients must keep the latest response version and reload
  before retrying after `409 Conflict`.
- Existing local JSON fixtures without `version` remain readable because the
  repository data record defaults missing/non-positive versions to 1.
- Granular endpoints still do not accept client-supplied aggregate versions;
  they increment the snapshot version so stale full-snapshot drafts are
  rejected.
- This does not provide multi-user identity, field-level merges, audit history,
  or conflict resolution UI.
