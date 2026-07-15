# 0021 Separate financial workspace queries and commands

## Status

Accepted - implemented 2026-07-15

## Context

After duplicate record mutation routes and tabular import/export behavior were
removed, `FinancialsService` remained an 819-line application service. It
loaded current workspace state, validated and normalized replacement requests,
performed optimistic commands, calculated derived financial values, built API
responses, converted JSON backups, and translated persistence conflicts into
HTTP errors.

Those responsibilities changed for different reasons and made the surviving
full-snapshot workflow harder to explain, test, or extend. The request-scoped
repository also exposed individual collection, version, and pay-period getters
solely so the service could reconstruct the aggregate during a query.

## Decision

Separate the current financial workspace path along explicit application
boundaries without changing the HTTP or persistence contracts.

- `FinancialWorkspaceQueries` owns current snapshot reads, bounded audit
  history, and JSON backup export.
- `FinancialWorkspaceCommands` owns versioned full-snapshot replacement and
  JSON backup restore.
- `FinancialSnapshotRequestMapper` validates and maps API request and backup
  records to and from the domain aggregate.
- `FinancialSnapshotNormalizer` owns the existing required-record compatibility
  rules.
- `FinancialSnapshotCalculator` produces a framework-neutral calculated
  snapshot with pay-period dates, totals, category grouping, due-state, and
  paycheck counts.
- `FinancialSnapshotResponseMapper` is the sole constructor of financial API
  response DTOs from calculated domain state and audit events.
- `FinancialsController` depends on the query and command interfaces instead of
  one concrete application service.
- `FinancialsRepository` exposes the complete current aggregate, audit query,
  and versioned replacement operations. Collection-level read accessors that
  existed only for response construction are removed.

The current request-derived workspace store and `ResponseStatusException`
usage remain temporarily unchanged. Replacing those framework-aware
application boundaries is the next Phase I decision, not part of this
responsibility-only refactor.

ADR 0022 subsequently resolved that temporary boundary with a
framework-neutral current-workspace port and application exceptions mapped by
the API exception handler.

## Consequences

- Snapshot queries load one aggregate and apply sorting, normalization,
  calculations, and response mapping through named collaborators.
- Versioned commands remain the only financial mutation path and still return
  the recalculated persisted snapshot after a successful replacement.
- The API payloads, status codes, JSON backup format, audit behavior, optimistic
  concurrency, and PostgreSQL schema do not change.
- Pure calculations and normalization can be tested without controllers or
  storage adapters, while controller tests can substitute the query and
  command interfaces directly.
- More classes are present, but each boundary has one reason to change and the
  former `FinancialsService` facade is removed rather than retained as another
  indirection layer.
- At the time of this decision, application code was not yet
  framework-neutral; ADR 0022 subsequently resolved the coupling tracked by
  LIM-027.
