# 0002 Use File-Backed JSON for Local Financial Data

## Status

Superseded by ADR 0014

## Context

The financials feature needs persistence so user edits survive application
restarts. At the same time, the current project should not require a database
before the financial workflow is fully understood.

The financial data may contain personal information, so real local data should
not be committed to Git.

## Decision

Store financial data in a backend-managed local JSON file. The file contains
the full financial snapshot aggregate, including withdrawals, income planning
items, assets, debts, calendar events, and important dates.

Commit a safe example file:

```text
backend/data/financials.example.json
```

Ignore the local user data file:

```text
backend/data/financials.local.json
```

On startup, the backend creates the local file from the example file when the
local file does not already exist.

## Consequences

- The feature has real persistence without requiring database setup.
- Personal financial data stays local and is protected by `.gitignore`.
- New clones remain usable because the committed example file provides a safe
  starting point.
- The repository layer gives us a clear place to replace JSON storage with a
  database later.
- The JSON shape can evolve as the financial workspace grows, while old local
  files remain loadable by treating missing collections as empty.
- The current storage model is single-user and local-only.
- File-backed JSON is not appropriate for concurrent multi-user production
  writes.
