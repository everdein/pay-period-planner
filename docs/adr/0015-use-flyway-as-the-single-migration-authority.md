# 0015 Use Flyway as the single migration authority

## Status

Accepted

## Context

The PostgreSQL runtime already used Flyway, but the local setup script executed
V1 through V4 directly with `psql` and skipped files based on representative
object presence. That second executor could create a usable schema without
`flyway_schema_history`, weaken migration-state evidence, and allow a partial
schema to be mistaken for a completed migration chain.

ADR 0014 requires one PostgreSQL migration path before user, workspace,
membership, and session migrations are added. Existing local databases may
contain personal data and therefore cannot be dropped, recreated, or silently
assigned a Flyway baseline during this transition.

## Decision

Use Flyway as the only executor of versioned PostgreSQL migrations.

- `scripts/setup-local-postgres.ps1` creates or updates the local role and
  database, inspects migration state, and delegates all versioned DDL to
  `scripts/migrate-postgres.ps1`.
- `scripts/migrate-postgres.ps1` invokes the repository-pinned Flyway Maven
  plugin with the same ordered migration directory used by Spring Boot, then
  runs Flyway validation.
- PostgreSQL application startup continues to run the same Flyway migration
  chain before repository access.
- A non-empty schema without Flyway history fails closed. The setup script
  permits a baseline only through explicit legacy adoption switches after an
  object-signature check.
- A V2 document-only legacy schema with the expected columns and no duplicate
  active rows receives baseline version 0 so Flyway still executes and records
  every migration from V1 onward. V2 restores its unique-active index when
  absent. A legacy schema with the expected V1-V4 table/index signature
  receives baseline version 4 and then applies every pending migration. Empty,
  partial, or mismatched schemas are refused.
- PostgreSQL integration tests run the classpath migration chain through the
  Flyway Java API in isolated schemas. Tests do not split or execute migration
  SQL independently.
- Direct `psql -f` execution of versioned migration files is not a supported
  setup, repair, or troubleshooting path.

## Consequences

- Fresh setup, repeat setup, application startup, and integration tests share
  one migration order, checksum model, and history table.
- Legacy adoption is an explicit database mutation and still requires a backup
  and operator approval when personal data is present.
- A mismatched legacy schema requires an additive repair plan; the setup script
  will not fabricate history or infer success from one table or index.
- V5 identity and V6 workspace-ownership groundwork, plus future ownership
  changes, can be added as Flyway migrations without preserving a parallel SQL
  execution path.
