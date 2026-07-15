# 0016 Use the financial workspace as the sole mutation aggregate

## Status

Accepted

## Context

ADRs 0003 and 0004 established a browser draft/save workflow around one
financial workspace snapshot. ADRs 0011 and 0012 later added record-level
PostgreSQL operations and public granular mutation endpoints as groundwork for
possible external clients. The active browser never adopted those endpoints;
it still edits one complete draft and saves it through the version-checked
`PUT /api/v1/financials` boundary.

Keeping both mutation models now creates more cost than flexibility:

- full-workspace saves use client-supplied optimistic versions, while granular
  endpoints mutate immediately without the same client concurrency contract;
- controllers, DTOs, service methods, repository operations, relational
  adapter methods, tests, and documentation describe two ways to change the
  same records;
- future contributors must reason about whether a record is independently
  mutable or part of one transactional planning workspace; and
- decomposition of the large financial service would preserve duplicate
  responsibilities instead of removing them.

The portfolio goal favors one coherent consistency model that can be explained,
tested, and defended over unused API breadth.

## Decision

Treat the authenticated financial workspace as the sole public mutation
aggregate for the current API and product workflow.

- Keep `GET /api/v1/financials` as the current workspace query and
  `PUT /api/v1/financials` as the version-checked mutation boundary.
- Keep initialization as creation of the first workspace aggregate. Backup or
  restore operations must pass through the same aggregate validation and
  optimistic-concurrency rules rather than introduce another consistency
  model.
- Retire the public record-level and pay-period mutation endpoints in a
  follow-up implementation slice. Retire repository and relational-adapter
  CRUD methods that no supported runtime workflow uses.
- Preserve the relational `financial_record_*` tables, stable application
  record IDs, and existing Flyway history. Do not edit applied migrations or
  drop schema objects solely because the corresponding public CRUD surface is
  removed.
- Keep read-only projections such as audit history and exports as separate
  queries when they do not mutate the workspace.
- Preserve whole-workspace optimistic version checks. This decision does not
  add collaborative merging, partial updates, or field-level conflict
  resolution.
- Perform backend command/query and calculation extraction only after the
  duplicate mutation paths are removed, so new boundaries reflect the smaller
  supported application.

This ADR reaffirms ADR 0004, supersedes ADR 0012, and supersedes the
record-level CRUD portion of ADR 0011. ADR 0011's additive V4 identity
constraints and the relational storage decisions in ADRs 0010 and 0014 remain
accepted.

## Implementation

Completed in the Phase H mutation-consolidation slice:

- removed all public record-level and pay-period mutation routes and frontend
  endpoint helpers;
- removed request-only DTOs plus the corresponding controller, service,
  request-scoped repository, and PostgreSQL adapter CRUD methods;
- retained aggregate load, initialization, optimistic replacement, JSON backup
  and restore, audit history, stable record IDs, relational tables, and Flyway
  history; and
- added controller regression coverage proving every retired route returns
  `404`, while service and PostgreSQL tests exercise aggregate replacement.

## Consequences

- The browser, API, service, and persistence layers converge on one mutation
  contract and one optimistic-concurrency story.
- Removing unused granular behavior should reduce controller, DTO, service,
  repository, adapter, test, and documentation surface before deeper
  refactoring begins.
- Full saves continue to send a larger payload and conflict at workspace
  granularity. That tradeoff matches the current draft/save product behavior.
- A future collaborative or integration-focused API would require a new ADR
  and likely a new API version with explicit partial-write concurrency
  semantics.
- Unsupported record-level or pay-period mutation requests now return `404`.
  New partial-write behavior requires a new API version and concurrency ADR.
