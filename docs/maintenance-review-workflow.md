# Maintenance Review Workflow

This repository uses scheduled GitHub Actions and deterministic local scripts
to prepare maintenance review packets. The packets help humans and AI agents
decide what needs attention; they do not mutate issues, labels, branches,
secrets, dependency policy, or repository settings.

## Scheduled Review

`.github/workflows/weekly-maintenance.yml` runs weekly and on manual dispatch.
It reviews:

- dependency state through `scripts/triage-dependency-updates.ps1`;
- documentation drift through `scripts/check-documentation-drift.ps1`;
- repository health through `scripts/generate-engineering-status.ps1`;
- spelling through `npm run spell`;
- root and frontend high-severity npm audit state;
- recent hosted CI runs through GitHub CLI metadata.

The workflow writes job summaries. It does not open issues, comment on PRs,
assign owners, rerun jobs, dismiss alerts, or merge updates.

## Weekly Engineering Status

Generate the local status packet:

```powershell
.\scripts\generate-engineering-status.ps1
```

The packet includes:

- current branch and commit;
- open roadmap checklist items;
- workflow inventory;
- deterministic local command inventory;
- current worktree state;
- weekly review checklist.

Use it as a starting point for an engineering-status report. Before sharing a
status report, verify live GitHub, CI, Snyk, dependency, and issue/PR state
from the relevant source. Do not include secrets, personal financial values,
database rows, full local JSON, or raw logs.

## Dependency Review

Dependabot opens dependency-update PRs according to `.github/dependabot.yml`.
Use `docs/dependency-update-triage.md` and the dependency triage packet before
accepting, splitting, holding, or superseding an update.

Major updates, security policy changes, Snyk ignores, repository secrets,
GitHub permissions, and CI behavior changes require explicit owner review.

## CI and Security Review

For failing CI, use `.agents/skills/triage-github-ci/references/triage-guide.md`
and the CI failure summary packet. For security review, prefer the hosted Snyk
scan and `scripts/run-security-checks.ps1` when authenticated tooling and
network access are available.

Missing `SNYK_TOKEN`, unavailable Snyk access, skipped npm audit, or an
unavailable external service is not a pass.

Dependabot-triggered GitHub Actions runs may not receive repository secrets.
When the internal Snyk CLI scan is skipped for that reason, report it as
skipped evidence and verify the external Snyk PR check or rerun the security
check manually with owner-approved credentials.

## Repository Health Review

Weekly repository health review should look for:

- stale issue or PR queues;
- failing or skipped hosted checks;
- unmerged Dependabot/security updates;
- documentation drift packets with unresolved risk;
- roadmap items that need owner decisions;
- known limitations whose revisit trigger has occurred.

External writes still require explicit intent. Do not comment, label, close,
assign, rerun, cancel, merge, or change settings from a packet alone.
