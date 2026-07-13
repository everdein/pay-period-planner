# MCP Integration Guide

This guide defines how Codex agents should use external MCP/app integrations
for this repository. The goal is simple: make live external context useful
without turning every agent session into an unrestricted GitHub, database, or
browser operator.

Use MCP when the task needs current external state that is not already in the
workspace, such as pull request checks, review comments, issue metadata, or
service-specific logs. Prefer local files and deterministic scripts when the
answer can be obtained from the checkout.

## Repository Stance

- Keep personal authentication in user-level Codex, GitHub CLI, browser, or
  keyring state. Do not commit tokens, OAuth material, personal account
  configuration, or machine-specific MCP credentials.
- Do not add project-scoped `.codex/config.toml` for GitHub solely to capture a
  personal setup. If a future team-shared MCP config is added, it must contain
  only non-secret server metadata and must work in trusted projects only.
- Treat MCP/app tools as external systems. Reads are normal when they stay
  within the user-provided repository, branch, PR, issue, or run scope. Writes
  require explicit user intent.
- If an MCP connector is unavailable, record that capability as unavailable and
  use `gh` only when authenticated and within the same boundaries.
- Never send personal financial values, local JSON contents, database rows,
  credentials, or secret values through GitHub comments, PR bodies, issue text,
  logs, artifacts, or external tools.

## GitHub MCP

### Purpose

Use the GitHub MCP connector or GitHub app integration for structured GitHub
state:

- repository, branch, and commit metadata;
- pull request details, changed files, commits, review comments, and reviews;
- check runs, commit statuses, annotations, and workflow failure evidence;
- issue metadata and labels when an issue is in scope;
- draft PR creation when the user asked to publish local changes.

Use `gh` as a fallback for Actions log details, PR discovery, or operations not
available through the connector. Use local `git` for local branch, diff,
commit, and push operations.

### Setup Checklist

For a new machine or Codex surface:

1. Install or enable the GitHub connector/plugin, or configure a GitHub MCP
   server in user-level Codex configuration.
2. Authenticate with the least privilege needed for the workflow. Routine
   repository work needs repository read/write only when publishing is
   expected; Actions or security triage may also need workflow/check access.
3. Verify identity before relying on MCP writes. With MCP, call the equivalent
   of `get_me`; with the CLI, run:

   ```powershell
   gh auth status
   ```

4. Verify repository access by reading a known PR, branch, or check run.
5. Keep OAuth and token storage in keyring, browser/app authorization, or
   environment variables. Do not paste tokens into prompts or commit them.
6. If a credentialed check cannot run, report it as skipped or unavailable.
   Never convert missing authentication into a clean result.

### Read/Write Boundary

| Action type                     | Default stance             | Examples                                                         |
| ------------------------------- | -------------------------- | ---------------------------------------------------------------- |
| Scoped reads                    | Allowed when relevant      | Read PR metadata, changed files, checks, comments, branch names  |
| Hosted status inspection        | Allowed when relevant      | Inspect check runs, Snyk status, GitGuardian status, annotations |
| Local publication               | User request required      | Commit, push, delete local branches                              |
| External GitHub writes          | User request required      | Create/update draft PRs, comments, labels, reviews, issues       |
| Issue-to-implementation reads   | Allowed when relevant      | Read scoped issue body, labels, comments, links, and status      |
| Issue mutation                  | User request required      | Comment, label, assign, close, convert, transfer, or milestone   |
| Branch cleanup on GitHub        | Explicit user request only | Delete merged remote branches after checking PR/merge state      |
| Workflow or check mutation      | Explicit user request only | Rerun jobs, cancel runs, request reviewers                       |
| Repository administration       | Separate explicit approval | Secrets, branch protection, environments, webhooks, permissions  |
| Destructive or irreversible act | Separate explicit approval | Force-push, merge, close active PRs/issues, delete active branch |

Do not use GitHub MCP to edit repository files directly when the local checkout
is available. Make source changes locally, verify them, commit intentionally,
push the branch, and then use GitHub only for review/pull-request metadata.

### Pull Request Workflow

When the user asks to publish changes:

1. Inspect `git status`, the diff, current branch, intended base, and remote.
2. Stage only intended files and commit locally.
3. Push only the topic branch.
4. Search for a pull request template.
5. Open a draft PR by default unless the user asks for ready-for-review.
6. Include summary, rationale, verification, data/security status, and
   remaining risk in the PR body.
7. Inspect initial checks after creation. Report hosted checks as pending,
   failed, skipped, or passed only from the actual GitHub state.

Do not manually request Copilot reviews, human reviewers, labels, merge, close,
change the base branch, or mark ready-for-review unless the user asks for that
specific action. Repository automation may request Copilot review according to
`docs/github-ai-workflows.md`.

### CI and Snyk Triage

Use the GitHub connector or `gh` to identify the exact run, attempt, commit,
event, job, and first actionable failing step. Do not diagnose from badges or a
summary alone.

For logs and annotations:

- collect only the relevant window around the first actionable failure;
- avoid copying secret-like values, tokens, or personal financial data;
- classify failures as code/test, runner/tooling, workflow/configuration,
  permissions/secrets, dependency/security, or external service;
- rerun only failures shown to be transient, and only when the user asks for
  reruns or the task clearly includes CI recovery.

For Snyk, report authentication state, scanned project/manifest, package,
installed version, vulnerable path, severity, advisory identifier, fixed
version, and compatibility risk. Missing `SNYK_TOKEN`, missing Snyk access, or
an unavailable service is not a pass.

### Branch Cleanup

Before deleting a local or remote branch:

1. Fetch/prune remote refs.
2. Confirm the branch is not the current branch, default branch, or base of an
   open PR.
3. Confirm associated PRs are merged or closed intentionally.
4. Confirm the branch is merged into the intended protected base, or obtain
   explicit user approval to delete an unmerged branch.
5. Delete local branches with safe deletion first. Delete remote branches only
   when the user asked to clean up remote branches.

Preserve stacked branches that still back open PRs.

## PostgreSQL MCP

### Purpose

Use a PostgreSQL MCP server only for database inspection that genuinely needs
live local database state. Prefer `scripts/inspect-postgres.ps1` when a static,
repeatable report is enough.

Allowed inspection targets:

- connectivity and current role;
- schema/table presence;
- role and privilege metadata;
- row counts;
- Flyway history metadata when present;
- active snapshot document ID, active flag, version, timestamps, JSON type, and
  collection counts.

Do not retrieve, summarize, paste, screenshot, or export full `snapshot_json`
values or normalized financial rows. Those may contain personal financial data.

### Role Setup

Use a dedicated read-only role for PostgreSQL MCP, reporting, and inspection:

```powershell
.\scripts\setup-postgres-readonly-role.ps1
```

Default local role:

```text
financial_app_reader
```

The setup script prompts for the PostgreSQL administrator password and the
read-only-role password. Keep those credentials in local keyring, environment,
or MCP-server-specific secret storage. Do not commit them, paste them into
prompts, or put them in project-scoped `.codex/config.toml`.

If a PostgreSQL MCP server needs a connection string, configure it at the
user-level with the read-only role and a secret source, for example:

```text
postgresql://financial_app_reader@localhost:5432/financial_app
```

Provide the password through the MCP server's supported secret mechanism rather
than embedding it in the URL.

### Read/Write Boundary

| Action type                 | Default stance             | Notes                                                    |
| --------------------------- | -------------------------- | -------------------------------------------------------- |
| Schema/privilege reads      | Allowed when relevant      | Use catalog and information-schema reads                 |
| Row counts and metadata     | Allowed when relevant      | Counts, IDs, versions, timestamps, JSON keys/types only  |
| Full financial data reads   | Prohibited by default      | Requires explicit user approval and a redaction plan     |
| DDL, DML, migrations, setup | Not an MCP task            | Use repository scripts or administrator tools explicitly |
| App runtime connection      | Use application role only  | Never run the app as the read-only MCP/reporting role    |
| MCP/reporting connection    | Use read-only role only    | Never connect MCP/reporting tools as database owner      |
| Backups, exports, recovery  | Separate explicit approval | Treat outputs as personal financial data                 |

Every live inspection should use explicit read-only transactions when the MCP
server allows it. If a connector cannot enforce read-only behavior, do not use
it with personal local data.

## Browser and Playwright

### Purpose

Use browser automation for UI workflow evidence that unit/component tests do
not provide: real browser rendering, keyboard/focus behavior, Vite startup,
route interception, screenshots, and save/load interaction paths.

The repository provides an opt-in Playwright smoke test:

```powershell
.\scripts\run-browser-checks.ps1
```

On a new machine, install the local Playwright Chromium browser first:

```powershell
.\scripts\run-browser-checks.ps1 -InstallBrowsers
```

The smoke test starts Spring Boot and the Vite dev server. Spring Boot uses the
`json` profile with a disposable data path under `test-results/`, seeded from
committed synthetic example data. The browser covers load, edit, save, refresh
persistence, delete confirmation, and post-delete refresh while avoiding
personal local data.

### Browser Boundary

| Action type                       | Default stance             | Notes                                                  |
| --------------------------------- | -------------------------- | ------------------------------------------------------ |
| Live synthetic Playwright smoke   | Allowed when relevant      | Uses temp JSON data and local browser artifacts        |
| Manual/browser visual inspection  | Allowed when scoped        | Avoid screenshots containing personal data             |
| Live backend browser testing      | Explicitly scope target    | Use JSON or PostgreSQL profile intentionally           |
| Personal financial data browsing  | Avoid by default           | Requires explicit approval and redacted reporting      |
| Browser screenshots/videos/traces | Treat as potentially local | Do not commit unless the task explicitly asks for them |
| External website/browser access   | User approval as required  | Stay within task scope and connector/browser policy    |

Prefer synthetic data for workflow tests. If a live backend is required, state
the profile, port, dataset, and whether any local/personal data may be visible.
Generated `playwright-report/` and `test-results/` output is ignored.

## Snyk MCP/API

### Purpose

Use Snyk MCP/API only for security triage that genuinely benefits from live
Snyk context, local Snyk scanner execution, or authorized Snyk project/advisory
metadata. The repository's canonical security gates remain CI `Snyk test` and
`scripts/run-security-checks.ps1`.

See `docs/snyk-integration-assessment.md` for the full feasibility decision,
official source links, setup boundary, and reporting template.

### Repository Stance

- Keep Snyk MCP configuration user-scoped. Do not commit Snyk MCP entries,
  tokens, personal Codex `config.toml`, OAuth material, or generated Snyk
  secrets.
- Prefer the Snyk MCP `lite` profile for routine agent sessions. Use broader
  profiles only when intentionally expanding the scan surface.
- Treat Snyk MCP scans as local tool execution. They may invoke package-manager
  or build-system tooling to resolve dependencies.
- Use Snyk API only with explicit user-provided organization/project scope and
  an authorized token. API triage can support decisions, but it does not replace
  CLI/CI scan evidence for this repository.

### Read/Write Boundary

| Action type                    | Default stance             | Notes                                                         |
| ------------------------------ | -------------------------- | ------------------------------------------------------------- |
| Auth/status/version reads      | Allowed when relevant      | Do not display token values                                   |
| Local Snyk scan on repo source | Allowed when authenticated | Report tool, profile, manifest, severity, and advisory facts  |
| Advisory/package explanation   | Allowed when scoped        | Propose upgrades with compatibility and verification evidence |
| Snyk API read triage           | Explicit scope required    | User/org/project/token state must be clear                    |
| Snyk authentication/logout     | User approval required     | Changes local account/tool state                              |
| Trusting folders/local state   | User approval required     | Do not silently trust workspaces                              |
| Policy ignores or exceptions   | Explicit owner decision    | Record rationale and revisit condition                        |
| Snyk API writes/settings       | Separate explicit approval | Includes project, org, service-account, and policy mutation   |
| Personal financial data scans  | Prohibited by default      | Do not scan ignored JSON, exports, screenshots, logs, or DBs  |

Missing Snyk authentication, missing API entitlement, unavailable network, or a
failed Snyk service is not a pass. Report it as skipped or unavailable.

## Future Integration Slots

These P3 items are tracked in `docs/ai-enablement-roadmap.md` and should extend
this guide as they are implemented:

- Observability: defer until production telemetry exists.

Filesystem MCP remains intentionally unnecessary for this repository because
Codex already has workspace filesystem access.
