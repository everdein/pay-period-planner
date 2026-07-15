# 0011 Add granular PostgreSQL record persistence

## Status

Accepted; record-level CRUD decision superseded by ADR 0016

## Context

ADR 0010 created the V3 `financial_record_*` relational table family and a
tested adapter that can save and load a whole backend `FinancialSnapshot`.
That proved the relational shape without changing the active `postgres`
runtime path, which still stores the authoritative snapshot in
`financial_snapshot_document.snapshot_json`.

The next production-readiness step is granular persistence against the V3
tables. That persistence needs stable per-record lookup keys before HTTP CRUD
APIs or runtime service wiring are added.

## Decision

Add an additive V4 migration that creates unique `(snapshot_id, app_record_id)`
indexes for each V3 financial-record table.

Extend `PostgresFinancialRecordSnapshotAdapter` with record-level find, create,
update, and delete operations for:

- monthly bills
- annual withdrawals
- asset accounts
- debt accounts
- income summary items
- income events
- important dates

Each mutation locks the active relational snapshot row, mutates records within
that snapshot, and increments the relational snapshot version. New records get
the next positive `app_record_id` for their table within the active relational
snapshot.

Do not wire this adapter into `FinancialsService` or expose new HTTP endpoints
yet. The existing `postgres` profile remains JSONB-backed until API behavior,
runtime activation, and client concurrency semantics are decided deliberately.

## Consequences

Historical implementation note: ADR 0016 later removed the record-level
adapter methods and their tests. The additive V4 uniqueness constraints remain
applied because they preserve stable record identity and migration history.

- The V3 relational path initially supported tested internal granular CRUD
  behavior.
- V4 makes app-record lookups unambiguous without rewriting existing V3 table
  definitions.
- PostgreSQL setup and documentation must apply and describe V1/V2/V3/V4.
- `financial_record_*` tables may still be empty in a healthy application
  database because the active runtime path has not been switched.
- Future relational runtime activation can reuse the adapter instead of
  designing SQL, locking, and app-record identity at the same time.
