# End-to-End Reference Application

Minimal end-to-end application demonstrating a frontend (React + TypeScript)
calling a backend (Spring Boot) JSON API.

This repository is intended as a **reference and teaching artifact**, not a
production-ready system.

---

## Project structure

```
end-to-end-app/
├── backend/        # Spring Boot API
├── frontend/       # React + TypeScript UI
├── scripts/        # Convenience scripts
└── README.md       # You are here
```

---

## Requirements

- Java 21 (for the backend)
- Node.js 20+ and npm (for the frontend)

Verify tools are available:

```sh
java -version
node -v
npm -v
```

---

## Running the application (local development)

You can run with **two terminals** (manual) or use a **convenience script**.

### Manual (two terminals)

Terminal 1 — Backend (from repo root):

```sh
cd backend
./mvnw spring-boot:run
```

Backend:

```
http://localhost:8080
```

Terminal 2 — Frontend (from repo root):

```sh
cd frontend
npm install
npm run dev
```

Frontend:

```
http://localhost:3000
```

Open the frontend in a browser to verify the end-to-end flow.

### Convenience scripts

Windows (PowerShell) — from repo root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\dev.ps1
```

macOS/Linux (bash) — from repo root:

```sh
./scripts/dev.sh
```

Notes:

- The scripts open the backend and frontend in separate terminal windows (Windows),
  or run them together in one terminal (macOS/Linux).
- Stop with Ctrl+C in each terminal window/process.

---

## Request flow

### Sequence diagram (local dev)

```
Browser                Vite Dev Server                 Spring Boot
  |                          |                             |
  |  GET http://localhost:3000/                            |
  |------------------------->|                             |
  |    (serves React app)    |                             |
  |<-------------------------|                             |
  |                          |                             |
  |  GET /api/hello          |                             |
  |------------------------->|                             |
  |        (proxy)           |  GET http://localhost:8080/api/hello
  |                          |---------------------------->|
  |                          |        JSON response        |
  |                          |<----------------------------|
  |        JSON response     |                             |
  |<-------------------------|                             |
  |     render JSON in UI    |                             |
  |                          |                             |
```

Because the Vite proxy is used:

- The frontend does not hard-code backend URLs
- No CORS configuration is required for local development

---

## API contract

Backend endpoint:

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

---

## Documentation

- See `backend/README.md` for backend-specific details
- See `frontend/README.md` for frontend-specific details

Each subproject README is intentionally self-contained.

---

## Notes

- No database
- No authentication
- No routing
- Focus is on clarity and data flow, not completeness
