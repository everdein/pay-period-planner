# 0003 Use a Draft/Save Workflow for Financial Edits

## Status

Accepted

## Context

The financials UI includes withdrawals, pay period dates, income planning
items, income calendar events, asset account categories, debt balances, and
important dates. Users may want to make several edits before deciding to
persist the final result.

An earlier approach could call the backend for each individual add, edit, or
remove action. That is simple locally, but it does not match the workflow used
in many production applications where a user reviews a group of changes before
saving.

## Decision

Use a draft/save workflow for the financials feature.

The frontend loads the financial snapshot once when the app opens. User edits
are held in local component state. Redux tracks the committed snapshot, loading
state, saving state, and API errors.

When the user clicks `Save Changes`, the frontend sends the full edited
snapshot to the backend in one request:

```http
PUT /api/financials/expenses/snapshot
```

The backend validates and persists the snapshot, then returns the updated
snapshot as the new committed state. Derived values such as totals, pay-period
inclusion, current paycheck status, important date status, debt totals, and net
worth are recalculated from the source rows.

## Consequences

- Users can make multiple edits before saving.
- The UI can show unsaved changes and support resetting to the last committed
  snapshot.
- The frontend remains responsive because form changes do not require immediate
  API calls.
- The backend receives one larger snapshot update instead of many smaller row
  updates.
- Conflict handling is minimal because the app is currently single-user.
- If the app becomes multi-user, this decision may need versioning, optimistic
  concurrency, or more granular write operations.
