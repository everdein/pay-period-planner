# Frontend (React + TypeScript + RTK Query)

Minimal React application that calls the backend JSON API and renders the response.  
This frontend is intentionally small and designed as a reference for end-to-end frontend ↔ backend integration.

## Requirements

- Node.js 20+ (LTS recommended)
- npm (included with Node.js)
- Backend running locally (see `backend/README.md`)

## Environment notes (Windows)

This project was developed and tested with:

- Node.js 20 LTS
- npm 11+

To verify Node and npm are available:

```sh
node -v
npm -v
```

If both commands succeed, the environment is configured correctly.

## Project initialization

This project was scaffolded using Vite with the React + TypeScript template.

## Install dependencies

From the `frontend` directory:

```sh
npm install
```

## Run the application

From the `frontend` directory:

```sh
npm run dev
```

The development server will start on:

```
http://localhost:3000
```

## Backend dependency

The frontend depends on the backend service, but does not call it directly from
the browser.

During local development, requests to:

```
/api/*
```

are proxied by the Vite dev server to:

```
http://localhost:8080
```

This avoids CORS issues and keeps backend URLs out of frontend source code.

## Key files

- `src/services/api.ts`
  - RTK Query API slice
  - Defines the `getHello` query and response typing
- `src/store.ts`
  - Redux store configuration
  - Registers RTK Query reducer + middleware
- `src/main.tsx`
  - Wraps the app in the Redux `Provider`
- `src/App.tsx`
  - Calls `useGetHelloQuery()` and displays the JSON response

## Notes

- Uses React + TypeScript
- Uses Redux Toolkit and RTK Query for data fetching
- No routing, no forms, no persistence—only the smallest working example
