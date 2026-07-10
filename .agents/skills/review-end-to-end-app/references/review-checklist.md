# End-to-End App Review Checklist

Use only sections relevant to the review scope. Trace cross-layer changes
through every affected section.

## Severity

- **P0:** Active exploitation, unrecoverable personal-data loss, or widespread
  outage; block immediately.
- **P1:** Likely data corruption/loss, security boundary failure, broken primary
  workflow, or incompatible API/persistence change; block merge.
- **P2:** Real defect with a narrower trigger, important missing validation, or
  meaningful regression risk; fix before release.
- **P3:** Low-impact correctness or maintainability issue with a concrete future
  failure mode; fix when practical.

Do not assign severity from code appearance alone. State the reachable trigger
and user or operator impact.

## Frontend

- Preserve load, draft edit, cancel, save, and save-error behavior.
- Check immutable Redux updates, stable identifiers, derived versus persisted
  fields, stale closures, effect dependencies, and async race conditions.
- Verify financial arithmetic, rounding/display boundaries, sign handling,
  empty states, and date/time-zone behavior.
- Verify API failures remain visible and failed saves do not overwrite the
  user's draft.
- Check semantic HTML, labels, keyboard access, focus behavior, modal behavior,
  error announcements, and meaningful control names.
- Require focused interaction or helper tests for changed behavior.

## Backend and API

- Preserve request/response compatibility or document an intentional contract
  change across frontend types, DTOs, controller, and tests.
- Keep controllers at the HTTP boundary, business rules in services, and
  persistence behind repository interfaces.
- Check validation, null/empty handling, numeric bounds, dates, identifiers,
  status codes, and exception-to-response mapping.
- Verify derived totals and dates cannot silently discard or rewrite persisted
  fields.
- Check full-snapshot replacement semantics, stale writes, partial failures,
  and error recovery.
- Require regression tests for defects and boundary tests for contract changes.

## PostgreSQL and JSON Persistence

- Keep JSON and PostgreSQL behavior equivalent for load, seed, save, IDs,
  versions, and serialization.
- Treat `financial_snapshot_document` as the active PostgreSQL store; do not
  assume normalized V1 tables contain application data.
- Require additive migrations. Check constraints, indexes, transaction
  boundaries, locking/concurrency, rollback behavior, and Flyway ordering.
- Check empty-database seeding and ensure personal local data cannot be
  overwritten, logged, committed, or included in fixtures.
- Use read-only inspection for diagnosis. Run isolated PostgreSQL tests for
  persistence changes.

## CI, Dependencies, and Security

- Keep workflow permissions least-privilege and third-party actions pinned to
  an intentional version.
- Verify job dependencies cannot skip required lint, typecheck, tests, builds,
  coverage, or scans.
- Check cache keys and lockfiles correspond to the project being installed.
- Never expose secrets through command output, artifacts, pull-request contexts,
  or untrusted scripts.
- Distinguish missing Snyk authentication/tooling from an actual clean scan.
- For dependency changes, review direct and transitive impact, fixed versions,
  runtime compatibility, and both lockfiles.

## Tests and Coverage

- Confirm tests execute the changed branch and fail without the implementation.
- Check assertions validate outcomes rather than only rendering or status.
- Look for missing error, empty, boundary, date, concurrency, persistence-mode,
  and accessibility cases.
- Do not accept lowered thresholds or broad exclusions as a substitute for
  tests.
- Report checks not run and explain whether the gap is local, hosted,
  credentialed, or database-dependent.

## Finding Quality

Each finding must include:

1. A short severity-tagged title.
2. The smallest useful changed-file line range.
3. A concrete trigger and observed or logically necessary behavior.
4. User, data, security, or operational impact.
5. A focused remediation direction.

Avoid duplicates: report one root cause and mention affected consumers in its
body.
