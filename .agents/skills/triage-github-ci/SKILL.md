---
name: triage-github-ci
description: Diagnose and fix GitHub Actions failures for end-to-end-app, including frontend checks, Maven checks, workflow permissions, repository secrets, and authenticated Snyk scans. Use when a pull request or main-branch pipeline is failing, stuck, skipped, or producing security findings.
---

# Triage GitHub CI

1. Read `.github/workflows/ci.yml` and inspect the exact run, job, and failing
   step through the GitHub MCP server or `gh`.
2. Separate failures into code/test, runner/tooling, permissions/secrets,
   dependency/security, and external-service categories.
3. Reproduce code failures locally with the command from the workflow. Use
   `.\scripts\verify.ps1` for the complete non-security check set.
4. For Snyk:
   - confirm the workflow references only `SNYK_TOKEN`;
   - never print or retrieve the secret value;
   - distinguish missing authentication from actual findings;
   - capture project, package, introduced-through path, severity, and fixed
     version from the authenticated result;
   - avoid equating `npm audit` with Snyk coverage.
5. Apply the smallest code or workflow fix that addresses the verified cause.
6. Rerun local checks, then inspect the new GitHub Actions result.
7. Report the root cause, evidence, fix, and any check that still requires the
   hosted pipeline.

Keep workflow `permissions` at least privilege. Pin security tooling versions
when changing installation steps so scans remain reproducible.
