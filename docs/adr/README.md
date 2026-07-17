# Architecture Decision Records

This folder contains Architecture Decision Records (ADRs) for the project.

ADRs capture meaningful technical decisions, the context behind them, and the
tradeoffs accepted at the time. They are intentionally short so they stay useful
as the codebase evolves.

## Records

| ADR                                                                    | Status               | Decision                                         |
| ---------------------------------------------------------------------- | -------------------- | ------------------------------------------------ |
| [0001](0001-use-react-and-spring-boot-for-the-reference-app.md)        | Accepted             | Use React and Spring Boot for the reference app  |
| [0002](0002-use-file-backed-json-for-local-financial-data.md)          | Superseded           | Use file-backed JSON for local financial data    |
| [0003](0003-use-draft-save-workflow-for-financial-edits.md)            | Accepted             | Use a draft/save workflow for financial edits    |
| [0004](0004-model-financials-as-a-single-snapshot-aggregate.md)        | Partially superseded | Model financials as a single snapshot aggregate  |
| [0005](0005-enforce-domain-invariants-with-ensure-functions.md)        | Superseded           | Enforce domain invariants with ensure functions  |
| [0006](0006-add-backend-production-readiness-guardrails.md)            | Accepted             | Add backend production readiness guardrails      |
| [0007](0007-introduce-postgresql-for-financial-persistence.md)         | Accepted             | Introduce PostgreSQL for financial persistence   |
| [0008](0008-add-snapshot-version-concurrency-control.md)               | Partially superseded | Add snapshot version concurrency control         |
| [0009](0009-keep-v1-normalized-tables-inactive.md)                     | Superseded           | Keep V1 normalized tables inactive               |
| [0010](0010-add-v3-relational-financial-record-path.md)                | Accepted             | Add V3 relational financial record path          |
| [0011](0011-add-granular-postgresql-record-persistence.md)             | Partially superseded | Add granular PostgreSQL record persistence       |
| [0012](0012-add-granular-financial-record-apis.md)                     | Superseded           | Add granular financial record APIs               |
| [0013](0013-add-tabular-financial-snapshot-import-export.md)           | Superseded           | Add tabular snapshot import/export               |
| [0014](0014-adopt-postgresql-only-workspace-persistence.md)            | Accepted             | Adopt PostgreSQL-only workspace persistence      |
| [0015](0015-use-flyway-as-the-single-migration-authority.md)           | Accepted             | Use Flyway as the single migration authority     |
| [0016](0016-use-financial-workspace-as-the-sole-mutation-aggregate.md) | Accepted             | Use the workspace as the sole mutation aggregate |
| [0017](0017-retire-xlsx-snapshot-import.md)                            | Superseded           | Retire XLSX snapshot import                      |
| [0018](0018-use-json-as-the-sole-application-backup-format.md)         | Accepted             | Use JSON as the sole application backup format   |
| [0019](0019-separate-current-snapshot-and-audit-persistence.md)        | Accepted             | Separate current snapshot and audit persistence  |
| [0020](0020-use-scoped-overrides-instead-of-install-time-patches.md)   | Accepted             | Use scoped overrides instead of install patches  |
| [0021](0021-separate-financial-workspace-queries-and-commands.md)      | Accepted             | Separate workspace queries and commands          |
| [0022](0022-isolate-current-workspace-and-http-errors.md)              | Accepted             | Isolate current workspace and HTTP errors        |
| [0023](0023-return-the-created-workspace-snapshot.md)                  | Accepted             | Return the created workspace snapshot            |
| [0024](0024-preserve-structured-frontend-api-failures.md)              | Accepted             | Preserve structured frontend API failures        |
| [0025](0025-use-one-canonical-frontend-financial-draft.md)             | Accepted             | Use one canonical frontend financial draft       |
| [0026](0026-use-versioned-record-ids-for-projection-roles.md)          | Accepted             | Use versioned record IDs for projection roles    |
| [0027](0027-store-pay-cadence-and-planning-time-zone.md)               | Accepted             | Store cadence and planning time zone             |
| [0028](0028-retire-legacy-snapshot-migration-administration.md)        | Accepted             | Retire legacy snapshot migration administration  |
| [0029](0029-retire-inactive-v1-schema-and-legacy-adoption.md)          | Accepted             | Retire V1 schema and legacy adoption paths       |

The table summarizes each record. Open the ADR's `Status` section for the
specific successor and which part of the original decision remains in force.

## Template

```md
# 0000 Decision title

## Status

Proposed | Accepted | Superseded

## Context

What problem are we solving? What constraints matter?

## Decision

What did we choose?

## Consequences

What tradeoffs, follow-up work, and risks come with this choice?
```
