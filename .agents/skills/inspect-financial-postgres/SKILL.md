---
name: inspect-financial-postgres
description: Inspect and diagnose the local financial_app PostgreSQL database without modifying it. Use for schema checks, snapshot counts, JSONB shape inspection, profile troubleshooting, persistence reviews, and comparison of database state with backend behavior.
---

# Inspect Financial PostgreSQL

1. Read `AGENTS.md`, `backend/README.md`, the migrations, and
   `PostgresFinancialsSnapshotStore.java`.
2. Prefer the configured PostgreSQL MCP server when it is available and
   demonstrably read-only.
3. Otherwise run `.\scripts\inspect-postgres.ps1` for a safe baseline.
4. For additional SQL, begin an explicit `READ ONLY` transaction and roll it
   back. Use only `SELECT`, catalog inspection, and `EXPLAIN` without
   `ANALYZE`.
5. Inspect aggregate metadata and JSON keys by default. Avoid printing complete
   financial snapshots unless the user specifically needs them.
6. Compare database observations with both the `postgres` profile and JSON
   fallback behavior before diagnosing divergence.
7. Report the connected database/user, query scope, and whether any expected
   table or active snapshot was absent.

Never run DDL, DML, migrations, grants, or setup scripts under this skill. Ask
for explicit approval before any database mutation.
