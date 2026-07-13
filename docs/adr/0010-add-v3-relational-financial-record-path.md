# 0010 Add V3 relational financial record path

## Status

Accepted

## Context

ADR 0009 keeps the V1 normalized tables inactive because they were created
before the current snapshot aggregate, optimistic versioning, protected domain
anchors, export behavior, and backend domain model were stable.

The project now needs a clean PostgreSQL relational path that can be tested
without changing the active runtime persistence model. The current `postgres`
profile still uses `financial_snapshot_document.snapshot_json` as the
authoritative saved snapshot, and switching the service to granular relational
CRUD requires more API and concurrency decisions.

## Decision

Create a new additive V3 relational schema using `financial_record_*` table
names instead of retrofitting or activating the V1 tables.

Add `PostgresFinancialRecordSnapshotAdapter` as a tested adapter that can save
and load the backend `FinancialSnapshot` domain aggregate through the V3
tables. The adapter keeps one active relational snapshot and marks previous
relational snapshots inactive for history/parity inspection.

Do not wire the V3 adapter into the active `FinancialsService` runtime path yet.
The JSONB document store remains authoritative for the `postgres` profile until
granular persistence and API behavior are implemented deliberately.

## Consequences

- The project has a concrete, tested relational migration/adapter path for
  financial records.
- Existing local databases can add V3 without destructive cleanup or V1 table
  reuse.
- V1 remains inactive historical groundwork.
- PostgreSQL setup and inspection scripts must recognize V3 tables.
- Follow-up work can implement granular CRUD persistence against V3 rather
  than designing tables and behavior at the same time. ADR 0011 records that
  persistence-layer follow-up.
- Until runtime activation, V3 tables may be empty in a healthy database.
