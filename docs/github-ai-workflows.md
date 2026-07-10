# GitHub AI Workflows

This repository uses hosted AI assistance only as an advisory layer. AI review
can help triage pull requests faster, but it does not replace human review,
required CI, security scans, PostgreSQL checks, browser checks, or explicit
owner approval for risky changes.

## Copilot Review Requests

The repository includes `.github/workflows/copilot-review.yml`. It runs on
`pull_request_target` for opened, reopened, and ready-for-review pull requests.
It does not check out or execute pull request code. Its only write is a GitHub
review request for:

```text
copilot-pull-request-reviewer[bot]
```

The workflow skips draft pull requests and requests Copilot when the pull
request becomes ready for review. It exits successfully if Copilot review is
unavailable, disabled, already requested, budget-limited, or blocked by
permissions/policies. That failure mode is deliberate: Copilot review is
assistive, not a merge gate.

Repository or organization owners may alternatively enable GitHub's built-in
automatic Copilot review rulesets. If a ruleset is enabled, avoid adding
duplicate manual requests unless the user explicitly wants a re-review.

## Required GitHub Configuration

For the workflow to do useful work, GitHub Copilot code review must be enabled
for the repository, organization, or user creating the pull request. GitHub
documents both manual reviewer requests and repository/organization rulesets
for automatic review. Plan, policy, and billing controls can prevent a review
even when the workflow is present.

Use the lowest review effort level that gives useful signal. Medium effort can
perform deeper analysis of complex logic, security-sensitive code, and
cross-service changes, but GitHub documents it as public preview and notes that
it can consume more Actions minutes and AI credits.

## Review Comment Severity

`.github/copilot-instructions.md` asks Copilot to categorize comments with this
format:

```text
[severity][area] finding summary
```

Severity levels:

- `[critical]`: credential exposure, personal financial data exposure,
  destructive data loss, or a required CI/security gate being bypassed.
- `[high]`: API contract breakage, unsafe persistence/migration behavior,
  accessibility-critical regression, or required verification failure.
- `[medium]`: missing coverage, documentation drift, cross-layer mismatch, or
  ambiguous ownership.
- `[low]`: maintainability, naming, or clarity issue that does not block the
  change.
- `[question]`: a concern that depends on an assumption the author should
  verify.

Area examples include `[security]`, `[api]`, `[database]`, `[frontend]`,
`[backend]`, `[ci]`, `[tests]`, `[docs]`, `[accessibility]`, and
`[architecture]`.

Every actionable AI review comment should identify impact, evidence,
recommended fix, and verification. Treat comments without enough evidence as
questions or suggestions, not blockers.

## PR Summaries

The repository includes `.github/PULL_REQUEST_TEMPLATE.md` and
`.github/workflows/pr-summary.yml`.

The template gives authors and AI assistants a stable structure:

- summary;
- exact verification;
- data and security notes;
- follow-up links, known limitations, or stacked PR context.

The summary packet workflow runs on `pull_request` with read-only permissions.
It checks out the pull request only to compute changed-file metadata, then
writes a GitHub Actions job summary through `GITHUB_STEP_SUMMARY`. The packet
is deterministic context for an author, Copilot, Codex, or reviewer to turn
into prose. It is not evidence that CI passed.

AI-generated PR summaries must stay evidence-based:

- describe what changed and why;
- identify user/developer impact;
- list commands and hosted checks actually run;
- call out skipped checks and why;
- state data/security posture without exposing personal financial values,
  secrets, raw database rows, or full local JSON snapshots.

## Failure-Log Summaries

The repository includes `.github/workflows/ci-failure-summary.yml`. It runs
after the `CI` workflow completes with a non-success conclusion and writes a
separate failure summary packet.

The failure packet records run metadata and failed/cancelled/incomplete job
links. It intentionally does not download or post raw logs. Raw logs can be
large and may contain sensitive context even when GitHub masks configured
secrets.

When producing an AI-assisted failure summary:

1. Start from the failure packet, run URL, attempt, SHA, and failed job list.
2. Inspect only the smallest relevant log window around the first actionable
   error.
3. Classify the failure as code/test, runner/tooling,
   workflow/configuration, permissions/secrets, dependency/security, or
   external service.
4. Include likely local reproduction commands from `docs/verification-matrix.md`
   or `.agents/skills/triage-github-ci/references/triage-guide.md`.
5. Redact tokens, secret-like values, personal financial values, raw database
   rows, full local JSON, screenshots, and Snyk token material.

Do not rerun jobs, post comments, open issues, or edit PR bodies from a
summary workflow unless the user explicitly asks for that external write.

## Issue-to-Implementation Assistance

Issue forms under `.github/ISSUE_TEMPLATE/` collect enough structure for a
human or AI agent to decide whether a branch is ready:

- objective or bug summary;
- affected area;
- acceptance criteria;
- expected verification;
- data-safety confirmation;
- explicit implementation-readiness signal.

Use `docs/issue-to-implementation-workflow.md` before turning an issue into
code. Reading issue metadata is normal when the issue is in scope. Commenting,
labeling, closing, assigning, or creating PRs from issues are external writes
and require user intent.

## Documentation-Drift Assistance

The repository includes `scripts/check-documentation-drift.ps1` and
`.github/workflows/documentation-drift.yml`.

The script compares changed files with documented source owners, checks that
source-map path references resolve to repository files, and emits a
documentation drift packet. The GitHub workflow runs the same script for pull
requests and manual dispatch, then writes the packet to the Actions job
summary. Packet generation is advisory: if the hosted runner cannot generate
the packet, the workflow writes a warning summary instead of blocking the pull
request.

Use the packet to decide whether a documentation audit or correction is needed.
It is deterministic context, not a final judgment. A flagged risk can be a
false positive when the change is internal-only, and an unflagged change can
still need documentation if it changes behavior. Before posting a finding or
editing docs, verify claims against `.agents/skills/audit-end-to-end-docs/`,
the source map, executable files, and existing docs.

## Dependency and Maintenance Assistance

Dependabot is configured in `.github/dependabot.yml`.
`.github/workflows/dependency-update-triage.yml` generates dependency triage
packets for dependency-related pull requests and manual runs. Use
`docs/dependency-update-triage.md` before accepting or rejecting generated
updates. Like documentation drift packets, dependency triage packets are
advisory and fail open with a warning summary when packet generation is
unavailable.

`.github/workflows/weekly-maintenance.yml` runs scheduled maintenance review
and writes weekly packets for dependency, CI, documentation, security, and
repository-health review. Use `docs/maintenance-review-workflow.md` before
turning those packets into an engineering-status report.

These packets are safe context for AI summarization. They are not authorization
to merge, close issues, request reviewers, dismiss alerts, edit Snyk policy, or
change repository settings.

## Boundaries

- Copilot review comments do not count as human approval.
- Copilot review comments do not replace `scripts/verify-local.ps1`,
  authenticated Snyk scans, PostgreSQL smoke tests, Playwright/browser checks,
  or required GitHub Actions jobs.
- Do not ask Copilot to inspect, paste, or reason over personal financial
  values from ignored JSON, PostgreSQL rows, logs, screenshots, or exports.
- Do not accept "Fix with Copilot" changes without reviewing the diff and
  rerunning the relevant verification.
- If Copilot suggests changing `.snyk` policy, branch rulesets, GitHub
  permissions, repository secrets, database roles, migrations, or data recovery
  behavior, require explicit owner review.

## Operating Checklist

When using Copilot review on a pull request:

1. Confirm CI and required local checks still define merge readiness.
2. Read Copilot comments like human review comments: validate evidence before
   acting.
3. Categorize any copied or summarized findings by severity and area.
4. If comments are false positives, resolve or hide them with a clear reason.
5. If a re-review is needed after fixes, request one intentionally; do not
   assume every push is re-reviewed unless a GitHub ruleset is configured for
   new pushes.
