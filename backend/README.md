# Backend (Spring Boot)

Minimal Spring Boot application exposing a JSON API.  
This service is intentionally simple and designed as a reference for end-to-end frontend ↔ backend integration.

## Requirements

- Java 21
- No external dependencies (no database)

## Environment notes (Windows)

This project assumes:

- Java 21 is installed
- `JAVA_HOME` is set to the JDK installation directory
- `%JAVA_HOME%\bin` is on the PATH

To verify the environment:

```sh
java -version
```

If this command succeeds, the environment is configured correctly.

## Project initialization

This project was generated using Spring Initializr (https://start.spring.io) with the following settings:

- Project: Maven
- Language: Java
- Spring Boot: 4.0.1
- Packaging: Jar
- Java: 21
- Group: com.example
- Artifact: backend
- Dependencies:
  - Spring Web

## Run the application

From the `backend` directory:

```sh
./mvnw spring-boot:run
```

This will run the application with spring boot devtools (recommended)

```sh
./mvnw -Pdev spring-boot:run
```

The server will start on:

```
http://localhost:8080
```

## Available endpoints

```
GET http://localhost:8080/api/hello
```

Response:

```json
{
  "message": "Hello from Spring Boot",
  "source": "backend"
}
```

## Notes

- Uses Spring Web (Spring MVC)
- Uses Java records for response DTOs
- No persistence layer or business services
- No CORS configuration is required for local development because the frontend
  uses a Vite dev-server proxy to forward `/api` requests to the backend.
- Intended as a minimal, instructional backend
