# Production Readiness Roadmap

This page tracks only open owner decisions and future product work. Completed
architecture and tradeoffs live in the ADR index, current behavior lives in
the owning documentation, and verification results live in engineering
evidence. Git history retains the former completed checklist.

## Completed Baseline

The current application has:

- PostgreSQL-only, workspace-owned relational persistence with Flyway,
  optimistic versions, audit history, JSON backup/restore, and isolated
  integration tests;
- account sessions, CSRF protection, workspace isolation, structured errors,
  and production configuration guardrails;
- one canonical frontend draft, stable projection roles, pay cadence and
  planning time-zone behavior, accessible workflows, and responsive layouts;
- local and hosted spelling, quality, coverage, browser, accessibility,
  responsive, dependency, and security gates;
- synthetic portfolio evidence, an approved public corpus, ADR history, and
  current architecture, contract, storage, verification, and limitation docs.

See [Architecture Decisions](adr/README.md),
[Architecture Map](architecture-map.md), and
[Engineering Evidence](engineering-evidence.md) for the maintained detail.

## Pre-Deployment Decision

- [ ] Decide whether to run a final architecture and scalability review before
      selecting providers. Record material deployment architecture decisions
      in a new ADR.

## Portfolio Demo Deployment

- [ ] Select hosting and managed PostgreSQL providers with approved privacy,
      retention, cost, and shutdown policies. Use synthetic demonstration data.
- [ ] Configure HTTPS, secrets, least-privilege database roles, migrations,
      health verification, rollback, automated backups, and a proved restore.
- [ ] Export safe logs, metrics, and browser errors with basic alerting while
      excluding financial contents.
- [ ] Provide a repeatable demo-account reset so reviewers do not encounter
      another visitor's state.

Provider comparison remains in
[Deployment Provider Assessment](deployment-provider-assessment.md).

## Portfolio Chatbot And Product Work

- [ ] Build the citation-first chatbot only after revalidating the public
      corpus. Ingest approved current files, exclude secrets and personal data,
      and cite repository/file context in every architecture answer.
- [ ] Prioritize planning, reporting, forecasting, and collaboration features
      after hosted privacy, recovery, and core workflow boundaries are proven.

## Current Priority

Make the pre-deployment review decision before starting provider selection.
