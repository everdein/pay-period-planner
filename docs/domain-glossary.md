# Domain Glossary

This glossary defines terms as the current application uses them. It is not
general financial advice or a model for multi-user accounting.

## Financial Workspace Terms

### Active pay period

The inclusive date window used to decide which monthly and annual withdrawals
are due now. The stored start/end dates act as an anchor. On read, the backend
shifts a window of the same length forward or backward until it contains the
current date.

The active pay period is not a payroll provider record and is not necessarily
exactly fourteen days.

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

### HYSA

High-yield savings account. “Possible HYSA transfer” is a projection output:
remaining projected cash after covered bills and debt. It is not a transfer
instruction and no banking integration exists.

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
replace numbered rows in a selected year when creating a bi-weekly payday
calendar. One-time income events without check numbers are preserved by that
replacement.

### Income summary item

A category/interval/amount source record. The distinguished source item is
`Net Income` / `Bi-Weekly`; projections use it as paycheck income. Additional
source rows are editable and persisted for planning. Additional summary rows may
be derived for presentation from the primary source.

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

A frontend-only planning calculation using pay-period withdrawals, the primary
bi-weekly net-income item, rent, rent reserve, debt, and available cash. It does
not persist a forecast, execute transfers, or guarantee future balances.

### Rent and Rent Reserve

Special name-based anchors used by projections:

- A monthly withdrawal named `Rent` is the rent obligation.
- An asset account named `Rent Reserve` is savings set aside for rent.

Matching is trimmed and case-insensitive. Renaming either record changes
projection behavior; these are domain anchors, not presentation-only labels.

## Application State Terms

### Draft

The editable browser copy derived from the last loaded server snapshot.
Changes to a draft are not persistent until the user saves. Failed saves leave
the draft available for correction or retry.

New draft records use temporary negative IDs. When saved, negative IDs are sent
as `null`; the backend assigns positive IDs.

### Dirty state

Frontend state indicating that the local draft differs through an edit the UI
tracks. It controls save/cancel actions. It is separate from the server
snapshot `version` used for optimistic concurrency.

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

### Granular endpoints

The bill create/update/delete and pay-period routes under
`/api/v1/financials`. They mutate the same aggregate and persist immediately.
The current workspace UI primarily uses the full-snapshot save.

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

The only application persistence path. `PostgresFinancialsSnapshotStore` binds the request-scoped aggregate to an
authenticated workspace and reads/writes V3/V4/V6/V7 relational rows. Flyway
applies schema migrations.

### Legacy JSON file

An ignored `backend/data/financials.local.json` or recovery sibling retained
outside runtime behavior as a possible explicit migration source. The
application never reads, writes, or seeds from these files during startup.

### Legacy snapshot document

The retained row in `financial_snapshot_document` where `active = true`. Its
`snapshot_json` may contain a pre-activation aggregate and audit history. It is
an explicit migration source, not the PostgreSQL runtime store.

### Normalized V1 tables

The relational tables introduced by
`V1__create_financials_schema.sql`. They are inactive historical groundwork,
not the active or planned runtime relational persistence path as-is. Zero rows
in these tables is expected.

### Snapshot store

The `FinancialsSnapshotStore` interface with `load` and `save` operations.
`PostgresFinancialsSnapshotStore` is the only runtime implementation.

### Workspace snapshot migration

An explicit PostgreSQL-only operator operation that copies one backed-up legacy
JSON file or active JSONB storage envelope into an empty relational workspace.
It names the owner and workspace, preserves source version and audit events,
and verifies record counts without changing the legacy source. The relational
adapter is already the PostgreSQL runtime store.

### Migration fingerprint

The SHA-256 digest of the exact external JSON backup artifact used as migration
input. The operator command creates the backup before applying the migration;
the backend rejects the operation if the current source does not match that
fingerprint.

### Migration record

The V7 `financial_snapshot_workspace_migration` row connecting a source kind,
fingerprint, effective version, destination owner/workspace, migrated snapshot,
expected counts, status, and timestamps. It contains metadata only, not another
copy of financial values.

### Rollback eligible

An applied migration whose relational snapshot is still active and whose
version and all record/audit counts still match its migration record. Guarded
rollback deactivates that snapshot and retains its rows. Any later target change
removes rollback eligibility and causes rollback to fail closed.

### Seed data

Committed example data is synthetic input for tests, demos, and explicit
migration. Runtime startup and account creation never seed a financial
workspace implicitly. Existing personal JSON enters PostgreSQL only through the
explicit migration workflow.

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
