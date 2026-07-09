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

### Income summary item

A category/interval/amount source record. The distinguished source item is
`Net Income` / `Bi-Weekly`; projections use it as paycheck income. Additional
summary rows may be derived for presentation from that source.

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
tracks. It controls save/cancel actions. It is not a server version or a
complete conflict-detection mechanism.

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

The operation is last-write-wins; there is no browser-visible optimistic
concurrency token.

### Granular endpoints

The bill create/update/delete and pay-period routes under
`/api/v1/financials`. They mutate the same aggregate and persist immediately.
The current workspace UI primarily uses the full-snapshot save.

### Derived field

A response or presentation value calculated from persisted source fields.
Examples include totals, due dates, `inPayPeriod`, category totals,
`checksInMonth`, event statuses, and projection values. Derived fields must not
silently become new persistence inputs.

## Persistence Terms

### JSON profile

The default Spring profile. `JsonFinancialsSnapshotStore` reads and writes the
ignored `backend/data/financials.local.json`. If absent, it seeds from the
committed synthetic `financials.example.json`.

### PostgreSQL profile

The opt-in `postgres` Spring profile.
`PostgresFinancialsSnapshotStore` reads and writes the complete aggregate as
JSONB. Flyway applies schema migrations.

### Active snapshot document

The row in `financial_snapshot_document` where `active = true`. Its
`snapshot_json` contains the persisted aggregate. Saves update that row,
increment its version, and update its timestamp; an empty database receives
version 1 when seeded.

The version is storage metadata, not an API concurrency token.

### Normalized V1 tables

The relational tables introduced by
`V1__create_financials_schema.sql`. They are schema groundwork and are not the
active persistence path. Zero rows in these tables is expected while the JSONB
snapshot document contains data.

### Snapshot store

The `FinancialsSnapshotStore` interface with `load` and `save` operations.
Exactly one profile-specific implementation is active: JSON or PostgreSQL.

### Seed data

Initial data used only when the selected store has no existing snapshot.
Committed example data is synthetic. Local JSON may contain personal data and
can seed an empty local PostgreSQL store.

## Data Classification Terms

### Mock financial data

Synthetic values safe for source control, tests, examples, screenshots, and
shared diagnostics. The committed `financials.example.json` is the canonical
mock dataset.

### Personal financial data

Any real balances, income, bills, dates, account/company combinations, local
JSON, PostgreSQL rows, exports, logs, or screenshots. Keep it local and out of
commits, prompts to external services, CI artifacts, and documentation.

### Metadata-only inspection

Diagnosis using schema names, row counts, JSON keys/types, IDs, versions,
timestamps, and privileges without printing financial values. This is the
default PostgreSQL inspection scope.
