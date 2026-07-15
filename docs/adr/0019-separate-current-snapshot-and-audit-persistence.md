# 0019 Separate current snapshot and audit persistence

## Status

Accepted - implemented 2026-07-15

## Context

The request-scoped financial repository loaded the active snapshot and every
audit event for the selected workspace as one `FinancialsData` envelope. An
ordinary snapshot read therefore became more expensive as history grew, even
though only the history endpoint needed those events. The endpoint applied its
requested limit after the complete history had reached application memory.

Replacement writes also passed the complete in-memory audit list back to the
PostgreSQL adapter, which filtered it to find new events. Each child record in
the replacement snapshot was then inserted through an individual JDBC update.
Those behaviors coupled current state to historical state and added avoidable
query and write round trips.

## Decision

Separate current-snapshot, audit-history, and replacement operations at the
runtime store boundary.

- Load the current `FinancialSnapshot` lazily on the first aggregate operation.
  An audit-history request does not load current snapshot records.
- Query audit events independently, newest first, and apply the requested limit
  in PostgreSQL.
- Pass exactly one newly created `FinancialAuditEvent` with each optimistic
  aggregate replacement. Under the existing workspace-row lock, PostgreSQL
  allocates its next workspace event ID and inserts only that event beside the
  new immutable snapshot.
- Batch inserts within each relational child-record family in groups of up to
  100 while keeping the complete replacement and audit append in one
  transaction.
- Retain historical snapshots and events. Keep `FinancialsData` as the explicit
  legacy JSON/JSONB migration envelope; it is no longer the runtime
  current-state store contract.

No schema migration is required. This changes query and transaction behavior
over the existing V3/V4/V6/V7 relational model.

## Consequences

- Current snapshot query cost no longer grows with audit history, and the
  history endpoint transfers at most its validated limit.
- A replacement cannot accidentally reinsert the caller's complete history;
  one successful version transition creates one new runtime audit row.
- Batch writes reduce JDBC round trips while preserving aggregate replacement,
  optimistic concurrency, workspace isolation, and rollback behavior.
- Whole-snapshot replacement still copies every current child record into a new
  immutable snapshot. Further decomposition should be driven by measured scale
  or product behavior rather than adding a second mutation model.
- Audit events remain coarse and do not yet record field-level diffs,
  authenticated actor identity, or request origin.
