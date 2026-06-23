# End-to-End Reference Application

![CI](https://github.com/everdein/end-to-end-app/actions/workflows/ci.yml/badge.svg)

Modern full-stack reference application demonstrating a React + TypeScript
frontend communicating with a Spring Boot REST API.

This repository is intentionally designed as a **learning, experimentation,
and architecture reference project** focused on modern engineering workflows,
tooling, and developer experience.

The goal is not production complexity, but establishing clean engineering
patterns and workflows that can scale over time.

---

## Tech stack

### Frontend

- React 19
- TypeScript
- Vite
- Redux Toolkit
- Vitest
- ESLint
- Prettier

### Backend

- Spring Boot 4
- Java 21
- Maven

### Tooling / Quality

- Husky
- lint-staged
- cspell
- Snyk
- GitHub Actions
- Vitest coverage
- Spotless
- SortPom

---

## Project structure

```text
end-to-end-app/
|-- backend/              # Spring Boot API
|   `-- data/             # Example and local financial snapshot data
|-- frontend/             # React + TypeScript frontend
|-- .github/workflows/    # CI pipelines
|-- .husky/               # Git hooks
`-- README.md
```

---

## Requirements

- Java 21+
- Node.js 20+
- npm 10+

Verify tools are available:

```sh
java -version
node -v
npm -v
```

---

## Installation

Install workspace tooling (repo root):

```sh
npm install
```

Install frontend dependencies:

```sh
cd frontend
npm install
```

---

## Running the application (local development)

Run the backend and frontend in separate terminals during local development.

### Manual startup

Backend:

```sh
cd backend
./mvnw spring-boot:run -Pdev
```

Backend URL:

```text
http://localhost:8080
```

Frontend:

```sh
cd frontend
npm run dev
```

Frontend URL:

```text
http://localhost:3000
```

## Request flow

### Local development flow

```text
Browser                Vite Dev Server                 Spring Boot
  |                          |                             |
  |  GET http://localhost:3000/                            |
  |------------------------->|                             |
  |    (serves React app)    |                             |
  |<-------------------------|                             |
  |                          |                             |
  |  GET /api/financials/expenses                         |
  |------------------------->|                             |
  |        (proxy)           |  GET http://localhost:8080/api/financials/expenses
  |                          |---------------------------->|
  |                          |        JSON response        |
  |                          |<----------------------------|
  |        JSON response     |                             |
  |<-------------------------|                             |
  |     render JSON in UI    |                             |
```

Because the Vite proxy is used:

- frontend code does not hard-code backend URLs
- no CORS configuration is required during local development

---

## API contract

Financials endpoints:

```http
GET /api/financials/expenses
PUT /api/financials/expenses/snapshot
POST /api/financials/expenses
PUT /api/financials/expenses/{id}
DELETE /api/financials/expenses/{id}
PUT /api/financials/pay-period
```

The Financials UI currently uses a draft/save workflow:

- one request loads the financial snapshot when the app opens
- edits are made locally in the browser
- one save request persists the full snapshot to the backend

The individual bill endpoints remain available as a more granular API option.

---

## Financials feature

The application includes a personal financial snapshot area with sidebar
sections for:

- overview totals, including assets, debt, net worth, and disposable income
- next pay period projections for paycheck income, bills, rent set-asides, debt payoff, and possible HYSA transfer
- monthly withdrawals with pay period planning
- annual withdrawals that can be included in the active pay period
- income summary assumptions by interval
- income calendar events with received/current/upcoming status
- retirement accounts
- investments
- cash and savings
- insurance and benefits
- debt balances
- important dates with passed/next/upcoming status

Editable tables support adding, editing, removing, warning before removal,
resetting unsaved changes, and saving the full draft snapshot. Displayed dates
use `MM/DD/YYYY`; browser date inputs use native date controls for editing.

Pay period dates are automatically derived from the saved schedule and today's
date when the app opens. Manually changing the pay period dates updates that
schedule anchor on the next save.

The Projection view is derived from the saved snapshot and current draft state.
It focuses on the next pay period, using bi-weekly net income, bills due, annual
withdrawals due, the rent bill, the rent savings account, and current debt to
estimate what can go toward credit card debt. If debt is covered, remaining cash
is shown as a possible Apple HYSA transfer. The current period is shown only as
supporting context.

Financial data is stored locally by the backend in:

```text
backend/data/financials.local.json
```

That file is intentionally ignored by Git so personal data stays local. A safe
template is committed at:

```text
backend/data/financials.example.json
```

If the local file does not exist, the backend creates it from the example file
on startup.

---

## Frontend quality tooling

### Linting

```sh
npm run lint
```

### Auto-fix lint issues

```sh
npm run code-quality:fix
```

### Formatting

```sh
npm run format
```

### Spell checking

```sh
npm run spell
```

### Frontend tests

```sh
cd frontend
npm run test
```

### Coverage

```sh
cd frontend
npm run test -- --coverage
```

---

## Backend quality tooling

### Format Java source

```sh
cd backend
./mvnw spotless:apply
```

### Verify Java formatting

```sh
cd backend
./mvnw spotless:check
```

### Format pom.xml

```sh
cd backend
./mvnw sortpom:sort
```

### Verify pom.xml formatting

```sh
cd backend
./mvnw sortpom:verify
```

---

## CI pipeline

GitHub Actions currently validates:

- linting
- spell checking
- TypeScript type safety
- frontend test coverage
- frontend builds
- backend builds
- dependency/security scans

---

## Documentation

Additional documentation:

- `docs/adr/README.md`
- `backend/README.md`
- `frontend/README.md`

Each subproject README is intentionally self-contained.

---

## Notes

Current intentional limitations:

- no database
- no authentication
- no routing
- local file-backed persistence only
- no deployment infrastructure
- no external financial website integrations

Focus areas:

- architecture clarity
- frontend/backend communication
- draft/save state management
- local data persistence boundaries
- modern tooling
- developer experience
- CI/CD workflows
- engineering standards
