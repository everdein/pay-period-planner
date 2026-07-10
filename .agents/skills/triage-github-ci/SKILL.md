---
name: triage-github-ci
description: Inspect and diagnose GitHub Actions and Snyk failures for end-to-end-app, classify root causes, reproduce relevant checks locally, explain dependency paths and fixed versions, and propose or implement scoped fixes. Use when pull-request or main-branch checks fail, hang, skip unexpectedly, lack authentication, or report dependency vulnerabilities.
---

# Triage GitHub CI

1. Read `AGENTS.md`, `.github/workflows/ci.yml`, and
   [references/triage-guide.md](references/triage-guide.md).
2. Identify the exact repository, commit SHA, workflow run, attempt, event,
   failing job, and first actionable failing step through the GitHub connector
   or `gh`. Do not diagnose from a PR badge or summary alone.
3. Inspect annotations and the relevant log window. Separate the primary
   failure from cancellation, dependency-skipped jobs, and cleanup noise.
4. Classify the root cause as code/test, runner/tooling, workflow/configuration,
   permissions/secrets, dependency/security, or external service.
5. Reproduce code-owned failures with the exact workflow command. Use
   `.\scripts\verify-local.ps1` for the complete non-security suite and
   `.\scripts\run-security-checks.ps1` only with authenticated Snyk access.
6. For Snyk, record the scanned project/manifest, vulnerable package, installed
   version, introduced-through path, severity, advisory identifier, exploit
   maturity when available, fixed version, and whether the fix is direct,
   transitive, breaking, or unavailable. Never retrieve or print secret values.
7. Propose the smallest safe fix. For upgrades, check both lockfiles, runtime
   compatibility, release notes, and whether an override merely masks an
   incompatible dependency. Implement only when the user requested a fix.
8. Rerun the relevant local checks. If changes are pushed, inspect the new run
   rather than assuming hosted success.
9. Report root cause, evidence, affected checks, proposed or applied fix,
   verification, and checks that remain hosted-only.

Keep workflow `permissions` at least privilege. Pin security tooling versions
when changing installation steps so scans remain reproducible. Never classify
missing `SNYK_TOKEN`, rate limits, outages, or an unauthenticated scan as clean.
