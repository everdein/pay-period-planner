# 0004 Model Financials as a Single Snapshot Aggregate

## Status

Accepted

## Context

The financials feature now tracks several related planning tables: monthly
withdrawals, annual withdrawals, income summary assumptions, income calendar
events, asset accounts, debt accounts, and important dates.

These records are edited together in one personal finance workspace. The
current app is single-user and file-backed, so splitting each table into a
separate persistence workflow would add complexity before the domain requires
it.

## Decision

Keep financial data as one backend-managed snapshot aggregate.

The frontend loads one snapshot when the app opens, edits local draft state,
and saves the full snapshot in one request. The backend validates and persists
the aggregate to local JSON.

Display derived values in the UI and API responses rather than persisting them
as source data. Examples include pay period inclusion, annual withdrawal due
dates, current paycheck status, important date status, totals, total debt, net
worth, and disposable-income overview figures.

Displayed dates should use `MM/DD/YYYY`. Native browser date inputs may use the
browser's internal ISO date value while editing.

## Consequences

- The financial workflow stays cohesive and easy to reset or save in one batch.
- Personal data remains isolated to the ignored local JSON file.
- Derived values can change as dates and pay periods move without mutating
  stored source rows.
- The API payload is larger than a narrow row-update endpoint.
- Multi-user collaboration would need versioning, conflict detection, or a move
  toward more granular persistence.
- A future production version can split the aggregate into database-backed
  tables while keeping the current snapshot contract as a useful boundary.
