# Backend (Spring Boot)

Modern Spring Boot backend API used by the frontend reference application.

This backend is intentionally designed as a lightweight instructional service
focused on:

- frontend ↔ backend communication
- REST API design
- layered architecture
- DTO usage
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

### Get hello message

```http
GET /api/getHello
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

### Post hello message

```http
POST /api/postHello
```

Example request:

```json
{
  "message": "Hello backend"
}
```

Example response:

```json
{
  "message": "Hello backend",
  "source": "backend",
  "timestamp": 1715890000000
}
```

---

## Project structure

```text
backend/
├── src/main/java/
│   ├── api/
│   ├── dto/
│   ├── service/
│   └── BackendApplication.java
│
├── src/main/resources/
│
├── pom.xml
└── README.md
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

### `service/`

Business/service layer.

Responsibilities:

- application logic
- orchestration
- reusable backend behavior

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
- no persistence layer
- no authentication
- no authorization
- no external APIs
- no production deployment infrastructure

Focus areas:

- backend architecture fundamentals
- clean API boundaries
- maintainability
- local development experience
- frontend/backend integration
- backend engineering standards
