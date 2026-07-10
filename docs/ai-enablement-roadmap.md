# AI Enablement Roadmap

This checklist is ordered by dependency and expected value. Complete each item
with focused verification before moving to the next.

## P0: Shared Ground Truth and Deterministic Workflows

- [x] Repository `AGENTS.md`
- [x] Project bootstrap skill
- [x] Full verification skill
- [x] Complete native script suite
- [x] PostgreSQL inspector skill
- [x] Code review skill
- [x] CI/Snyk triage skill
- [x] Documentation auditor skill
- [x] Release/PR preparation skill

Existing skills may satisfy part of an unchecked item, but each must be checked
against the requested workflow and validated before it is marked complete.

## P1: Durable Project Knowledge

- [x] Architecture map
- [x] Domain glossary
- [x] API contract
- [x] Database ownership and storage guide
- [x] Verification matrix
- [x] Known limitations register
- [x] Troubleshooting decision tree
- [x] Decision log / ADRs

## P2: Role-Oriented Review

- [x] Frontend reviewer
- [x] Backend/API reviewer
- [x] PostgreSQL reviewer
- [x] Security/dependency reviewer
- [x] Accessibility reviewer
- [x] Test-coverage reviewer
- [x] Documentation reviewer
- [x] Architecture reviewer
- [x] Coordinating review workflow with deduplication and severity ranking

Implement these as narrowly scoped agent definitions only where they add value
beyond the repository skills and review checklist.

## P3: External Integrations

- [x] GitHub MCP setup and read/write boundaries
- [x] Read-only PostgreSQL MCP role and setup
- [x] Browser/Playwright workflow testing
- [x] Snyk MCP/API feasibility assessment
- [ ] Observability integration when production telemetry exists

Filesystem MCP is intentionally omitted because agents already have workspace
access.

## P4: Hosted Assistance and Maintenance

- [x] GitHub Copilot review requests and categorized review comments
- [x] PR summaries and failure-log summaries
- [x] Issue-to-implementation workflow
- [x] Documentation-drift workflow
- [x] Dependency-update triage
- [x] Scheduled dependency, CI, documentation, security, and repository-health
      reviews
- [x] Weekly engineering-status report

Hosted AI assists review; it never bypasses required CI, security, or human
approval gates.
