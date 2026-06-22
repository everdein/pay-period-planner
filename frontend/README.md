# Frontend (React + TypeScript + Redux Toolkit)

Modern React frontend application built with Vite and TypeScript.

This frontend is intentionally designed as a lightweight reference application
focused on:

- frontend ↔ backend communication
- modern React architecture
- Redux Toolkit patterns
- TypeScript strictness
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

- Node.js 20+
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

### `src/api/endpoints/hello.ts`

Defines the hello API integration logic.

Responsibilities:

- API endpoint definitions
- response typing
- frontend/backend communication

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

- data fetching
- rendering API responses
- demonstrating frontend ↔ backend flow

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

The frontend uses the `@` alias for cleaner imports:

```ts
import { helloService } from '@/api/endpoints/hello';
```

instead of deeply nested relative imports:

```ts
import { helloService } from '../../api/endpoints/hello';
```

### Strict TypeScript

The TypeScript configuration intentionally favors strictness and explicitness
to encourage safer and more maintainable frontend code.

---

## Notes

Intentional simplifications:

- no routing
- no authentication
- no persistence
- no component library
- no advanced caching/state normalization
- no deployment infrastructure

Focus areas:

- architecture clarity
- maintainability
- frontend/backend integration
- modern tooling
- developer workflow quality
- frontend engineering standards
