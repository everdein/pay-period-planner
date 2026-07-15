# 0004 Model Financials as a Single Snapshot Aggregate

## Status

Partially superseded by ADR 0026

## Context

The financials feature now tracks several related planning tables: monthly
withdrawals, annual withdrawals, income summary assumptions, income calendar
events, asset accounts, debt accounts, and important dates.

These records are edited together in one household planning workspace. The
current app is single-user and file-backed, so splitting each table into a
separate persistence workflow would add complexity before the domain requires
it.

## Decision

Keep financial data as one backend-managed snapshot aggregate.

The frontend loads one snapshot when the app opens, edits local draft state,
and saves the full snapshot in one request. The backend validates and persists
the aggregate to local JSON.

The current HTTP routes use `/api/v1/financials` as the conceptual API
resource. Individual bill endpoints live underneath the aggregate at
`/api/v1/financials/bills`.

Display derived values in the UI and API responses rather than persisting them
as source data. Examples include pay period inclusion, annual withdrawal due
dates, current paycheck status, important date status, totals, total debt, net
worth, disposable-income overview figures, and pay period projection totals.

Treat stored pay period dates as a schedule anchor. When the snapshot is read,
the backend derives the active pay period by moving that window forward or
backward until it contains today's date. If the user edits the dates and saves,
those edited dates become the new anchor.

Keep pay period projections as a derived planning view for now. The projection
uses the current snapshot/draft data to estimate remaining cash from the
selected primary paycheck and cadence, next pay period withdrawals, annual
withdrawals due in the period, housing reserve needs, and current debt. It
focuses on the next paycheck, shows a possible debt payment first, and shows a
possible savings transfer only after debt is covered. Current pay period
values can be displayed as context, but they should not compete with the next
paycheck projection. Projection history and saved projection plans are
intentionally deferred until there is a clearer workflow for comparing planned
and actual results.

Projection-specific meaning should be modeled with a few protected anchor rows
instead of role metadata on every record. The app owns `Rent`, `Rent Reserve`,
and `Net Income / Bi-Weekly` as projection anchors. Users can edit their
amounts and normal details, but the UI prevents renaming or deleting them so
projection math has stable inputs without adding role columns to every table.
Income summary persistence follows the same source-row idea: only the
bi-weekly net income source needs to be saved, while annual, monthly, weekly,
and disposable income values are derived from that source and monthly
withdrawals.

ADR 0026 supersedes the protected-name portion of this decision. Projection
meaning now uses versioned role IDs, while the single-snapshot aggregate and
derived projection decisions remain accepted.

ADR 0027 supersedes the biweekly-only and machine-local date assumptions. The
aggregate now stores pay cadence and planning time zone, and projections
support weekly, biweekly, semimonthly, and monthly schedules.

Displayed dates should use `MM/DD/YYYY`. Native browser date inputs may use the
browser's internal ISO date value while editing.

## Consequences

- The financial workflow stays cohesive and easy to reset or save in one batch.
- Personal data remains isolated to the ignored local JSON file.
- Derived values can change as dates and pay periods move without mutating
  stored source rows.
- Pay period planning can evolve without introducing another persistence model
  too early.
- The API payload is larger than a narrow row-update endpoint.
- Multi-user collaboration would need versioning, conflict detection, or a move
  toward more granular persistence.
- A future production version can split the aggregate into database-backed
  tables while keeping the current snapshot contract as a useful boundary.
