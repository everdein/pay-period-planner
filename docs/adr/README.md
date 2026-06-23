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
