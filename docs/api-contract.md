# Financials API Contract

## Scope and Conventions

- Base path: `/api/v1/financials`
- Media type: `application/json`
- Dates: ISO date-only values (`YYYY-MM-DD`); the saved IANA planning zone
  determines `currentDate`, while persisted dates never carry an offset
- Money: JSON decimal numbers mapped to Java `BigDecimal`
- Authentication/authorization: financial routes require a valid account
  session with `WORKSPACE` authority
- Concurrency: full-snapshot `PUT` requests use an optimistic `version` token
- Primary UI save boundary: the complete financial snapshot

This document describes the current contract. Backend DTO records and
controller annotations remain authoritative.

Operator Basic-auth development defaults are `financial_app` /
`financial_app_local_password`. Override them with `FINANCIALS_API_USERNAME` and
`FINANCIALS_API_PASSWORD` before starting the backend.
These credentials protect migration-admin and metrics routes only; they are not
PostgreSQL database or financial-account credentials. Failed financial-session
authentication returns `401` without a browser Basic-auth challenge.

## Account And Session Foundation

The account API is exposed under `/api/v1/auth`:

| Method | Path                   | Success       | Purpose                               |
| ------ | ---------------------- | ------------- | ------------------------------------- |
| `GET`  | `/api/v1/auth/csrf`    | `200` token   | Bootstrap cookie-auth CSRF protection |
| `POST` | `/api/v1/auth/signup`  | `201` session | Create user, workspace, and session   |
| `POST` | `/api/v1/auth/signin`  | `200` session | Verify credentials and create session |
| `GET`  | `/api/v1/auth/session` | `200` session | Recover the current browser session   |
| `POST` | `/api/v1/auth/signout` | `204` empty   | Revoke and expire the current session |

Signup accepts `email`, `password`, and `displayName`; sign-in accepts `email`
and `password`. Email uniqueness is case-insensitive. Passwords must be 12-72
characters and no more than 72 UTF-8 bytes because the current adaptive encoder
uses bcrypt. Signup creates one `Personal` workspace with an `owner`
membership.

Successful signup and sign-in set a `financials_session` cookie with
`HttpOnly`, `SameSite=Strict`, `Path=/`, and the configured lifetime. The raw
opaque token is never returned in JSON and only its SHA-256 hash is stored.
`FINANCIALS_SESSION_DURATION` defaults to `7d` and
`FINANCIALS_SESSION_COOKIE_SECURE` must be `true` under the `prod` profile.

Before any account or financial `POST`/`PUT`/`DELETE`, clients call
`GET /api/v1/auth/csrf`, retain its `HttpOnly` CSRF cookie, and send the returned
`token` in the returned `headerName` (`X-XSRF-TOKEN`). The frontend obtains a
fresh proof for every mutation and never retries a write after submission.
Missing or invalid CSRF proof returns `403`.

Session responses contain `userId`, `email`, `displayName`, `expiresAt`, and
the current `workspaces` list with workspace `id`, `name`, and membership
`role`. A valid session receives `WORKSPACE` authority and may access only a
relational financial snapshot owned by one of those memberships. When there is
one membership it is selected automatically. Accounts with multiple
memberships send `X-Workspace-ID`; malformed or missing ambiguous selections
return `400`, and selecting a non-membership returns `403`. A workspace without
an explicitly created or migrated snapshot returns `404` and is never silently
seeded from personal JSON. An authenticated member may create an empty version-1
snapshot with `POST /api/v1/financials`; existing data still uses the explicit
operator migration workflow.

The frontend captures the selected workspace ID for each financial request and
sends it as `X-Workspace-ID`, including when only one membership is available.
It does not derive the header from mutable module-global workspace state, so a
later selection cannot redirect an earlier in-flight request. Clearing the
account session also aborts active frontend API requests.

## Workspace Migration Operations

The operator-only transition API is exposed under
`/api/v1/admin/workspace-migrations`. Every route requires operator Basic
authentication with `FINANCIALS` authority. Account-session `WORKSPACE`
principals receive `403` and unauthenticated requests receive `401`.

| Method | Path                      | Confirmation | Purpose                                                                 |
| ------ | ------------------------- | ------------ | ----------------------------------------------------------------------- |
| `GET`  | `/legacy-jsonb-backup`    | None         | Download the effective active JSONB storage envelope                    |
| `POST` | `/apply/json-file`        | `APPLY`      | Migrate the request JSON envelope into an empty owned workspace         |
| `POST` | `/apply/jsonb-document`   | `APPLY`      | Migrate the current active JSONB document into an empty owned workspace |
| `GET`  | `/{migrationId}`          | None         | Read metadata-only migration verification                               |
| `POST` | `/{migrationId}/rollback` | `ROLLBACK`   | Deactivate an unchanged migrated snapshot                               |

Apply requests require `expectedFingerprint`, `destinationEmail`, and positive
`workspaceId` query parameters. The fingerprint is the lowercase or uppercase
64-character SHA-256 digest of the exact external backup artifact. The
confirmation value is sent in `X-Confirm-Financial-Migration`. A JSON-file apply
uses `Content-Type: application/json` and the exact backed-up file as its body.

The destination email must identify the active owner of the named workspace,
and that workspace must not have an active relational financial snapshot.
Apply validates source IDs, required fields, date ranges, numeric precision,
and audit metadata, then writes the snapshot, all record families, source audit
events, and migration history in one transaction. The legacy source is not
changed or deactivated.

Migration responses contain IDs, source kind and fingerprint, source/current
versions, destination account/workspace metadata, expected/current record
counts, status/timestamps, `metadataMatches`, `snapshotActive`, and
`rollbackEligible`. They never contain financial values. Rollback succeeds only
while the migrated snapshot remains active and its version and counts match the
migration record; otherwise it returns `409`. Successful rollback retains the
relational rows and history but marks the snapshot inactive and the migration
`rolled_back`.

Use `scripts/migrate-financial-snapshot-to-workspace.ps1` and
`scripts/rollback-workspace-snapshot-migration.ps1` as the supported operator
workflow because they create the external backup and perform an independent
metadata verification request.

Cross-origin browser calls are denied unless `FINANCIALS_ALLOWED_ORIGINS`
contains exact allowed origins. Request bodies above
`FINANCIALS_MAX_REQUEST_BYTES` are rejected with `413 Payload Too Large` before
controller handling; the local default is `1048576` bytes.

Clients may send `X-Request-ID` using 1–100 letters, numbers, periods,
underscores, colons, or hyphens. The backend replaces missing or unsafe values
and returns the final ID in every response header. CORS allows and exposes this
header for configured origins and allows `X-Workspace-ID` as a request header.

Actuator exposure is intentionally narrow: `/actuator/health` and
`/actuator/info` are public, `/actuator/metrics` requires operator
credentials, and other Actuator endpoints are denied. Activating the `prod`
profile requires non-default operator credentials, no wildcard CORS origin,
and secure session cookies.

## Endpoints

| Method | Path                                                   | Success        | Purpose                                          |
| ------ | ------------------------------------------------------ | -------------- | ------------------------------------------------ |
| `POST` | `/api/v1/financials`                                   | `201` snapshot | Create an empty workspace snapshot               |
| `GET`  | `/api/v1/financials`                                   | `200` snapshot | Load the calculated current workspace            |
| `GET`  | `/api/v1/financials/history`                           | `200` history  | Load recent saved-change audit events            |
| `GET`  | `/api/v1/financials/export`                            | `200` backup   | Download the saved source snapshot as JSON       |
| `POST` | `/api/v1/financials/restore?expectedVersion=<version>` | `200` snapshot | Restore a JSON backup against the target version |
| `PUT`  | `/api/v1/financials`                                   | `200` snapshot | Replace and return the complete snapshot         |

ADR 0016 retired the record-level and pay-period mutation routes. The
version-checked full-snapshot `PUT` is the sole interactive financial mutation
boundary. The explicit restore operation delegates to the same aggregate
validation and optimistic replacement path.

`POST /api/v1/financials` accepts `startDate`, `endDate`, and
`planningSettings`. The settings require a supported `payCadence` and valid
IANA `timeZone`. It persists version `1` with zero-value initial records,
planning settings, and projection roles for the selected authenticated
workspace and returns the calculated snapshot. These records
are application structure, not imported financial values. The
dates are required and the end cannot precede the start. A workspace that
already has an active snapshot returns `409 Conflict`, including concurrent
creation attempts. This endpoint never reads example, personal JSON, or legacy
JSONB data. The creation command returns its successfully persisted aggregate
for response presentation rather than issuing a second current-snapshot query.

Every `GET /api/v1/financials` response includes the current snapshot
`version`. Clients must echo that value in `PUT /api/v1/financials`. If another
write has committed first, the backend rejects the stale save with `409
Conflict` and leaves the newer snapshot intact.

## Audit History

`GET /api/v1/financials/history` returns the newest saved-change events first.
The optional `limit` query parameter defaults to `50` and must be between `1`
and `100`. The limit is applied by PostgreSQL, and this route does not load the
current financial snapshot.

```http
GET /api/v1/financials/history?limit=10
```

```json
{
  "events": [
    {
      "id": 1,
      "occurredAt": "2026-07-12T10:15:30Z",
      "action": "CREATE",
      "resourceType": "monthly-bill",
      "resourceId": 42,
      "versionBefore": 7,
      "versionAfter": 8,
      "summary": "Created monthly bill",
      "projectionSummary": {
        "payPeriodStart": "2026-07-01",
        "payPeriodEnd": "2026-07-14",
        "monthlyBillCount": 3,
        "annualWithdrawalCount": 1,
        "assetAccountCount": 2,
        "debtAccountCount": 1,
        "incomeSummaryItemCount": 2,
        "incomeEventCount": 2,
        "importantDateCount": 1,
        "totalMonthlyExpenses": 1500,
        "totalAnnualWithdrawals": 99,
        "totalTrackedAssets": 15000,
        "totalDebt": 500,
        "netWorth": 14500
      }
    }
  ]
}
```

Audit events are appended when a persisted write commits and the snapshot
version advances. They intentionally store coarse metadata, record type/ID,
version movement, a short action summary, and aggregate projection totals; they
do not store request bodies or field-level before/after personal-finance
diffs. The history is part of the persisted local data envelope, so it is
personal financial data when the underlying snapshot is personal.

## Complete Snapshot

### Request

```json
{
  "version": 1,
  "payPeriodStart": "2026-07-01",
  "payPeriodEnd": "2026-07-14",
  "planningSettings": {
    "payCadence": "BIWEEKLY",
    "timeZone": "America/New_York"
  },
  "projectionRoles": {
    "rentBillId": 1,
    "rentReserveAssetAccountId": 1,
    "primaryPaycheckIncomeSummaryItemId": 1
  },
  "bills": [
    {
      "id": 1,
      "bill": "Housing",
      "dueDay": 1,
      "amount": 0,
      "account": "Example checking",
      "paid": false
    }
  ],
  "annualWithdrawals": [],
  "assetCategories": [
    {
      "key": "cash-savings",
      "label": "Cash & Savings",
      "accounts": [
        {
          "id": 1,
          "account": "Housing reserve",
          "company": "Example institution",
          "amount": 0
        }
      ]
    }
  ],
  "debtAccounts": [],
  "incomeSummaryItems": [
    {
      "id": 1,
      "category": "Primary paycheck",
      "interval": "Every two weeks",
      "amount": 0
    }
  ],
  "incomeEvents": [],
  "importantDates": []
}
```

| Field                | Required | Meaning                                            |
| -------------------- | -------- | -------------------------------------------------- |
| `version`            | Yes      | Current snapshot version returned by the last GET  |
| `payPeriodStart`     | Yes      | Stored pay-period anchor start                     |
| `payPeriodEnd`       | Yes      | Stored pay-period anchor end; cannot precede start |
| `planningSettings`   | Yes*     | Pay cadence and IANA planning time zone            |
| `projectionRoles`    | Yes*     | Typed IDs used by the paycheck projection          |
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

`projectionRoles` is required for current clients. Each value must reference
exactly one record in its corresponding request collection. Negative IDs are
allowed so a newly added record can be selected and saved in one request; the
response returns the assigned positive IDs. Legacy backup input without roles is
accepted through the compatibility normalizer (`Yes*`).

`planningSettings.payCadence` must be `WEEKLY`, `BIWEEKLY`, `SEMIMONTHLY`, or
`MONTHLY`. `planningSettings.timeZone` must be a Java-supported IANA zone.
Legacy backup and migration input without settings defaults once to
`BIWEEKLY` and `UTC` (`Yes*`).

### Response

```json
{
  "version": 1,
  "payPeriodStart": "2026-07-01",
  "payPeriodEnd": "2026-07-14",
  "currentDate": "2026-07-15",
  "planningSettings": {
    "payCadence": "BIWEEKLY",
    "timeZone": "America/New_York"
  },
  "projectionRoles": {
    "rentBillId": 1,
    "rentReserveAssetAccountId": 1,
    "primaryPaycheckIncomeSummaryItemId": 1
  },
  "totalMonthlyExpenses": 0,
  "paidTotal": 0,
  "unpaidTotal": 0,
  "payPeriodTotal": 0,
  "totalAnnualWithdrawals": 0,
  "annualPayPeriodTotal": 0,
  "totalTrackedAssets": 0,
  "totalDebt": 0,
  "netWorth": 0,
  "assetCategories": [
    {
      "key": "cash-savings",
      "label": "Cash & Savings",
      "total": 0,
      "accounts": [
        {
          "id": 1,
          "account": "Housing reserve",
          "company": "Example institution",
          "amount": 0
        }
      ]
    }
  ],
  "debtAccounts": [],
  "incomeSummaryItems": [
    {
      "id": 1,
      "category": "Primary paycheck",
      "interval": "Every two weeks",
      "amount": 0
    }
  ],
  "bills": [
    {
      "id": 1,
      "bill": "Housing",
      "dueDay": 1,
      "dueLabel": "1st",
      "dueDate": "2026-07-01",
      "amount": 0,
      "account": "Example checking",
      "paid": false,
      "inPayPeriod": true
    }
  ],
  "annualWithdrawals": [],
  "incomeEvents": [],
  "importantDates": []
}
```

The returned `currentDate` is derived once from the request clock instant and
the snapshot's planning time zone. The returned pay period is the current
calculated window: the service shifts the stored anchor window by its inclusive
length until it contains that same `currentDate`. The shift is a response
calculation and does not itself persist new anchors.

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

## Snapshot Backup and Restore

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
    "planningSettings": {
      "payCadence": "BIWEEKLY",
      "timeZone": "America/New_York"
    },
    "projectionRoles": {
      "rentBillId": 1,
      "rentReserveAssetAccountId": 1,
      "primaryPaycheckIncomeSummaryItemId": 1
    },
    "bills": [
      {
        "id": 1,
        "bill": "Housing",
        "dueDay": 1,
        "amount": 0,
        "account": "Example checking",
        "paid": false
      }
    ],
    "annualWithdrawals": [],
    "assetCategories": [
      {
        "key": "cash-savings",
        "label": "Cash & Savings",
        "accounts": [
          {
            "id": 1,
            "account": "Housing reserve",
            "company": "Example institution",
            "amount": 0
          }
        ]
      }
    ],
    "debtAccounts": [],
    "incomeSummaryItems": [
      {
        "id": 1,
        "category": "Primary paycheck",
        "interval": "Every two weeks",
        "amount": 0
      }
    ],
    "incomeEvents": [],
    "importantDates": []
  }
}
```

`snapshot` mirrors the full-snapshot request shape and preserves saved source
IDs. It intentionally excludes calculated totals, labels, due dates,
pay-period flags, monthly check counts, and projection-only fields.

Restore submits the exported envelope unchanged:

```http
POST /api/v1/financials/restore?expectedVersion=8
Content-Type: application/json
```

The request body is the complete JSON backup envelope shown above.
`snapshot.version` records the source backup version and is not used as the
target concurrency token. `expectedVersion` must be the current version of the
selected target workspace. The service validates the backup format and nested
snapshot, replaces the aggregate through the same path as
`PUT /api/v1/financials`, and persists the result at the next target version.

This separation allows an older backup to restore deliberately while still
preventing an unnoticed concurrent target write. A stale `expectedVersion`
returns `409 Conflict` and leaves the target unchanged. An unsupported `format`
or invalid nested snapshot returns `400`. Restore requires an authenticated
workspace session and CSRF proof.

JSON backup files may contain personal financial data and must be handled like
local profile files and database backups. Do not commit them or store them in
repository folders. They do not contain complete relational audit history and
do not replace database-native backup tooling.

## Nested Types

### Planning settings request

```json
{
  "payCadence": "SEMIMONTHLY",
  "timeZone": "America/Chicago"
}
```

Cadence controls income annualization and frontend recurring-payday semantics:
weekly uses 52 periods per year, biweekly 26, semimonthly 24, and monthly 12.
Calendar-month recurrence clamps requested days to shorter months. The time
zone controls the workspace's current planning date and active-period
calculation. It does not convert date-only financial values.

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

Category, interval, and nonnegative amount are required. The snapshot's
`primaryPaycheckIncomeSummaryItemId` role selects the item used by projections;
category and interval remain editable labels.

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

## Normalization

Current snapshots use projection role IDs and never infer runtime behavior from
display names. Role references are validated against their typed collections.

Legacy JSON, JSONB, or backup input without roles is upgraded at the boundary.
Historical rent and paycheck labels may be selected without renaming the
records. Missing responsibilities receive positive-ID, zero-value default
records, and the resulting role configuration is persisted on migration or the
next aggregate save.

## Errors

Handled errors use Spring `ProblemDetail` JSON (`application/problem+json`).

Validation failures return `400`:

```json
{
  "title": "Invalid request",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/api/v1/financials",
  "requestId": "77a5012a-aeda-47a6-8f2f-1f2314ab50d7",
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
  "instance": "/api/v1/financials"
}
```

Invalid query parameters, such as a nonnumeric audit-history `limit`, return
`400` with title `Invalid request`. Unsupported request media types return
`415` with title `Unsupported media type`.

Financial application exceptions are mapped by `ApiExceptionHandler` to their
HTTP status and safe detail, including:

- `400` when pay-period end precedes start
- `400` when a restore backup uses an unsupported format
- `409` when a full snapshot save uses a stale `version` or restore uses a
  stale target `expectedVersion`

Unauthenticated or invalid-session financial API requests return `401` without
a `WWW-Authenticate` challenge header. Operator Basic credentials do not grant
financial access and receive `403`. Oversized requests return `413 Payload Too
Large` with a generic text response before body parsing.

An `IllegalStateException` while processing persistence is converted to `500`
with title `Persistence failure` and generic detail. Malformed-body and
persistence errors intentionally avoid echoing internal exception text,
snapshot contents, or submitted financial values.

Handled Problem Detail responses include the same `requestId` returned in the
`X-Request-ID` response header. The frontend preserves the response `status`,
safe `detail`, optional `title`, and backend-confirmed `requestId` as separate
error fields. User-facing failures format the request reference from that
structured ID; status-based behavior never parses presentation messages.

## Compatibility Rules

- Keep frontend endpoint types and backend DTOs aligned.
- Keep `currentDate`, cadence, and planning-zone behavior aligned across the
  backend calculator and frontend date helpers.
- Treat fields removed from a full-snapshot request as deleted persisted data.
- Treat the live snapshot `version` as an API concurrency token. Full-snapshot
  save clients must reload after `409 Conflict` before retrying. During restore,
  the backup's embedded version is source metadata and the separately supplied
  `expectedVersion` protects the target workspace.
- Do not add derived response fields to persistence without an explicit
  contract decision.
- Preserve decimal precision through the backend; JavaScript consumers still
  receive JSON numbers.
- Add contract tests for field, validation, status, or endpoint changes.
- Update this document and add a new API version when a breaking change cannot
  remain compatible under `/api/v1`.
