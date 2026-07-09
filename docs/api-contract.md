# Financials API Contract

## Scope and Conventions

- Base path: `/api/v1/financials`
- Media type: `application/json`
- Dates: ISO local dates (`YYYY-MM-DD`), without time zone
- Money: JSON decimal numbers mapped to Java `BigDecimal`
- Authentication/authorization: none
- Concurrency: last write wins; no ETag or request version
- Primary UI save boundary: the complete financial snapshot

This document describes the current contract. Backend DTO records and
controller annotations remain authoritative.

## Endpoints

| Method   | Path                            | Success        | Purpose                                  |
| -------- | ------------------------------- | -------------- | ---------------------------------------- |
| `GET`    | `/api/v1/financials`            | `200` snapshot | Load the calculated current workspace    |
| `PUT`    | `/api/v1/financials`            | `200` snapshot | Replace and return the complete snapshot |
| `POST`   | `/api/v1/financials/bills`      | `201` bill     | Add one monthly bill immediately         |
| `PUT`    | `/api/v1/financials/bills/{id}` | `200` bill     | Replace one existing monthly bill        |
| `DELETE` | `/api/v1/financials/bills/{id}` | `204` empty    | Delete one existing monthly bill         |
| `PUT`    | `/api/v1/financials/pay-period` | `200` snapshot | Replace pay-period anchor dates          |

The granular endpoints persist through the same aggregate store as the full
snapshot. The current browser workspace primarily uses `GET` and full-snapshot
`PUT`.

## Complete Snapshot

### Request

```json
{
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
| `payPeriodStart`     | Yes      | Stored pay-period anchor start                     |
| `payPeriodEnd`       | Yes      | Stored pay-period anchor end; cannot precede start |
| `bills`              | Yes      | Monthly bill source records                        |
| `annualWithdrawals`  | No       | Null/missing is normalized to an empty list        |
| `assetCategories`    | Yes      | Categories containing asset accounts               |
| `debtAccounts`       | No       | Null/missing is normalized to an empty list        |
| `incomeSummaryItems` | No       | Null/missing is normalized to an empty list        |
| `incomeEvents`       | Yes      | Dated income-calendar source records               |
| `importantDates`     | Yes      | Dated event source records                         |

`PUT /api/v1/financials` replaces every persisted collection. Omitting an
optional collection therefore clears it; omitting a required collection yields
`400`. Derived response fields are not request fields.

### Response

```json
{
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

An update or delete for an absent ID returns `404`.

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

Service-generated `ResponseStatusException` errors preserve their HTTP status
and reason, including:

- `400` when pay-period end precedes start
- `404` when a granular bill update/delete ID is absent

An `IllegalStateException` while processing persistence is converted to `500`
with title `Persistence failure` and generic detail. Internal financial data
and implementation exceptions must not be added to public error details.

## Compatibility Rules

- Keep frontend endpoint types and backend DTOs aligned.
- Treat fields removed from a full-snapshot request as deleted persisted data.
- Do not add derived response fields to persistence without an explicit
  contract decision.
- Preserve decimal precision through the backend; JavaScript consumers still
  receive JSON numbers.
- Add contract tests for field, validation, status, or endpoint changes.
- Update this document and add a new API version when a breaking change cannot
  remain compatible under `/api/v1`.
