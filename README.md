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
  |  GET /api/getHello       |                             |
  |------------------------->|                             |
  |        (proxy)           |  GET http://localhost:8080/api/getHello
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

Endpoints:

```http
GET /api/getHello
POST /api/postHello
```

Example response:

```json
{
  "message": "Hello from BACKEND!",
  "source": "backend",
  "timestamp": 1715890000000
}
```

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

- `backend/README.md`
- `frontend/README.md`

Each subproject README is intentionally self-contained.

---

## Notes

Current intentional limitations:

- no database
- no authentication
- no routing
- no persistence layer
- no deployment infrastructure

Focus areas:

- architecture clarity
- frontend/backend communication
- modern tooling
- developer experience
- CI/CD workflows
- engineering standards
