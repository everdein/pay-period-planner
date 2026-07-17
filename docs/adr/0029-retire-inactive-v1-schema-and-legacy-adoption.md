# 0029 Retire inactive V1 schema and legacy adoption

## Status

Accepted

## Context

V1 created eight normalized financial tables before the current aggregate,
workspace ownership, projection roles, planning settings, and audit model were
stable. ADR 0009 kept those tables inactive, and the runtime later moved to the
workspace-owned `financial_record_*` schema. The V1 objects remained visible in
otherwise healthy databases and could mislead operators into querying the
wrong tables.

The local setup and migration scripts also retained signature-checked baseline
modes for pre-Flyway V2 and V4 databases. ADR 0028 records the owner's decision
to recover from an independent source instead of preserving obsolete
application stores. Keeping those baseline modes after that decision adds a
second setup path with no supported recovery use case.

## Decision

Add Flyway V12 to drop the eight inactive V1 tables after their dependent
tables are removed. Do not modify or delete V1 through V11; they remain the
immutable migration history required to build and upgrade a database.

Remove the legacy baseline and adoption switches from the setup and migration
scripts. A non-empty schema without `flyway_schema_history` must fail closed and
receive an explicit additive recovery plan or be replaced when disposable.

Keep inactive rows in `financial_record_snapshot`. They are current-schema
version history used by optimistic concurrency, audit, and restore behavior,
not retired V1 storage.

## Consequences

- Existing V1 rows and tables are irreversibly removed when V12 runs. The owner
  approved that loss because no application data is recovered through V1.
- Current `financial_record_*`, identity, workspace, membership, session, and
  audit rows are unchanged by V12.
- Fresh databases still execute the complete Flyway chain, including historical
  creation followed by explicit retirement.
- Operators see one relational financial table family instead of two
  look-alike schemas.
- Unsupported pre-Flyway databases no longer receive an automatic baseline.
