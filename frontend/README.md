# Frontend (React + TypeScript + Redux Toolkit)

Modern React frontend application built with Vite and TypeScript.

This frontend is intentionally designed as a lightweight reference application
focused on:

- frontend/backend communication
- modern React architecture
- Redux Toolkit patterns
- TypeScript strictness
- draft/save user workflows
- testing and quality tooling
- developer experience

The goal is not production complexity, but establishing clean frontend
engineering patterns that can scale over time.

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

---

## Financials UI

The main application screen is a personal financial snapshot with grouped
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

The Projection tab derives a next-paycheck forecast from the current draft. It
uses bi-weekly net income, next pay period bills, annual withdrawals due in the
period, the rent bill, the current rent savings balance, and current debt to
show cash after bills, the credit card payment that leftover cash could make,
remaining debt, and any possible Apple HYSA transfer after debt is covered. The
current pay period is shown only as supporting context. The Overview shows the
projection headline numbers.

Annual Withdrawals tracks recurring yearly charges with native calendar inputs
while storing month/day recurrence data. Income Summary tracks net and
disposable income assumptions by interval. Income Calendar tracks paydays and
one-time income events, with derived received/current/upcoming statuses.

The asset and debt tabs support editable account rows. Asset categories show
category totals and an overall tracked-assets total. Debt contributes to total
debt and net worth. Important Dates tracks holidays and personal/company dates,
with derived passed/next/upcoming statuses.

Displayed dates use `MM/DD/YYYY`. Editable date fields use native browser date
inputs.

### Data flow

The frontend uses a draft/save pattern:

1. `fetchMonthlyExpenses` loads one snapshot from the backend.
2. The user edits local React component state.
3. Redux tracks loading, saving, and error state.
4. `saveExpenseSnapshot` sends the full edited snapshot to the backend.
5. The backend response becomes the new committed Redux snapshot.

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

On a new machine, install the local Playwright Chromium browser first:

```sh
npm run test:e2e:install
```

The current smoke test starts the Vite dev server, mocks
`/api/v1/financials` with synthetic data, edits a monthly withdrawal, and
verifies the save payload. It does not require the Spring Boot backend.

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
- optional granular withdrawal endpoint helpers

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

- sidebar financial sections
- local draft state for edits
- monthly withdrawal forms
- annual withdrawal forms
- income summary forms
- income calendar forms
- asset account forms
- debt account forms
- important date forms
- pay period calculations
- save/reset workflow

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
- `financialsDraft.ts`
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

Financial form changes are intentionally held in component-local draft state
until the user clicks `Save Changes`. This mirrors applications that fetch data
once, let a user make a batch of edits, then persist the final snapshot in one
request.

---

## Notes

Intentional simplifications:

- no routing
- no authentication
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
