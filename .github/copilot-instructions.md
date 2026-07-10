# GitHub Copilot Repository Instructions

Use `AGENTS.md` as the primary repository guide. Use the closest README and
the documents in `docs/` for architecture, API, persistence, verification, and
data-safety details.

When reviewing pull requests, prefer actionable findings over style-only
feedback. Prefix each review comment with a severity and area tag:

- `[critical][security]` for credential exposure, personal financial data
  exposure, destructive data loss, or a required security/CI gate being
  bypassed.
- `[high][api]`, `[high][database]`, `[high][frontend]`, or `[high][ci]` for
  changes that break the snapshot contract, persistence safety, migrations,
  accessibility-critical behavior, or required verification.
- `[medium][tests]`, `[medium][docs]`, or `[medium][architecture]` for missing
  coverage, documentation drift, cross-layer mismatch, or unclear ownership.
- `[low][maintainability]` for non-blocking clarity, naming, or cleanup.
- `[question][area]` when a concern depends on an assumption that should be
  verified by the author.

For each finding, include the impact, the concrete evidence in the diff, the
recommended fix, and the verification command or manual check that would prove
the fix. Do not count Copilot review as a human approval or as a replacement
for CI, Snyk, PostgreSQL smoke tests, browser checks, or manual review.

When summarizing a pull request, use the PR template and the PR summary packet
from GitHub Actions. Include summary, impact, verification, data/security
posture, skipped checks, and follow-up risk. Do not claim hosted checks passed
unless GitHub reports them as passed.

When summarizing a failed workflow run, use the CI failure summary packet plus
only the smallest relevant log window. Identify the first actionable failing
job/step, root-cause category, likely local reproduction command, and remaining
hosted checks. Do not paste full logs or secret-like values.

When helping with an issue, use the issue forms and
`docs/issue-to-implementation-workflow.md` to confirm scope, acceptance
criteria, verification, and data-safety state before proposing implementation.

Project-specific review priorities:

- Treat `backend/data/financials.local.json`, PostgreSQL contents, exports,
  logs, screenshots, and credentials as sensitive personal data.
- Preserve the full financial snapshot API contract unless the pull request
  intentionally changes it and updates frontend, backend, tests, and docs.
- The default runtime profile is JSON. PostgreSQL is opt-in and stores the
  active aggregate in `financial_snapshot_document.snapshot_json`; normalized
  V1 tables are groundwork, not active persistence.
- Keep migrations additive and never edit an applied migration.
- Require focused tests for behavior changes and report skipped security,
  PostgreSQL, browser, or hosted checks explicitly.
