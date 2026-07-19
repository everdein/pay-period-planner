---
name: inspect-financial-postgres
description: Inspect and diagnose the local financial_app PostgreSQL database without modifying it, including connectivity, Flyway and schema state, exact relational table row counts, active workspace snapshot metadata, and effective privileges. Use for PostgreSQL health checks, profile troubleshooting, persistence reviews, missing-data investigations, or comparison of database state with backend behavior.
---

# Inspect Financial PostgreSQL

1. Read `AGENTS.md`, `backend/README.md`, the migrations, and
   `PostgresFinancialsSnapshotStore.java`.
2. Prefer the configured PostgreSQL MCP server when it is available and
   demonstrably read-only.
3. Otherwise run `.\scripts\inspect-postgres.ps1` for a safe baseline. Override
   connection parameters only when the target is explicitly in scope.
4. For additional SQL, begin an explicit `READ ONLY` transaction and roll it
   back. Use only `SELECT`, catalog inspection, and `EXPLAIN` without
   `ANALYZE`.
5. Confirm `transaction_read_only` is `on`. Check expected tables, Flyway
   history, exact row counts, active snapshot version/timestamps and collection
   counts, plus database/schema/table privileges.
6. Treat the relational `financial_record_*` tables as active workspace
   storage. Flyway V12 removes the retired V1 table family; V10 and V11 remove
   legacy JSONB transition storage and unowned compatibility rows.
7. Inspect aggregate metadata by default. Never print complete
   financial snapshots unless the user explicitly authorizes that scope.
8. Compare observations with the PostgreSQL relational runtime and Flyway
   history before diagnosing divergence.
9. Report the connected database/user, missing schema objects, migration state,
   row counts, active snapshot count/version, and whether the connected role is
   read-only or write-capable.

Never run DDL, DML, migrations, grants, or setup scripts under this skill. Ask
for explicit approval before any database mutation.
