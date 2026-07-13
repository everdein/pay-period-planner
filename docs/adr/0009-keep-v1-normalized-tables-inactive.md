# 0009 Keep V1 normalized tables inactive

## Status

Accepted

## Context

`V1__create_financials_schema.sql` introduced normalized tables for financial
snapshots and records before the application had a stable relational domain
model. `V2__create_financial_snapshot_document.sql` then became the active
PostgreSQL persistence path by storing the complete aggregate in
`financial_snapshot_document.snapshot_json`.

The V1 tables now create ambiguity: their names look authoritative, but the
application does not read, write, validate, backfill, or migrate data through
them. Activating them as-is would require dual-write behavior, data movement,
ID mapping, parity tests, and conflict semantics around a schema that was
created before later decisions about full-snapshot saves, versioning, protected
anchors, and frontend draft behavior.

## Decision

Do not activate the V1 normalized tables as the application persistence path.

Keep `financial_snapshot_document` authoritative for the current PostgreSQL
profile. Treat the V1 tables as inert historical groundwork that may remain
empty in healthy databases. Do not dual-write, backfill, or query them from the
runtime adapter.

Any future relational persistence work should use a new additive migration path
instead of retrofitting V1. That future path should follow clearer domain model
and API decisions, include explicit JSON-to-relational migration/backfill
steps, preserve snapshot export/backup recovery, and prove parity with the
existing JSONB document behavior before becoming active. ADR 0010 establishes
the V3 `financial_record_*` migration and adapter path for that follow-up work.

## Consequences

- Operators and agents have one authoritative PostgreSQL data source today:
  `financial_snapshot_document`.
- The misleading V1 tables remain visible, but documentation, inspectors, and
  troubleshooting guidance identify them as inactive.
- Future relational persistence is not blocked, but it must be designed as a
  deliberate V3+ migration/adapter effort instead of silently adopting the V1
  schema.
- Existing local databases do not need destructive cleanup or backfill.
- Export/backup support becomes more important before any future data-shape
  migration.
