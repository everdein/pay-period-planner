# Pay Period Planner Web

React and TypeScript client for the Pay Period Planner household cash-flow
workspace. It owns account access, workspace selection, canonical draft state,
pay-period projections, and the accessible responsive planning experience.

The frontend demonstrates:

- explicit API boundaries and structured request failures
- Redux Toolkit session and server-snapshot state
- one reducer-owned financial draft with domain-focused hook facades
- optimistic save, stale-response, conflict, and in-flight edit handling
- pure cadence, date, and projection calculations with focused tests
- live-backend Playwright, WCAG, keyboard, and responsive verification

---

## Tech stack

### Core

- React 19
- TypeScript
- Vite
- Redux Toolkit
- React Redux

### Testing

- Vitest
- Testing Library
- jsdom
- V8 coverage

### Quality tooling

- ESLint
- Prettier
- Husky
- lint-staged
- cspell
- eslint-config-prettier

---

## Requirements

- Node.js 24+
- npm 10+
- Backend application running locally

Verify Node/npm are installed:

```sh
node -v
npm -v
```

---

## Installation

From the `frontend` directory:

```sh
npm install
```

---

## Running the application

From the `frontend` directory:

```sh
npm run dev
```

Frontend URL:

```text
http://localhost:3000
```

---

## Backend integration

During local development, API requests are proxied through the Vite dev server.

Frontend requests:

```text
/api/*
```

are automatically forwarded to:

```text
http://localhost:8080
```

This provides:

- clean frontend API calls
- no hard-coded backend URLs
- no local CORS configuration required

The backend exposes
account signup, sign-in, session recovery, and sign-out; the browser session is
held in an `HttpOnly`, `SameSite=Strict` cookie. The frontend stores no account
credentials or Basic token. It obtains fresh CSRF proof before each mutation,
sends an explicit `X-Workspace-ID` for the selected membership on every
financial request, and stores only that non-sensitive workspace preference in
browser session storage. Clearing the account session aborts active requests,
and Redux associates each request with its originating workspace and request ID
so a delayed completion cannot repopulate a newer account or workspace context.

New accounts begin with an empty `Personal` workspace. Existing data must be
explicitly migrated into that workspace; no personal or synthetic snapshot is
seeded during signup. A new user can select the current pay-period dates and
create an empty relational snapshot in the browser, then add the first income
or withdrawal and commit it through the versioned save workflow. If the backend
is unavailable, the account screen remains visible and reports the correlated
request reference.

Each frontend API request includes a generated `X-Request-ID`. Failed requests
preserve the HTTP status, safe problem detail, optional problem title, and
backend-confirmed ID as separate fields. Redux classifies financial failures
from the structured status, never from display text, and user-facing errors
surface the structured request reference so a failure can be matched to backend
logs.
Unexpected render errors are contained by an accessible recovery screen, while
the local browser reporter records only a reference ID, error category, and
JavaScript error type. It does not record error messages, stacks, Redux state,
URLs, credentials, or financial data. See `../docs/observability-guide.md`.

---

## Financials UI

The main application screen is a household cash-flow planning workspace with grouped
sidebar navigation for:

- Overview
- Projection
- Monthly Withdrawals
- Annual Withdrawals
- Income Summary
- Income Calendar
- Retirement
- Investments
- Cash & Savings
- Insurance / Benefits
- Debt
- Important Dates

The same typed section model drives both the desktop sidebar and the grouped
`Financial section` menu shown on smaller screens. The Overview dashboard
also provides direct entry points into projection, income, withdrawal, asset,
debt, payday, and important-date workflows without duplicating persistence or
draft state.

The Monthly Withdrawals tab supports:

- pay period start and end dates
- automatic pay period highlighting
- automatic pay period dates based on today's date and the saved schedule
- monthly, paid, unpaid, and in-period totals
- annual withdrawals due in the active pay period
- adding withdrawal rows
- editing existing withdrawal rows
- removing withdrawal rows
- resetting unsaved changes
- saving the full draft snapshot

The backend returns the pay period that contains today's date. If the user edits
the pay period dates and saves, those dates become the new schedule anchor for
future automatic updates.

The Projection tab derives a next-paycheck forecast from the current draft. Its
three workspace settings select the housing payment, housing reserve account,
and primary paycheck by stable record ID, independently of their editable labels.
It combines those inputs with next pay period bills, annual withdrawals due in
the period, and current debt to show cash after bills, a possible debt payment,
remaining debt, and a possible savings transfer after debt is covered. The
current pay period is shown only as supporting context. The Overview shows the
projection headline numbers. These values support household cash-flow planning;
they are not accounting records, transaction reconciliation, transfer
instructions, or financial advice.

Annual Withdrawals tracks recurring yearly charges with native calendar inputs
while storing month/day recurrence data. Income Summary tracks editable source
rows by category, interval, and amount, plus derived net/disposable income
values. Income Calendar tracks paydays and one-time income events, with derived
received/current/upcoming statuses. It generates weekly, biweekly,
semimonthly, or monthly paycheck rows for a selected year from the required
anchor date or dates and starting check number; by default the generator
replaces existing numbered income rows for that year while preserving one-time
income events. Month-based recurrence clamps dates to shorter months.

The asset and debt tabs support editable account rows. Asset categories show
category totals and an overall tracked-assets total. Debt contributes to total
debt and net worth. Important Dates tracks household reminders and notable dates,
with derived passed/next/upcoming statuses.

Displayed dates use `MM/DD/YYYY`. Editable date fields use native browser date
inputs. The workspace's saved IANA planning zone supplies the current planning
date returned by the backend, and frontend date-only arithmetic uses UTC
calendar operations so selected dates do not shift with browser offsets.

Selected projection-input records must be reassigned before removal. A new
temporary record can be selected and saved once because its negative draft ID
is remapped with the assigned positive ID in the aggregate response.

### Data flow

The frontend uses a draft/save pattern:

1. `fetchMonthlyExpenses` loads one snapshot into Redux from the backend.
2. `useFinancialsDraftWorkspace` synchronizes that snapshot into a
   feature-local reducer with one committed baseline and one editable draft.
3. Domain hooks retain only transient form and editing state. Their commands
   update the canonical draft through the reducer.
4. Redux tracks loading, saving, and error state for server requests.
5. `saveExpenseSnapshot` sends the full edited snapshot with the loaded
   snapshot version, explicit workspace ID, and submitted draft revision to the
   backend.
6. The backend response becomes the new committed Redux snapshot and draft
   baseline.

If the user makes another draft edit while a save is in flight, the returned
snapshot version becomes the base for the next save without replacing those
newer local edits. A save response clears dirty state only when the submitted
draft revision is still current. Resetting the draft restores the committed
baseline without rewinding the monotonic local revision sequence.

If the backend returns `409 Conflict`, another save committed first. The local
draft remains in place and a dedicated conflict notice explains that reloading
will discard it. `Discard Draft and Reload` deliberately replaces the stale
draft with the latest saved snapshot. Other save failures keep the draft and
offer retry and dismissal actions; successful saves announce the committed
snapshot version.

Editable collection tables render an explicit empty row when they have no
income events, withdrawals, accounts, debts, or important dates. Their add
forms remain available in the same workflow.

The header also includes an authenticated export button that downloads
`GET /api/v1/financials/export` as a JSON backup. Unsaved draft edits are not
included until they are saved.

This keeps form editing responsive while still demonstrating production-style
API boundaries.

---

## Available scripts

### Start development server

```sh
npm run dev
```

### Production build

```sh
npm run build
```

### Preview production build

```sh
npm run preview
```

### Run tests

```sh
npm run test
```

### Run browser workflow smoke tests

```sh
npm run test:e2e
```

Run the focused live accessibility audit from the repository root so the
temporary PostgreSQL schema is always removed:

```powershell
.\scripts\run-browser-checks.ps1 -TestPath e2e/accessibility.spec.ts
```

The audit applies axe WCAG A/AA rules to account access, onboarding, all
financial sections, and the removal dialog. It also checks modal keyboard focus
and account-tab keyboard navigation. Use
`../docs/accessibility-verification.md` for the manual screen-reader protocol;
automated results are not a substitute for an assistive-technology run.

Run the focused responsive workflow audit from the repository root:

```powershell
.\scripts\run-browser-checks.ps1 -TestPath e2e/responsive.spec.ts
```

The responsive audit traverses signup, onboarding, compact navigation, and all
twelve financial sections at 320, 390, 768, and 1024 pixels wide. It rejects
page-level overflow, controls or table regions outside the viewport, undersized
controls, unnecessary withdrawal-table scrolling at tablet/desktop widths,
inaccessible narrow table scrolling, and incorrect navigation behavior at the
900-pixel breakpoint. Use
`../docs/responsive-verification.md` for the complete contract and manual
viewport, orientation, and zoom checks.

On a new machine, install the local Playwright Chromium browser first:

```sh
npm run test:e2e:install
```

The current smoke test starts both Spring Boot and Vite. Spring Boot runs in a
unique PostgreSQL schema, applies all Flyway migrations, and
drops the schema after the run. Using only the committed synthetic example, the
browser creates two accounts and distinct workspaces, proves cross-user
isolation, edits and saves a monthly withdrawal, recovers the first account,
confirms a delete modal, saves again, and verifies the removal after reload.

### Capture portfolio evidence

Run the dedicated capture from the repository root:

```powershell
.\scripts\capture-portfolio-evidence.ps1
```

This separate Playwright configuration starts the app on ports `3001` and
`18081`, loads only `backend/data/financials.example.json` into an isolated
schema, and writes the approved desktop and mobile PNG files under
`docs/images/portfolio`. It is not part of the normal browser or CI suite, so
verification never rewrites committed visual evidence accidentally. See
`../docs/portfolio-case-study.md` for the walkthrough and data-safety boundary.

### Run tests in watch mode

```sh
npm run test:watch
```

### Run coverage

```sh
npm run test -- --coverage
```

### Run ESLint

```sh
npm run lint
```

### Verify dependency compatibility

```sh
npm run check:dependency-compat
```

This read-only check verifies the temporary minimatch 3 compatibility override
documented in `../docs/dependency-update-triage.md`. Frontend installation does
not run a lifecycle script or modify dependency source files.

### Auto-fix ESLint issues

```sh
npm run lint:fix
```

### Type-check only

```sh
npm run type-check
```

---

## Project structure

```text
frontend/
|-- src/
|   |-- api/
|   |-- app/
|   |-- features/
|   |   `-- financials/
|   |-- setupTests.ts
|   `-- main.tsx
|
|-- vite.config.ts
|-- eslint.config.js
|-- tsconfig.app.json
|-- tsconfig.node.json
`-- package.json
```

---

## Key application files

### `src/api/client.ts`

Defines shared HTTP request helpers.

Responsibilities:

- request configuration
- response parsing
- error handling
- frontend/backend communication

### `src/api/endpoints/financials.ts`

Defines the financials API integration logic.

Responsibilities:

- financial snapshot typing
- withdrawal and asset category contracts
- fetch and save API calls
- one versioned full-snapshot mutation boundary

### `src/app/store.ts`

Redux store configuration.

Responsibilities:

- Redux Toolkit store setup
- middleware registration
- centralized application state

### `src/app/hooks.ts`

Typed Redux hook helpers.

Responsibilities:

- typed dispatch access
- typed selector access

### `src/main.tsx`

Frontend application entry point.

Responsibilities:

- React root creation
- Redux Provider registration
- application bootstrapping

### `src/App.tsx`

Primary application component.

Responsibilities:

- rendering the financials feature
- demonstrating frontend/backend flow
- hosting the application shell

### `src/features/financials/FinancialsPage.tsx`

Financial snapshot feature UI.

Responsibilities:

- authenticated account and workspace header
- load, initialize, save, export, and retry action coordination
- active-tab selection and financial workspace composition

### Financials tab components

The financials feature is split into focused tab and shared UI modules:

- `OverviewTab.tsx`
- `ProjectionTab.tsx`
- `MonthlyWithdrawalsTab.tsx`
- `AnnualWithdrawalsTab.tsx`
- `IncomeSummaryTab.tsx`
- `IncomeCalendarTab.tsx`
- `AssetTable.tsx`
- `DebtTab.tsx`
- `ImportantDatesTab.tsx`
- `ConfirmRemoveModal.tsx`
- `SaveControls.tsx`
- `FinancialsWorkspaceState.tsx`
- `FinancialsWorkflowFeedback.tsx`
- `WorkspaceOnboarding.tsx`
- `financialsDraft.ts`
- `financialsDraftReducer.ts`
- `financialsFormatters.ts`
- `financialsTypes.ts`

### `src/features/financials/financialsSlice.ts`

Redux Toolkit slice for backend-backed financial snapshot state.

Responsibilities:

- initial snapshot fetch
- snapshot save request
- loading and saving status
- API error state

### `vite.config.ts`

Frontend build and development configuration.

Responsibilities:

- Vite plugin registration
- development server configuration
- proxy configuration
- Vitest configuration
- path alias configuration

### `eslint.config.js`

Frontend linting configuration.

Responsibilities:

- TypeScript linting
- React linting
- accessibility validation
- import sorting
- React hooks validation
- formatting compatibility

### `scripts/check-dependency-compatibility.cjs`

Verifies that current ESLint packages using minimatch 3 resolve the secure,
CommonJS-compatible brace-expansion 1.x release. It fails when the dependency
path changes so the temporary override can be reviewed or removed.

---

## Testing

Vitest is used as the test runner.

Coverage reports are generated using the V8 coverage provider.

Coverage output:

```text
frontend/coverage/
```

---

## Code quality

The frontend uses:

- ESLint for static analysis
- Prettier for formatting
- strict TypeScript compiler settings
- import sorting
- accessibility rules
- React hooks validation
- path aliases for cleaner imports

Git hooks automatically run checks on staged files before commits.

---

## Architecture notes

### Path aliases

The frontend config defines the `@` alias for cleaner imports:

```ts
import { financialsService } from '@/api/endpoints/financials';
```

instead of deeply nested relative imports:

```ts
import { financialsService } from '../../api/endpoints/financials';
```

Current feature modules still mostly use relative imports. The alias is
available for future cleanup but has not been applied broadly yet.

### Strict TypeScript

The TypeScript configuration intentionally favors strictness and explicitness
to encourage safer and more maintainable frontend code.

### Draft/save financial editing

Financial record changes are intentionally held in one feature-local canonical
draft reducer until the user clicks `Save Changes`. Domain hooks expose focused
forms, selectors, and commands without maintaining independent record
collections. This mirrors applications that fetch data once, let a user make a
batch of edits, then persist the final snapshot in one request.

---

## Notes

Intentional simplifications:

- no routing
- no account recovery, invitation, or membership-management workflow yet
- no component library
- no advanced caching/state normalization
- no deployment infrastructure
- no external financial website integrations

Focus areas:

- architecture clarity
- maintainability
- frontend/backend integration
- draft/save state management
- modern tooling
- developer workflow quality
- frontend engineering standards
