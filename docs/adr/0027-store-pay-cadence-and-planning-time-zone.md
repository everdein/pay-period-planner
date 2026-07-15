# 0027 Store pay cadence and planning time zone

## Status

Accepted

## Context

The workspace originally assumed a biweekly paycheck and derived "today" from
the backend host and browser local zones independently. That worked while both
processes ran on one workstation, but it produced incorrect income
annualization for other payroll schedules and could disagree near midnight
once the frontend and backend were hosted separately.

Date-only financial values are household planning dates, not instants. The
product still needs one explicit zone for deciding the current date and one
explicit cadence for income projections and recurring payday generation.

## Decision

Store `planningSettings` in every versioned financial snapshot:

- `payCadence`: `WEEKLY`, `BIWEEKLY`, `SEMIMONTHLY`, or `MONTHLY`
- `timeZone`: a validated IANA time-zone identifier

V9 adds `pay_cadence` and `planning_time_zone` to
`financial_record_snapshot`. Historical rows and legacy input default to
`BIWEEKLY` and `UTC`. New browser onboarding proposes the browser's IANA zone,
and users can edit both settings in the projection view.

The backend derives one `currentDate` from the request clock instant and the
stored workspace zone, then uses that same date for the active pay period and
response. API dates remain ISO date-only strings. Frontend date-only arithmetic
uses UTC calendar operations so browser offsets cannot move a selected day.

Income annualization uses 52, 26, 24, or 12 periods per year and derives a
monthly value as annual income divided by 12. Recurring payday generation uses
7-day, 14-day, twice-monthly, or calendar-month recurrence. Calendar-month
dates clamp to the final day of shorter months.

## Consequences

- Frontend and backend agree on the workspace's current planning date even
  when hosted in different system zones.
- Weekly, biweekly, semimonthly, and monthly households receive cadence-aware
  income projections and payday generation.
- Changing cadence does not silently redefine the stored pay-period anchor;
  users may edit that inclusive planning window independently.
- The API, JSON backup, canonical draft, relational adapter, and migration
  history carry the same settings as part of aggregate replacement.
- Daylight-saving transitions affect the instant at which the workspace date
  changes, but never change a persisted date-only value.
