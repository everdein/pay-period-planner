# 0025 Use one canonical frontend financial draft

## Status

Accepted

## Context

The financial workspace previously expanded one server snapshot into seven
independent domain-hook collections. Each hook loaded and mutated its own
records and allocated temporary IDs, while the workspace hook separately
tracked dirty state, draft revision, reset behavior, and pending removal.

That design kept each form focused but distributed ownership of one aggregate
across several state machines. Cross-domain changes such as pay-period updates,
aggregate saves, resets, and responses arriving after newer edits required
coordination outside the state that those operations changed.

Redux already owns the committed server snapshot and request lifecycle. Moving
unsaved personal financial data into global application state would increase
its exposure and couple server caching to the editing model.

## Decision

Use one feature-local reducer for the editable financial aggregate. The reducer
owns the committed baseline, canonical draft, server snapshot version,
monotonic local revision, shared temporary-ID allocator, pending removal, and
reset generation.

All financial record and pay-period mutations use typed reducer commands.
Selectors derive dirty state, the full save request, submitted revision,
snapshot version, and removal confirmation from the same state.

Domain-focused hooks remain as facades. They retain transient form and editing
state, derive presentation values from the canonical draft, and dispatch
commands instead of maintaining independent record collections.

Snapshot synchronization records the revision submitted with a save. A
matching response replaces the baseline and draft. If newer edits exist, the
response advances the committed baseline and server version while preserving
the newer draft. Reset points the draft back to the baseline but does not
rewind the revision sequence, preventing an in-flight response from sharing a
revision with a later edit.

Redux continues to own only the last server snapshot and remote request state.
The canonical draft remains local to the authenticated financial workspace.

## Consequences

- Aggregate edits, resets, removals, and pay-period changes are atomic and can
  be tested through a pure reducer.
- Temporary negative IDs are unique across financial record types.
- Save-race behavior is explicit and covered without coordinating mutable refs
  across domain hooks.
- Domain hooks stay small enough to express form-specific behavior without
  becoming competing sources of truth.
- `FinancialsPage` remains the authenticated server-action controller while
  focused workspace-state and workflow-feedback components own route and
  recovery presentation.
