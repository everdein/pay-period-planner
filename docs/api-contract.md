# Financials API Contract

## Scope and Conventions

- Base path: `/api/v1/financials`
- Media type: `application/json`
- Dates: ISO local dates (`YYYY-MM-DD`), without time zone
- Money: JSON decimal numbers mapped to Java `BigDecimal`
- Authentication/authorization: HTTP Basic authentication with the
  `FINANCIALS` application role is required for every endpoint under this base
  path
- Concurrency: full-snapshot `PUT` requests use an optimistic `version` token
- Primary UI save boundary: the complete financial snapshot

This document describes the current contract. Backend DTO records and
controller annotations remain authoritative.

Local development defaults are `financial_app` /
`financial_app_local_password`. Override them with `FINANCIALS_API_USERNAME` and
`FINANCIALS_API_PASSWORD` before starting the backend.

Cross-origin browser calls are denied unless `FINANCIALS_ALLOWED_ORIGINS`
contains exact allowed origins. Request bodies above
`FINANCIALS_MAX_REQUEST_BYTES` are rejected with `413 Payload Too Large` before
controller handling; the local default is `1048576` bytes.

Actuator exposure is intentionally narrow: only `/actuator/health` and
`/actuator/info` are exposed by default. Activating the `prod` profile requires
the `postgres` profile, non-default API credentials, and no wildcard CORS
origin.

## Endpoints

| Method   | Path                                           | Success        | Purpose                                    |
| -------- | ---------------------------------------------- | -------------- | ------------------------------------------ |
| `GET`    | `/api/v1/financials`                           | `200` snapshot | Load the calculated current workspace      |
| `GET`    | `/api/v1/financials/export`                    | `200` export   | Download the saved source snapshot as JSON |
| `GET`    | `/api/v1/financials/export/csv`                | `200` CSV      | Download the saved source snapshot as CSV  |
| `GET`    | `/api/v1/financials/export/xlsx`               | `200` XLSX     | Download the saved source snapshot as XLSX |
| `POST`   | `/api/v1/financials/import/csv`                | `200` snapshot | Restore the full snapshot from CSV         |
| `POST`   | `/api/v1/financials/import/xlsx`               | `200` snapshot | Restore the full snapshot from XLSX        |
| `PUT`    | `/api/v1/financials`                           | `200` snapshot | Replace and return the complete snapshot   |
| `POST`   | `/api/v1/financials/bills`                     | `201` bill     | Add one monthly bill immediately           |
| `PUT`    | `/api/v1/financials/bills/{id}`                | `200` bill     | Replace one existing monthly bill          |
| `DELETE` | `/api/v1/financials/bills/{id}`                | `204` empty    | Delete one existing monthly bill           |
| `POST`   | `/api/v1/financials/annual-withdrawals`        | `201` record   | Add one annual withdrawal immediately      |
| `PUT`    | `/api/v1/financials/annual-withdrawals/{id}`   | `200` record   | Replace one annual withdrawal              |
| `DELETE` | `/api/v1/financials/annual-withdrawals/{id}`   | `204` empty    | Delete one annual withdrawal               |
| `POST`   | `/api/v1/financials/asset-accounts`            | `201` record   | Add one asset account immediately          |
| `PUT`    | `/api/v1/financials/asset-accounts/{id}`       | `200` record   | Replace one asset account                  |
| `DELETE` | `/api/v1/financials/asset-accounts/{id}`       | `204` empty    | Delete one asset account                   |
| `POST`   | `/api/v1/financials/debt-accounts`             | `201` record   | Add one debt account immediately           |
| `PUT`    | `/api/v1/financials/debt-accounts/{id}`        | `200` record   | Replace one debt account                   |
| `DELETE` | `/api/v1/financials/debt-accounts/{id}`        | `204` empty    | Delete one debt account                    |
| `POST`   | `/api/v1/financials/income-summary-items`      | `201` record   | Add one income summary item immediately    |
| `PUT`    | `/api/v1/financials/income-summary-items/{id}` | `200` record   | Replace one income summary item            |
| `DELETE` | `/api/v1/financials/income-summary-items/{id}` | `204` empty    | Delete one income summary item             |
| `POST`   | `/api/v1/financials/income-events`             | `201` record   | Add one income event immediately           |
| `PUT`    | `/api/v1/financials/income-events/{id}`        | `200` record   | Replace one income event                   |
| `DELETE` | `/api/v1/financials/income-events/{id}`        | `204` empty    | Delete one income event                    |
| `POST`   | `/api/v1/financials/important-dates`           | `201` record   | Add one important date immediately         |
| `PUT`    | `/api/v1/financials/important-dates/{id}`      | `200` record   | Replace one important date                 |
| `DELETE` | `/api/v1/financials/important-dates/{id}`      | `204` empty    | Delete one important date                  |
| `PUT`    | `/api/v1/financials/pay-period`                | `200` snapshot | Replace pay-period anchor dates            |

The granular endpoints persist through the same aggregate store as the full
snapshot. The current browser workspace primarily uses `GET` and full-snapshot
`PUT`. ADR 0012 records the compatibility and concurrency tradeoffs for the
granular record APIs.

Every `GET /api/v1/financials` response includes the current snapshot
`version`. Clients must echo that value in `PUT /api/v1/financials`. If another
write has committed first, the backend rejects the stale save with `409
Conflict` and leaves the newer snapshot intact.

## Complete Snapshot

### Request

```json
{
  "version": 1,
  "payPeriodStart": "2026-07-01",
  "payPeriodEnd": "2026-07-14",
  "bills": [],
  "annualWithdrawals": [],
  "assetCategories": [],
  "debtAccounts": [],
  "incomeSummaryItems": [],
  "incomeEvents": [],
  "importantDates": []
}
```

| Field                | Required | Meaning                                            |
| -------------------- | -------- | -------------------------------------------------- |
| `version`            | Yes      | Current snapshot version returned by the last GET  |
| `payPeriodStart`     | Yes      | Stored pay-period anchor start                     |
| `payPeriodEnd`       | Yes      | Stored pay-period anchor end; cannot precede start |
| `bills`              | Yes      | Monthly bill source records                        |
| `annualWithdrawals`  | No       | Null/missing is normalized to an empty list        |
| `assetCategories`    | Yes      | Categories containing asset accounts               |
| `debtAccounts`       | No       | Null/missing is normalized to an empty list        |
| `incomeSummaryItems` | No       | Null/missing is normalized to an empty list        |
| `incomeEvents`       | Yes      | Dated income-calendar source records               |
| `importantDates`     | Yes      | Dated event source records                         |

`PUT /api/v1/financials` replaces every persisted collection after the supplied
`version` matches the current server version. Omitting an optional collection
therefore clears it; omitting a required collection yields `400`. Derived
response fields are not request fields.

### Response

```json
{
  "version": 1,
  "payPeriodStart": "2026-07-01",
  "payPeriodEnd": "2026-07-14",
  "totalMonthlyExpenses": 0,
  "paidTotal": 0,
  "unpaidTotal": 0,
  "payPeriodTotal": 0,
  "totalAnnualWithdrawals": 0,
  "annualPayPeriodTotal": 0,
  "totalTrackedAssets": 0,
  "totalDebt": 0,
  "netWorth": 0,
  "assetCategories": [],
  "debtAccounts": [],
  "incomeSummaryItems": [],
  "bills": [],
  "annualWithdrawals": [],
  "incomeEvents": [],
  "importantDates": []
}
```

The returned pay period is the current calculated window. The service shifts
the stored anchor window by its inclusive length until it contains today; that
shift is a response calculation and does not itself persist new anchors.

Derived top-level values:

- `totalMonthlyExpenses`: sum of all bill amounts
- `paidTotal`: sum of paid bills
- `unpaidTotal`: monthly total minus paid total
- `payPeriodTotal`: bills whose derived due date is inside the current period
- `totalAnnualWithdrawals`: sum of annual-withdrawal amounts
- `annualPayPeriodTotal`: annual withdrawals due inside the current period
- `totalTrackedAssets`: sum of asset-category totals
- `totalDebt`: sum of debt balances
- `netWorth`: tracked assets minus debt

## Snapshot Export and Tabular Restore

`GET /api/v1/financials/export` is a read-only backup endpoint. It returns an
attachment with `Cache-Control: no-store` and a filename like
`financial-snapshot-v3.json`.

The response envelope is:

```json
{
  "format": "end-to-end-app.financial-snapshot.v1",
  "exportedAt": "2026-07-11T10:15:30Z",
  "snapshot": {
    "version": 3,
    "payPeriodStart": "2026-07-01",
    "payPeriodEnd": "2026-07-14",
    "bills": [],
    "annualWithdrawals": [],
    "assetCategories": [],
    "debtAccounts": [],
    "incomeSummaryItems": [],
    "incomeEvents": [],
    "importantDates": []
  }
}
```

`snapshot` mirrors the full-snapshot request shape and preserves saved source
IDs. It intentionally excludes calculated totals, labels, due dates,
pay-period flags, monthly check counts, and projection-only fields.

CSV and XLSX use the same one-sheet tabular exchange format:

```http
GET /api/v1/financials/export/csv
GET /api/v1/financials/export/xlsx
POST /api/v1/financials/import/csv
POST /api/v1/financials/import/xlsx
```

Downloads are attachments named like `financial-snapshot-v3.csv` or
`financial-snapshot-v3.xlsx`, also with `Cache-Control: no-store`. Imports
replace the complete saved snapshot through the same service path as
`PUT /api/v1/financials`, so the imported `version` must match the current
server version. Stale imports return `409 Conflict`; successful imports
increment the snapshot version and return the calculated snapshot response.

Tabular columns are fixed and must remain in this order:

```text
recordType,version,id,payPeriodStart,payPeriodEnd,bill,dueDay,month,day,amount,account,paid,categoryKey,categoryLabel,company,category,interval,date,label,event,type,checkNumber
```

The first data row must have `recordType` = `snapshot` and must provide
`version`, `payPeriodStart`, and `payPeriodEnd`. Remaining rows use these
record types: `bill`, `annualWithdrawal`, `assetAccount`, `debtAccount`,
`incomeSummaryItem`, `incomeEvent`, and `importantDate`. Dates are ISO local
dates (`YYYY-MM-DD`). Blank IDs are treated as new records during restore.
XLSX imports read the first worksheet and expect the same columns; date cells
should remain ISO text.

Exports and import files may contain personal financial data and must be
handled like local profile files and database backups. Do not commit them or
store them in repository folders.

## Nested Types

### Bill snapshot request

```json
{
  "id": null,
  "bill": "Synthetic utility",
  "dueDay": 15,
  "amount": 100.0,
  "account": "Example checking",
  "paid": false
}
```

- `id`: nullable. Null or non-positive IDs receive a positive ID during
  aggregate replacement.
- `bill`, `account`: required, not blank, and trimmed by the service.
- `dueDay`: integer from 1–31.
- `amount`: required and zero or greater.
- `paid`: Boolean planning state.

Bill responses add `dueLabel`, derived `dueDate`, and `inPayPeriod`.

### Annual withdrawal request

```json
{
  "id": null,
  "bill": "Synthetic annual fee",
  "month": 12,
  "day": 31,
  "amount": 50.0,
  "account": "Example checking",
  "paid": false
}
```

Month must be 1–12, day 1–31, amount nonnegative, and text fields not blank.
Responses add `dateLabel`, a year-specific `dueDate`, and `inPayPeriod`.

### Asset category request

```json
{
  "key": "cash-savings",
  "label": "Cash & Savings",
  "accounts": [
    {
      "id": null,
      "account": "Example savings",
      "company": "Example institution",
      "amount": 1000.0
    }
  ]
}
```

Category key, label, and accounts list are required. Account name, company, and
nonnegative amount are required. Responses add each category’s derived `total`.

### Debt account request

```json
{
  "id": null,
  "account": "Example card",
  "company": "Example issuer",
  "amount": 250.0
}
```

Account, company, and nonnegative amount are required.

### Income summary item request

```json
{
  "id": null,
  "category": "Net Income",
  "interval": "Bi-Weekly",
  "amount": 1500.0
}
```

Category, interval, and nonnegative amount are required. `Net Income` /
`Bi-Weekly` is the name-based primary paycheck source used by projections.

### Income event request

```json
{
  "id": null,
  "date": "2026-07-10",
  "label": "Synthetic paycheck",
  "type": "Paycheck",
  "checkNumber": 1
}
```

Date, label, and type are required. `checkNumber` is nullable; when present it
must be at least 1. Responses add `checksInMonth`, the number of events with a
non-null check number in that calendar month.

### Important date request

```json
{
  "id": null,
  "date": "2026-12-31",
  "event": "Synthetic reminder",
  "type": "Reminder"
}
```

Date, event, and type are required.

## Granular Request Bodies

### Create or update a bill

`POST /bills` and `PUT /bills/{id}` accept the bill fields without an `id`:

```json
{
  "bill": "Synthetic utility",
  "dueDay": 15,
  "amount": 100.0,
  "account": "Example checking",
  "paid": false
}
```

An update or delete path ID must be a positive whole number. A non-positive or
nonnumeric path ID returns `400`; a positive but absent ID returns `404`.

The same create/update/delete pattern applies to the other record collections.
Requests omit `id`; creates assign a new positive ID, updates preserve the path
ID, non-positive path IDs return `400`, and absent update/delete IDs return
`404`.

### Create or update an annual withdrawal

`POST /annual-withdrawals` and `PUT /annual-withdrawals/{id}`:

```json
{
  "bill": "Synthetic annual fee",
  "month": 12,
  "day": 31,
  "amount": 50.0,
  "account": "Example checking",
  "paid": false
}
```

Responses use the annual-withdrawal response shape, including `dateLabel`,
derived `dueDate`, and `inPayPeriod`.

### Create or update an asset account

`POST /asset-accounts` and `PUT /asset-accounts/{id}`:

```json
{
  "categoryKey": "cash-savings",
  "categoryLabel": "Cash & Savings",
  "account": "Example savings",
  "company": "Example institution",
  "amount": 1000.0
}
```

The granular asset-account response is flat so a single record can carry its
category:

```json
{
  "id": 10,
  "categoryKey": "cash-savings",
  "categoryLabel": "Cash & Savings",
  "account": "Example savings",
  "company": "Example institution",
  "amount": 1000.0
}
```

Snapshot responses remain grouped under `assetCategories`.

### Create or update a debt account

`POST /debt-accounts` and `PUT /debt-accounts/{id}`:

```json
{
  "account": "Example card",
  "company": "Example issuer",
  "amount": 250.0
}
```

### Create or update an income summary item

`POST /income-summary-items` and `PUT /income-summary-items/{id}`:

```json
{
  "category": "Net Income",
  "interval": "Bi-Weekly",
  "amount": 1500.0
}
```

### Create or update an income event

`POST /income-events` and `PUT /income-events/{id}`:

```json
{
  "date": "2026-07-10",
  "label": "Synthetic paycheck",
  "type": "Paycheck",
  "checkNumber": 1
}
```

Responses include `checksInMonth`, recalculated after the write.

### Create or update an important date

`POST /important-dates` and `PUT /important-dates/{id}`:

```json
{
  "date": "2026-12-31",
  "event": "Synthetic reminder",
  "type": "Reminder"
}
```

### Update pay-period anchors

```json
{
  "startDate": "2026-07-01",
  "endDate": "2026-07-14"
}
```

Both dates are required, and end cannot precede start.

## Normalization

The service guarantees three name-based records used by projections:

- A bill named `Rent`
- An asset account named `Rent Reserve` under cash/savings
- An income item with category `Net Income` and interval `Bi-Weekly`

Matching is trimmed and case-insensitive. Legacy names containing “rent” may
be normalized. If an anchor is absent, a zero-valued record can appear in the
response with a temporary negative ID. A later full-snapshot save sends that
record as new and assigns a positive ID.

Consumers must not treat these labels as freely interchangeable presentation
text without updating the normalization and projection contract.

## Errors

Handled errors use Spring `ProblemDetail` JSON (`application/problem+json`).

Validation failures return `400`:

```json
{
  "title": "Invalid request",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/api/v1/financials",
  "errors": ["bills[0].bill: Bill name is required"]
}
```

Null elements inside snapshot record arrays are validation failures. For
example, `{"bills":[null]}` returns `400` with an error for `bills[0]`.

Malformed or unreadable request bodies return `400` without echoing parser
details or submitted financial values:

```json
{
  "title": "Malformed request",
  "status": 400,
  "detail": "Request body is malformed or cannot be parsed",
  "instance": "/api/v1/financials/bills"
}
```

Nonnumeric path variables, such as `/bills/not-a-number`, return `400` with
title `Invalid request`. Unsupported request media types return `415` with
title `Unsupported media type`.

Service-generated `ResponseStatusException` errors preserve their HTTP status
and reason, including:

- `400` when pay-period end precedes start
- `400` when a granular record update/delete path ID is not positive
- `409` when a full snapshot save uses a stale `version`
- `404` when a granular record update/delete ID is absent

Unauthenticated financial API requests return `401`. Oversized requests return
`413 Payload Too Large` with a generic text response before body parsing.

An `IllegalStateException` while processing persistence is converted to `500`
with title `Persistence failure` and generic detail. Malformed-body and
persistence errors intentionally avoid echoing internal exception text,
snapshot contents, or submitted financial values.

## Compatibility Rules

- Keep frontend endpoint types and backend DTOs aligned.
- Treat fields removed from a full-snapshot request as deleted persisted data.
- Treat the snapshot `version` as an API concurrency token. Full-snapshot save
  clients must reload after `409 Conflict` before retrying.
- Do not add derived response fields to persistence without an explicit
  contract decision.
- Preserve decimal precision through the backend; JavaScript consumers still
  receive JSON numbers.
- Add contract tests for field, validation, status, or endpoint changes.
- Update this document and add a new API version when a breaking change cannot
  remain compatible under `/api/v1`.
