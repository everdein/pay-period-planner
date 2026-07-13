# 0007 Introduce PostgreSQL for financial persistence

## Status

Accepted

## Context

The financials feature started with file-backed JSON so personal data could stay
local and the app could move quickly without infrastructure. That was useful for
prototyping, but it is not enough for a production-grade end-to-end reference
application. The project needs a realistic persistence path for CRUD behavior,
concurrency, migrations, testing, and future authentication/user ownership.

ADR 0002 remains the local-development fallback decision. This ADR defines the
next persistence direction.

## Decision

Introduce PostgreSQL as the production persistence target while keeping JSON as
the default local fallback during the migration.

The first step is intentionally narrow:

- add PostgreSQL and Flyway dependencies
- keep datasource auto-configuration disabled in the default profile
- add a `postgres` Spring profile for datasource and migration configuration
- add an initial schema for the current financial snapshot aggregate
- store the active financial snapshot as a PostgreSQL `jsonb` document first
- migrate repository behavior in later slices instead of rewriting the full
  feature at once

As implemented, the `postgres` profile reads and writes the active snapshot via
`financial_snapshot_document.snapshot_json`. The normalized relational tables
from the first migration may be empty in a healthy database. ADR 0009 later
decided that those V1 tables should remain inactive rather than becoming the
runtime relational persistence path.

## Consequences

This gives the app a real database foundation without breaking the current
local workflow. The tradeoff is that the codebase temporarily has two
persistence stories: JSON as the default local implementation and PostgreSQL
JSONB as the database-backed implementation.

Follow-up work should add export/backup support, then design a cleaner
relational persistence path with new additive migrations, database-backed
repositories, CRUD APIs for financial records, and broader integration tests
around migrations and persistence behavior. Snapshot versioning for
full-snapshot saves was later handled by ADR 0008; the V1 relational-table path
was later clarified by ADR 0009.
