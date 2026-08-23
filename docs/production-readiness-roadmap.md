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

## Azure Migration Planning

- [x] Approve the production-shaped, single-owner, synthetic-only Azure
      boundary and record it in
      [ADR 0030](adr/0030-use-a-production-shaped-single-owner-azure-environment.md).
- [ ] Confirm the subscription, region, availability objectives, monthly
      ceiling, budget thresholds, and resource names.
- [ ] Complete the owner account, billing, workstation, provider, quota, and
      GitHub OIDC setup in the
      [Azure Deployment Prerequisites](azure-prerequisites.md).
- [ ] Run a final architecture, security, and scalability review. Record the
      implemented deployment architecture in a new ADR.
- [ ] Build the single-origin React and Spring Boot container described in the
      [Azure Migration Plan](azure-migration-plan.md).
- [ ] Provision Azure Container Apps, Azure Database for PostgreSQL Flexible
      Server, Container Registry, Key Vault, private networking, and monitoring
      through infrastructure as code.
- [ ] Separate the Flyway migration identity from the least-privilege runtime
      database identity and prove forward migration and application rollback.
- [ ] Add GitHub OIDC delivery with immutable images, environment approval,
      health verification, and an authenticated synthetic smoke gate.
- [ ] Implement transactional owner bootstrap, closed production registration,
      sign-in throttling, full-session revocation, and operator-assisted account
      recovery.

## Azure Portfolio Demo Release

- [ ] Configure HTTPS, secure cookies, managed secrets, private database access,
      edge request limits, cost alerts, and shutdown procedures.
- [ ] Export sanitized logs, metrics, traces, and browser errors to Application
      Insights and Log Analytics with explicit retention and basic alerting.
- [ ] Prove both application JSON restore and PostgreSQL point-in-time restore
      into separate targets.
- [ ] Create the owner account with the temporary bootstrap credential, rotate
      that credential, disable signup, and populate only synthetic values.
- [ ] Run and record hosted authorization, CSRF, save, concurrency, recovery,
      browser, accessibility, responsive, and failure-path checks.

The detailed sequence, estimates, milestones, and completion gate live in the
[Azure Migration Plan](azure-migration-plan.md). Earlier provider research
remains in the
[Deployment Provider Assessment](deployment-provider-assessment.md).

## Portfolio Chatbot And Product Work

- [ ] Build the citation-first chatbot only after revalidating the public
      corpus. Ingest approved current files, exclude secrets and personal data,
      and cite repository/file context in every architecture answer.
- [ ] Prioritize planning, reporting, forecasting, and collaboration features
      after hosted privacy, recovery, and core workflow boundaries are proven.

## Current Priority

Approve the Azure planning boundary and complete Milestone A: produce one
hardened, single-origin container without creating cloud resources or adding
billing by implication.
