# Pull Request Preparation Checklist

## Scope and Safety

- Confirm the base branch and ensure the working branch is not `main`.
- Separate unrelated pre-existing changes from the intended commit.
- Review staged additions, deletions, renames, binaries, generated files, and
  lockfile changes.
- Exclude `backend/data/financials.local.json`, database dumps, logs, coverage
  output, build output, IDE state, credentials, and tokens.
- Use only synthetic values in tests, screenshots, examples, and PR text.

## Behavior and Documentation

- Trace frontend, API, service, JSON, PostgreSQL, and CI impacts.
- Preserve the full-snapshot contract or describe intentional compatibility and
  migration consequences.
- Update root/subproject READMEs, `AGENTS.md`, operational scripts, known
  limitations, and architecture artifacts where ownership requires it.
- Add a new ADR when the change alters an accepted architectural decision.
- Include additive migrations and rollback/recovery notes for schema changes.

## Verification Matrix

| Change surface        | Required evidence                                             |
| --------------------- | ------------------------------------------------------------- |
| Documentation only    | Spell check, links/commands checked                           |
| Frontend              | Typecheck, lint, tests with coverage, build                   |
| Backend/API           | Formatting, tests, JaCoCo gate, package build                 |
| PostgreSQL            | Full local verification, including isolated integration tests |
| Dependencies/security | Lockfiles, compatibility tests, authenticated scan            |
| CI workflow           | Local equivalents plus hosted run after push                  |
| UI behavior           | Relevant tests and screenshot or explicit reason omitted      |

Use `scripts/verify-local.ps1` as the default aggregate check. Treat hosted,
credentialed, or database checks as skipped—not passed—until they run.

## Commit

- Inspect the exact staged diff and staged filenames.
- Confirm no conflict markers, whitespace errors, personal data, or secrets.
- Use an imperative subject; explain non-obvious rationale in the body.
- Do not mix unrelated cleanup with the intended outcome.
- Record verification before committing so the handoff is reproducible.

## Draft Pull Request Body

Use the repository template when one exists. Otherwise include:

```markdown
## Summary

- What changed
- Why it changed

## Verification

- [x] Checks that passed
- [ ] Hosted or optional checks still pending, with reason

## Data and migration impact

- JSON/PostgreSQL/API compatibility and recovery notes, or “None”

## Security

- Authenticated scan status and dependency impact, or “Not run” with reason

## UI evidence

- Screenshot/behavior notes, or “Not applicable”

## Risks and follow-ups

- Known limitations, rollout concerns, and deferred work
```

Never include credentials, personal financial values, or full security logs in
the PR body.
