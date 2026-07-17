# Domain Glossary

This glossary defines terms as the current application uses them. The product
supports household cash-flow and pay-period planning; it is not accounting,
transaction reconciliation, transfer instruction, or financial advice.

## Financial Workspace Terms

### Active pay period

The inclusive date window used to decide which monthly and annual withdrawals
are due now. The stored start/end dates act as an anchor. On read, the backend
shifts a window of the same length forward or backward until it contains the
current date.

The active pay period is not a payroll provider record and is not necessarily
exactly fourteen days.

### Planning settings

Versioned workspace settings containing the pay cadence and IANA planning time
zone. Historical input without settings defaults to biweekly and UTC. Changing
these settings is a normal full-snapshot draft/save operation.

### Pay cadence

The selected paycheck recurrence: weekly, biweekly, semimonthly, or monthly.
It controls primary-paycheck annualization and recurring-payday generation; it
does not silently resize the independently editable pay-period anchor.

### Planning time zone

The IANA zone used by the backend to derive the workspace's `currentDate` from
an instant. Current-period and frontend status calculations use that planning
date. Persisted financial dates remain date-only values and are not converted
between zones.

### Annual withdrawal

A recurring expense stored as month/day, amount, account, and paid state. The
service projects it into the active pay-period year to derive a concrete due
date and `inPayPeriod` status. The year is derived, not persisted.

### Asset account

One balance-bearing account identified by account name and company and grouped
under an asset category. Amounts are nonnegative tracked balances. The
application does not model transactions, market prices, cost basis, or account
ownership.

### Asset category

A labeled grouping of asset accounts, such as cash or investments. Category
totals and total tracked assets are derived from account amounts. Categories
are represented in the API, while persistence stores the underlying accounts
with category keys and labels.

### Bill / monthly withdrawal

Two UI names for the same recurring monthly expense concept. The API and Java
model call it a bill; the workspace tab calls it a monthly withdrawal. It
stores a due day from 1–31, amount, payment account, and paid state.

The concrete due date, ordinal label, and `inPayPeriod` flag are derived. A due
day beyond a month’s length is clamped to that month’s final day.

### Debt account

A named balance owed to a company. Total debt is the sum of debt-account
amounts. The application does not model interest, minimum payments,
amortization, transactions, or account authentication.

### Possible savings transfer

A projection output representing estimated cash remaining after covered bills
and debt. The destination is not tied to an account type or institution. It is
not a transfer instruction, and no banking integration exists.

### Important date

A user-managed date, event label, and type. The frontend derives `passed`,
`next`, or `upcoming` presentation status relative to today. Status is not
persisted.

### Income event

A dated calendar event with a label, type, and optional check number. The
frontend derives monthly paycheck counts and `received`, `current`, or
`upcoming` status. Those derived values are not persisted.

### Numbered income row

An income event with a non-null check number. The Income Calendar generator can
replace numbered rows in a selected year when creating a cadence-aware payday
calendar. Semimonthly generation requires two anchor dates. One-time income
events without check numbers are preserved by that replacement.

### Income summary item

A category/interval/amount source record. The workspace projection roles select
one item as the primary paycheck independently of its editable category and
interval labels. Additional source rows are editable and persisted for
planning. Additional summary rows may be derived for presentation from the
selected primary source.

### Net worth

`total tracked assets - total debt`, calculated by the backend for the returned
snapshot. It covers only records entered in this workspace.

### Paid / unpaid

A planning flag on monthly and annual withdrawals. It is manually managed and
does not represent bank settlement or reconciliation.

### Pay-period total

The sum of monthly withdrawals whose derived due dates fall inside the active
pay period. Annual withdrawals due in the period are reported separately as
the annual pay-period total.

### Projection

A frontend-only household-planning calculation using pay-period withdrawals,
the primary paycheck annualized from the selected cadence, the selected housing
payment and reserve, debt, and available cash. It does not persist a forecast,
execute transfers, reconcile transactions, produce accounting records, provide
financial advice, or guarantee future balances.

### Projection roles

Three typed record references stored with each versioned snapshot select the
housing payment, housing reserve, and primary paycheck used by projections. Their
bill, account, category, and interval names are presentation labels and can be
edited freely. A selected record must be reassigned before it can be removed.
Legacy input without roles may use historical labels once to establish these roles.

## Application State Terms

### Draft

The editable browser aggregate derived from the last loaded server snapshot.
One feature-local reducer owns both the committed baseline and canonical draft;
domain hooks expose focused forms and commands without owning separate record
collections. Changes to a draft are not persistent until the user saves.
Failed saves leave the draft available for correction or retry.

New draft records use temporary negative IDs. When saved, negative IDs are sent
as `null`; the backend assigns positive IDs. One shared allocator prevents
temporary IDs from colliding across record types.

### Dirty state

Derived frontend state indicating that the canonical draft differs from its
committed baseline. It controls save/reset actions. It is separate from the
server snapshot `version` used for optimistic concurrency.

### Draft revision

A monotonic frontend sequence that identifies the local draft submitted by a
save. A matching save response can commit that revision; a response for an
older revision updates the baseline and server version without overwriting
newer edits. Resetting a draft does not rewind the sequence.

### Snapshot

The aggregate persistence and API boundary containing pay-period anchors,
bills, annual withdrawals, asset accounts, debt accounts, income summary
items, income events, and important dates.

A snapshot response also contains derived totals, dates, category groupings,
and statuses. A snapshot save replaces the stored aggregate rather than
patching individual fields.

### Full-snapshot save

`PUT /api/v1/financials`. The frontend sends all persisted collections and
pay-period anchors in one request. The backend validates, normalizes, replaces
its in-memory aggregate, persists it through the selected store, and returns a
fresh calculated response.

The request must include the current snapshot `version`. If the server has a
newer version, the operation fails with `409 Conflict` instead of overwriting
the newer snapshot.

### Retired granular endpoints

The former record-level and pay-period mutation routes under
`/api/v1/financials`. ADR 0016 retired them so the versioned full-snapshot save
is the sole supported financial mutation contract.

### Derived field

A response or presentation value calculated from persisted source fields.
Examples include totals, due dates, `inPayPeriod`, category totals,
`checksInMonth`, event statuses, and projection values. Derived fields must not
silently become new persistence inputs.

### Audit history

Append-only saved-change metadata available from
`GET /api/v1/financials/history`. Each audit event records action, resource
type/ID, timestamp, snapshot version movement, and a compact aggregate
projection summary after the write. Audit history is not a field-level ledger
or compliance audit log.

### Application user

A database-backed identity represented by `application_user`. V5 stores a
case-insensitively normalized email, adaptive credential hash, display name,
and account status. The account API creates and authenticates these users, and
financial authorization derives from their current workspace memberships.

### Workspace

The PostgreSQL runtime ownership boundary for one financial aggregate. Signup
creates a `Personal` workspace and records its creator. V6 links relational
snapshots to workspaces and scopes every PostgreSQL financial operation by a
current membership.

### Workspace membership

The association between an application user and a workspace, with an `owner`,
`admin`, or `member` role. V5 permits at most one owner per workspace. Session
recovery reloads these memberships for the authenticated principal, and
financial authorization validates the selected membership on every request.

### Application session

A server-managed authenticated session used by the account API. V5
stores only a SHA-256 token hash, along with expiration, activity, and
revocation metadata. The plaintext opaque token exists only in an `HttpOnly`,
`SameSite=Strict` browser cookie. The financial API uses this
session, and the frontend recovers it without storing credentials or reading
the cookie value.

## Persistence Terms

### PostgreSQL runtime

The only application persistence path. `PostgresFinancialsSnapshotStore` binds
the request-scoped aggregate to an authenticated workspace and reads/writes
workspace-owned `financial_record_*` rows. Flyway applies schema migrations.

### Retired transition storage

The V2 JSONB document table, V7 migration ledger, optional source linkage, and
unowned relational compatibility rows removed by V10/V11. They remain visible in
immutable Flyway history but are not supported storage or recovery paths.

### Retired normalized V1 schema

The eight relational tables introduced by `V1__create_financials_schema.sql`.
They never became the runtime persistence path and are dropped by V12. Their
definitions remain only as immutable migration history.

### Snapshot store

The `FinancialsSnapshotStore` interface with `load` and `save` operations.
`PostgresFinancialsSnapshotStore` is the only runtime implementation.

### Seed data

Committed example data is synthetic input for tests and demos. Runtime startup
and account creation never seed a financial workspace implicitly. A user
initializes an empty workspace and enters data through authenticated APIs, or
deliberately restores a supported application export.

## Data Classification Terms

### Mock financial data

Synthetic values safe for source control, tests, examples, screenshots, and
shared diagnostics. The committed `financials.example.json` is the canonical
mock dataset.

### Personal financial data

Any real balances, income, bills, dates, account/company combinations, local
JSON, PostgreSQL rows, exports, audit history, logs, or screenshots. Keep it
local and out of commits, prompts to external services, CI artifacts, and
documentation.

### Metadata-only inspection

Diagnosis using schema names, row counts, JSON keys/types, IDs, versions,
timestamps, and privileges without printing financial values. This is the
default PostgreSQL inspection scope.
