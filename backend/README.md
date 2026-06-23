# Backend (Spring Boot)

Modern Spring Boot backend API used by the frontend reference application.

This backend is intentionally designed as a lightweight instructional service
focused on:

- frontend/backend communication
- REST API design
- layered architecture
- DTO usage
- file-backed local persistence
- local development workflows
- backend tooling and CI integration

The goal is not production complexity, but establishing clean backend
engineering patterns and workflows that can scale over time.

---

## Tech stack

### Core

- Java 21
- Spring Boot 4
- Spring MVC
- Maven
- Jackson

### Tooling

- Maven Wrapper
- Spring Boot DevTools
- Spotless
- SortPom
- GitHub Actions
- Snyk
- OWASP Dependency Check

---

## Requirements

- Java 21+
- No database required
- No external infrastructure required

Verify Java installation:

```sh
java -version
```

---

## Environment notes (Windows)

This project assumes:

- Java 21 is installed
- `JAVA_HOME` points to the JDK installation directory
- `%JAVA_HOME%\bin` is available on the system PATH

---

## Project initialization

This project was generated using Spring Initializr:

```text
https://start.spring.io
```

Configuration:

- Project: Maven
- Language: Java
- Spring Boot: 4.x
- Packaging: Jar
- Java: 21

Dependencies:

- Spring Web MVC
- Spring Boot Actuator
- Lombok

---

## Running the application

From the `backend` directory:

### Standard startup

```sh
./mvnw spring-boot:run
```

### Development profile (recommended)

```sh
./mvnw -Pdev spring-boot:run
```

The `dev` profile enables Spring Boot DevTools for improved local development
experience.

Backend URL:

```text
http://localhost:8080
```

---

## Available endpoints

### Get financial snapshot

```http
GET /api/financials/expenses
```

Returns the current pay period, withdrawals, income summaries, income calendar
events, asset category totals, debt balances, important dates, and calculated
overview totals.

---

### Save financial snapshot

```http
PUT /api/financials/expenses/snapshot
```

Persists the full edited financial snapshot in one request. This is the main
endpoint used by the frontend draft/save workflow.

---

### Granular financial endpoints

```http
POST /api/financials/expenses
PUT /api/financials/expenses/{id}
DELETE /api/financials/expenses/{id}
PUT /api/financials/pay-period
```

These endpoints remain available for direct bill and pay period updates, even
though the current UI saves the full snapshot.

---

## Financial data storage

Financial data is stored in a local JSON file:

```text
backend/data/financials.local.json
```

That file is ignored by Git and should contain the user's real local financial
data. The committed template lives at:

```text
backend/data/financials.example.json
```

On startup, `FinancialsRepository` creates `financials.local.json` from the
example file when the local file does not already exist. This keeps the feature
usable after a fresh clone without committing personal data.

The storage path can be overridden with Spring configuration:

```properties
financials.data.path=data/financials.local.json
financials.example-data.path=data/financials.example.json
```

---

## Project structure

```text
backend/
|-- data/
|   |-- financials.example.json
|   `-- financials.local.json        # ignored by Git
|-- src/main/java/
|   |-- com/example/backend/api/
|   |-- com/example/backend/dto/
|   |   `-- financials/
|   |-- com/example/backend/repository/
|   |-- com/example/backend/service/
|   `-- com/example/backend/BackendApplication.java
|-- src/test/java/
|-- pom.xml
`-- README.md
```

---

## Architecture overview

### `api/`

REST controllers.

Responsibilities:

- HTTP request handling
- request/response mapping
- status code handling
- API boundary definition

### `dto/`

Data Transfer Objects.

Responsibilities:

- request payloads
- response payloads
- API contract typing

### `repository/`

Persistence-facing data models and storage adapters.

Responsibilities:

- loading local JSON data
- writing local JSON data
- assigning IDs for new local rows
- keeping persistence concerns out of controllers

### `service/`

Business/service layer.

Responsibilities:

- application logic
- validation
- pay period calculations
- totals and snapshot aggregation
- orchestration between API and repository layers

---

## Financials domain behavior

The financials service calculates:

- monthly withdrawal total
- paid total
- unpaid total
- pay period total
- annual withdrawal total
- annual withdrawals due in the active pay period
- per-category asset totals
- total tracked assets
- total debt
- net worth
- income calendar monthly paycheck counts

Pay periods are stored as start and end dates. When a snapshot is read, the
service advances or rewinds the stored pay period window to include the current
date while preserving the original period length.

Withdrawal due dates are derived from each row's due day and the active pay
period month. Days beyond the end of a month are clamped to the last valid day
of that month.

Annual withdrawals are stored as recurring month/day values. The service
projects them into the active pay period year so they can be displayed as
`MM/DD/YYYY` and included in pay period planning.

The persisted snapshot currently includes monthly bills, annual withdrawals,
asset accounts, debt accounts, income summary items, income calendar events,
and important dates. This is intentionally a single-user local aggregate until
the app needs database-backed collaboration or integrations.

---

## Development notes

### Spring Boot DevTools

DevTools are isolated to the `dev` Maven profile to avoid accidental inclusion
in production builds.

Benefits:

- automatic restart
- improved local iteration speed
- enhanced development workflow

### Spotless

Spotless is used to enforce consistent Java formatting.

Format Java source:

```sh
./mvnw spotless:apply
```

Verify formatting:

```sh
./mvnw spotless:check
```

### SortPom

SortPom is used to keep `pom.xml` consistently organized and formatted.

Format `pom.xml`:

```sh
./mvnw sortpom:sort
```

Verify `pom.xml` formatting:

```sh
./mvnw sortpom:verify
```

---

## Local frontend integration

The frontend communicates with the backend through the Vite development proxy.

Frontend requests:

```text
/api/*
```

are proxied to:

```text
http://localhost:8080
```

This avoids local CORS configuration requirements during development.

---

## Build the application

```sh
./mvnw clean verify
```

---

## CI / Security tooling

The backend participates in repository CI pipelines for:

- Maven builds
- dependency scanning
- Snyk security analysis
- OWASP dependency checks
- formatting verification

---

## Notes

Intentional simplifications:

- no database
- local JSON persistence only
- no authentication
- no authorization
- no external APIs
- no production deployment infrastructure

Focus areas:

- backend architecture fundamentals
- clean API boundaries
- local persistence boundaries
- maintainability
- local development experience
- frontend/backend integration
- backend engineering standards
