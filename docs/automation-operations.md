# Automation And Agent Operations

This guide owns repository automation, agent, connector, and external-write
boundaries. Executable workflows, scripts, and live provider settings remain
the source of truth.

## Operating Principles

- Use local files and deterministic scripts for repository work.
- Use connectors or CLIs for live pull requests, checks, issues, and provider
  state that the checkout cannot answer.
- External writes require explicit user intent. Reads and requested publication
  steps do not authorize unrelated comments, labels, reruns, settings changes,
  or issue mutations.
- Never send personal financial values, database rows, ignored JSON, exports,
  traces, screenshots, credentials, or raw secret-bearing logs to a connector.
- Hosted AI and maintenance packets provide context; they never replace tests,
  security gates, or human judgment.

## GitHub Workflow

Implementation requests normally include publication and cleanup, following
the standing completion instructions in `AGENTS.md`. Read-only reviews,
planning, and explicit local-only or draft-only requests keep their scope.

1. Inspect the branch, status, diff, and intended base.
2. Stage only the intended files and run relevant local verification.
3. Commit on a topic branch and push with tracking.
4. Use `.github/PULL_REQUEST_TEMPLATE.md` and open a ready pull request unless
   the user requested a draft.
5. Inspect hosted checks and diagnose failures from their actual logs.
6. Merge after required checks pass; never bypass repository protection.
7. Verify the merged commit and applicable deployments. Synchronize local main
   and remove the completed task's local and remote branches. Confirm its
   merged PR and reviewed head after a squash merge; preserve active work and
   unexplained unique commits.

Publication defaults do not authorize unrelated profile updates, social posts,
Drive edits, messages, or financial-data operations. Follow the selected task's
scope and report the exact step when completion is blocked.

Prefer the GitHub connector for structured reads and supported pull-request
operations. Use authenticated `gh` when the connector lacks repository
permission or when Actions logs, merge operations, or branch cleanup require
CLI coverage. Do not use GitHub APIs to edit files that are available locally.

Issue forms under `.github/ISSUE_TEMPLATE/` define intake fields. Before
implementation, confirm the objective, acceptance criteria, affected surface,
verification, and data-safety state. An issue defines scope; it does not grant
permission for deployment, data mutation, or unrelated repository writes.

## Hosted Automation

The repository currently uses:

- `ci.yml` for spelling, quality, frontend/backend builds, coverage,
  PostgreSQL integration, browser, accessibility, responsive, and scans;
- `codeql.yml` and `dependency-review.yml` for hosted security analysis;
- `copilot-review.yml` for non-blocking review requests;
- `dependabot-auto-merge.yml` for patch-only auto-merge after required checks;
- `pr-advisory.yml` for combined documentation-drift and dependency-triage
  context;
- `weekly-maintenance.yml` for scheduled repository-health evidence.

Third-party and GitHub-owned actions are pinned to immutable full commit SHAs,
with release comments retained for Dependabot maintenance. Pull-request
workflows use concurrency groups to cancel superseded runs without canceling
protected `main` verification.

Copilot comments and generated advisory packets must be verified against code,
tests, ADRs, and data-safety rules. A successful advisory workflow does not mean
the underlying CI job passed.

## Connector Boundaries

### PostgreSQL

Use `financial_app_reader` or another dedicated read-only role for external
inspection. Confirm `transaction_read_only` is enabled and limit investigation
to metadata, counts, catalog queries, and minimal synthetic reproductions.
Never connect an external tool with the application-owner credential.

Run `scripts/setup-postgres-readonly-role.ps1` only for an explicitly requested
setup task. Ordinary diagnosis must not grant privileges or mutate data.

### Browser And Playwright

Use the browser for visible interaction or manual checks and Playwright scripts
for repeatable workflow, accessibility, and responsive evidence. Browser tests
must use synthetic accounts and isolated PostgreSQL schemas. Keep generated
reports, traces, and screenshots in ignored paths unless an explicit synthetic
portfolio capture is requested.

### Snyk And Security Providers

Keep tokens user-scoped or CI-scoped and never commit them. The pinned Snyk CLI
version is stored in `.snyk-cli-version`. A missing token, skipped job, or npm
audit result is not equivalent to an authenticated Snyk pass. CodeQL,
Dependency Review, GitGuardian, and Snyk provide different evidence and should
be reported separately.

## Project Skills And Reviewers

Repository skills are reserved for workflows that need project-specific
commands or domain knowledge:

- bootstrap local development;
- run the complete verification gate;
- inspect PostgreSQL read-only;
- triage hosted CI failures;
- audit documentation against executable sources;
- perform a cross-layer findings-first review.

Four optional reviewers cover frontend/UX, backend/data, security/CI, and
documentation/architecture. Use only those relevant to a change. The parent
agent consolidates findings and owns the final severity and release judgment.

## Scheduled Maintenance

`weekly-maintenance.yml` runs deterministic status, dependency, documentation,
spelling, audit, and recent-CI checks on GitHub-hosted runners. Its existing
Monday schedule is unchanged. Packet findings are advisory; before acting,
verify live PR, issue, workflow, dependency, and security state.

`scripts/generate-maintenance-packets.ps1` collects separate Markdown packets
and structured `comparison.json` and `maintenance-status.json` files under
runner temporary storage, then appends every packet to the workflow summary.
The comparison is the current commit against its latest ancestor before seven
days ago. New repositories compare against an empty tree; no-change weeks use
the same base and head. An unavailable comparison fails the affected packets
instead of silently falling back to a clean working tree. This is a rolling
seven-day window, not a claim that every change since the last successful run
was reviewed.

Each packet runs independently. A failed collector gets a generic failure
packet in place of partial output, and its raw exception is not retained.
Only the selected Markdown and JSON packet files are uploaded, for 14 days;
database rows, exports, and raw logs are excluded. Dependency triage is a
declared inventory, not registry update discovery or an authenticated scan.

Failed or skipped collection, installation, spelling, audit, recent-CI, test,
or artifact steps fail the final completion gate. High-or-critical dependency
findings continue to fail the review. Independent diagnostics still run when
another check fails. Quiet healthy runs write summaries without opening issues
or posting messages.

Run `scripts/test-maintenance-packets.ps1` for synthetic regression checks of
committed changes, initial and no-change windows, summary preservation,
independent failures, sanitized artifacts, and unavailable comparisons. It uses
temporary Git fixtures and never accesses PostgreSQL. The test also runs in
the hosted CI and weekly maintenance workflows.

Weekly review should look for:

- failing or skipped required checks;
- stale pull requests or issues;
- pending security and dependency updates;
- documentation drift with a concrete contradicted claim;
- known limitations whose revisit trigger occurred;
- open product and deployment roadmap items.

Do not automatically merge, label, close, assign, rerun, dismiss, or change
repository settings from a maintenance packet.

## Configuration Audit

Useful read-only checks include:

```powershell
gh auth status
gh repo view --json nameWithOwner,defaultBranchRef,url
gh api repos/{owner}/{repository}/rulesets
gh workflow list
.\scripts\generate-engineering-status.ps1
.\scripts\check-documentation-drift.ps1
```

Live GitHub settings and installed-app permissions can change independently of
the repository. Treat this guide as policy and routing, not a cached statement
that a provider permission is currently enabled.
