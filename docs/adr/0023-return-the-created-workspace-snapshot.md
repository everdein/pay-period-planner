# 0023 Return the created workspace snapshot

## Status

Accepted - implemented 2026-07-15

## Context

`POST /api/v1/financials` previously called a `void` workspace initializer and
then asked `FinancialWorkspaceQueries` to load the current snapshot. The
initializer had already constructed and persisted the version-1 aggregate, so
the follow-up query repeated a workspace-scoped PostgreSQL read only to build
the `201 Created` response.

That sequence obscured command ownership and made successful initialization
depend on an unrelated read after the write. Snapshot calculation and response
mapping also needed a reusable boundary if both ordinary reads and creation
responses were to produce the same API representation.

## Decision

Return and present the created aggregate deliberately.

- `WorkspaceFinancialSnapshotInitializer.initialize` returns the exact
  `FinancialSnapshot` after its relational creation succeeds.
- `FinancialSnapshotPresenter` defines calculation and API presentation of a
  supplied domain snapshot.
- `CalculatedFinancialSnapshotPresenter` composes the existing calculator and
  response mapper with the application clock.
- `FinancialsController` presents the aggregate returned by initialization and
  does not call the current-workspace query path afterward.
- `FinancialWorkspaceQueryService` uses the same presenter for ordinary current
  snapshot reads.

The endpoint, payload, status, protected zero-value projection anchors, and
duplicate-initialization behavior do not change.

## Consequences

- Successful workspace initialization performs one relational creation and no
  redundant active-snapshot read.
- The command result passed to the response presenter is visibly the aggregate
  that was created.
- Query and initialization responses share one calculation and presentation
  path without duplicating mapping code.
- The initializer can return its input aggregate safely because initialization
  creates empty record collections and PostgreSQL does not assign domain record
  IDs. If initialization later creates child records, the persistence contract
  must return their stored identities deliberately.
- Focused controller tests prove the returned aggregate is presented, while the
  PostgreSQL account-session integration test preserves the complete `201`,
  `409`, and subsequent-read workflow.
