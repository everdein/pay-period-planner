# 0022 Isolate current workspace and HTTP errors

## Status

Accepted - implemented 2026-07-15

## Context

After financial workspace queries and commands were separated, the active
PostgreSQL store and workspace initialization service still depended directly
on `HttpServletRequest` and a servlet-aware workspace resolver. Financial
request validation and optimistic-write handling also raised Spring
`ResponseStatusException` values from the application layer.

Those dependencies made otherwise reusable application behavior responsible
for transport details. They also allowed HTTP status types to cross service and
repository boundaries even though controllers and `ApiExceptionHandler` already
owned the public Problem Detail contract.

## Decision

Isolate current-workspace resolution and HTTP error mapping behind explicit
boundaries.

- `service/CurrentWorkspace` is a framework-neutral application port that
  supplies the required workspace ID without exposing request, header, session,
  or security-framework types.
- `config/AuthenticatedRequestWorkspace` is the HTTP and Spring Security
  adapter. It reads `X-Workspace-ID`, inspects the authenticated account
  session, and verifies the selected database-derived membership.
- The active PostgreSQL snapshot store and workspace initialization service
  depend only on `CurrentWorkspace`.
- Financial request validation raises `FinancialRequestException`, and stale
  aggregate commands translate repository conflicts to
  `FinancialSnapshotVersionConflictException`.
- `ApiExceptionHandler` is the only layer that maps those application
  exceptions to HTTP `400` and `409` Problem Detail responses. Existing
  workspace selection, access, not-found, and initialization-conflict
  exceptions remain mapped there as well.

The financial API methods, payloads, status codes, safe details, and request-ID
properties do not change.

## Consequences

- Query, command, initialization, and persistence behavior can be tested with a
  simple `CurrentWorkspace` implementation and no servlet request.
- HTTP header and Spring Security principal handling remain visible in one
  adapter at the configuration boundary.
- Application services and active storage no longer import servlet request,
  HTTP status, or Spring web exception types.
- Repository-specific optimistic conflict exceptions do not escape the command
  boundary into the API handler.
- The mutable aggregate repository remains Spring request-scoped as a lifecycle
  choice; that annotation does not participate in workspace selection or error
  semantics.
- LIM-027 is resolved. A later refactor may revisit repository lifecycle without
  changing this port.
