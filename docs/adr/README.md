# Architecture Decision Records

This folder contains Architecture Decision Records (ADRs) for the project.

ADRs capture meaningful technical decisions, the context behind them, and the
tradeoffs accepted at the time. They are intentionally short so they stay useful
as the codebase evolves.

## Records

| ADR                                                             | Status   | Decision                                        |
| --------------------------------------------------------------- | -------- | ----------------------------------------------- |
| [0001](0001-use-react-and-spring-boot-for-the-reference-app.md) | Accepted | Use React and Spring Boot for the reference app |
| [0002](0002-use-file-backed-json-for-local-financial-data.md)   | Accepted | Use file-backed JSON for local financial data   |
| [0003](0003-use-draft-save-workflow-for-financial-edits.md)     | Accepted | Use a draft/save workflow for financial edits   |
| [0004](0004-model-financials-as-a-single-snapshot-aggregate.md) | Accepted | Model financials as a single snapshot aggregate |
| [0005](0005-enforce-domain-invariants-with-ensure-functions.md) | Accepted | Enforce domain invariants with ensure functions |
| [0006](0006-add-backend-production-readiness-guardrails.md)     | Accepted | Add backend production readiness guardrails     |
| [0007](0007-introduce-postgresql-for-financial-persistence.md)  | Accepted | Introduce PostgreSQL for financial persistence  |
| [0008](0008-add-snapshot-version-concurrency-control.md)        | Accepted | Add snapshot version concurrency control        |
| [0009](0009-keep-v1-normalized-tables-inactive.md)              | Accepted | Keep V1 normalized tables inactive              |
| [0010](0010-add-v3-relational-financial-record-path.md)         | Accepted | Add V3 relational financial record path         |
| [0011](0011-add-granular-postgresql-record-persistence.md)      | Accepted | Add granular PostgreSQL record persistence      |
| [0012](0012-add-granular-financial-record-apis.md)              | Accepted | Add granular financial record APIs              |
| [0013](0013-add-tabular-financial-snapshot-import-export.md)    | Accepted | Add tabular snapshot import/export              |
| [0014](0014-adopt-postgresql-only-workspace-persistence.md)     | Accepted | Adopt PostgreSQL-only workspace persistence     |

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
