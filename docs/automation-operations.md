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
verification, dependencies, owner, and data-safety state. An issue defines scope; it does not grant
permission for deployment, data mutation, or unrelated repository writes.

Use Bug report for a reproducible defect, Feature request for a proposed
capability, and Engineering task for understood implementation, maintenance,
or investigation. For an investigation, define the question, time limit and
expected evidence instead of promising a code change. PostgreSQL is runtime
storage; JSON backup/export is a separate affected area.

Ready means objective, scope/non-goals, observable acceptance, verification,
dependencies and owner are identified. Keep one primary item active. A merged
PR remains in Review / Verification until main and applicable deployment/live
checks pass and task-branch cleanup is complete; then close the issue and mark
Done. Draft-only and read-only work keep their selected scope.

For example, a synthetic maintenance task can name “remove an obsolete command
from an issue-form placeholder” as its objective, limit scope to that form,
require that the replacement matches the current setup guide, and verify YAML
parsing, spelling and the rendered form. Record “no dependencies” explicitly;
exclude application/data changes. Do not invent runtime test results for an
intake-only change.

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
Monday schedule is 07:15 America/Los_Angeles (10:15 America/New_York while
both locations observe the same daylight-saving offset). The workflow's IANA
time zone, not the cron text alone, determines the run time. Packet findings
are advisory; before acting,
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

For a missed-review gap, run a bounded catch-up with a known successful
review's full commit SHA as `-BaseRef`; the manual workflow exposes the same
value as `base_commit`. The script verifies that commit exists and is an
ancestor of the selected head. It records `explicit-base`, exact base/head
and a null time window rather than silently reverting to seven days. Missing,
invalid or unrelated bases fail comparison-dependent packets while retaining
independent engineering status. Do not combine `-Since` and `-BaseRef`.

```powershell
./scripts/generate-maintenance-packets.ps1 -OutputDirectory "$env:TEMP/planner-catch-up" -BaseRef <full-reviewed-ancestor-SHA>
```

The reviewer selects and records the prior successful review and resulting
head, then verifies the whole final maintenance gate and retained packets.
This manual catch-up does not create a durable unattended checkpoint or prove
every historical interval was reviewed. Scheduled runs still use the disclosed
rolling window. Healthy unchanged runs remain summary/artifact-only; no new
notification channel or write permission is needed.

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

Dependabot auto-queueing is narrower than update discovery: only allowlisted
npm development-tool patch updates with matching manifest/lockfile scope and
unchanged other manifest fields qualify. Mixed minor/patch groups remain manual
because metadata reports their highest change. Runtime/framework/security,
Maven and Actions updates remain manual. The privileged workflow loads only
its trusted base revision's eligibility helper, never PR code; provider gaps
fail closed and the queue request matches the event head. Existing required
checks still govern the merge. Run the helper's synthetic negative cases with
`node --test .github/scripts/dependabot-eligibility.test.mjs`; fixtures are not
evidence of a real eligible update being merged.

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
