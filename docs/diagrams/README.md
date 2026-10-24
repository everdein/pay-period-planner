# Pay Period Planner diagrams

Implementation-backed views, checked against the local checkout on October 24, 2026.
These explain the existing system; they do not propose new runtime behavior.
The topology is explicitly local development. It does not assert a deployed
Azure environment, production availability, or a hosted public demo.

## Reading order

1. [Local topology](01-local-topology.svg) - processes and request routing.
2. [Application architecture](02-application-architecture.svg) - responsibilities
   and state ownership across browser, API, and database boundaries.
3. [Save and conflict sequence](03-save-conflict-sequence.svg) - two tabs,
   optimistic concurrency, and deliberate recovery.

Each view also has an editable `.mmd` source and a `.png` export with the same
filename. Open a source in VS Code and run **Mermaid Preview: Preview Diagram**.
Use the SVG for zooming or publishing; the long sequence is intended for a
full-size viewer, not a small social thumbnail.

## What these views establish

- Vite serves the frontend on port 3000 and proxies `/api` to port 8080 by
  default. PostgreSQL is a dependency, not a claim about a particular host,
  container, network, or database port.
- The canonical draft reducer owns edits; Redux owns the last server snapshot
  and request status. The architecture shows these as separate nodes with an explicit baseline
  synchronization edge.
- The HTTP client supplies the session cookie, fresh CSRF proof for writes,
  and the selected workspace header. Server membership validation determines
  authorization; the client header alone does not grant access.
- Save replaces a complete aggregate. The adapter locks the workspace and
  active snapshot, checks the expected version, and writes the replacement
  and audit event in one transaction.
- A conflict keeps the draft visible. Discard Draft and Reload deliberately
  replaces it. The application does not automatically merge competing edits.
- A successful save synchronizes the baseline. The sequence assumes no newer
  edits are made while that save is pending; revision tracking handles that
  separate case in the implementation.

Solid flowchart arrows indicate a request, dependency, or stated data flow;
labels specify the meaning. Sequence arrows indicate chronological messages.
Read the existing [architecture map](../architecture-map.md) and
[API contract](../api-contract.md) for the complete contracts.

## Implementation evidence

- [Development server and proxy](../../frontend/vite.config.ts)
- [Browser HTTP and CSRF boundary](../../frontend/src/api/client.ts)
- [Save and reload actions](../../frontend/src/features/financials/FinancialsPage.tsx)
- [Conflict recovery UI](../../frontend/src/features/financials/FinancialsWorkflowFeedback.tsx)
- [Server snapshot state](../../frontend/src/features/financials/financialsSlice.ts)
- [Canonical draft](../../frontend/src/features/financials/financialsDraftReducer.ts)
- [HTTP controller](../../backend/src/main/java/com/example/backend/api/FinancialsController.java)
- [Security configuration](../../backend/src/main/java/com/example/backend/config/ApiSecurityConfig.java)
- [Workspace resolution](../../backend/src/main/java/com/example/backend/service/CurrentWorkspace.java)
- [Versioned command service](../../backend/src/main/java/com/example/backend/service/FinancialWorkspaceCommandService.java)
- [Transactional relational adapter](../../backend/src/main/java/com/example/backend/repository/PostgresFinancialRecordSnapshotAdapter.java)

## Maintenance

Update the source and regenerate both exports when a represented boundary or
workflow changes. The source references support review; this is a static code
inspection, not a new runtime, load, security, or production test result.
