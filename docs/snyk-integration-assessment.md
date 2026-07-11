# Snyk MCP/API Feasibility Assessment

Assessed on 2026-07-09 against official Snyk documentation and the current
repository scripts/workflows.

## Decision

Snyk MCP/API integration is feasible and useful for local security triage, but
it should remain user-scoped or CI-scoped for now. Do not commit Snyk tokens,
personal MCP configuration, or project-scoped Codex MCP settings for Snyk.

The canonical repository gates remain:

- CI `Snyk test` when repository secrets are available
- `scripts/run-security-checks.ps1`

Snyk MCP can help an agent run or explain scans on a developer machine. Snyk
API can support future Enterprise automation. Neither replaces authenticated
Snyk CLI/CI evidence for this repository.

## Official Capability Snapshot

- [Snyk Studio](https://docs.snyk.io/integrations/snyk-studio-agentic-integrations)
  provides a Snyk MCP Server for agentic development workflows.
- The [Snyk Studio getting-started guide](https://docs.snyk.io/integrations/snyk-studio-agentic-integrations/getting-started-with-snyk-studio)
  describes the MCP server as local, CLI-backed, and not available as a hosted
  remote Snyk MCP service.
- The same guide documents `lite`, `full`, and `experimental` MCP profiles.
  Use `lite` by default for agent sessions; use `full` only when broader
  Snyk coverage is intentionally needed; avoid `experimental` unless the task
  explicitly asks to evaluate preview tools.
- Snyk notes that an SCA scan can run ecosystem tools such as Maven or Gradle
  to build a dependency tree. Treat Snyk MCP scans as local tool execution and
  run them only in a trusted checkout.
- [Snyk API authentication](https://docs.snyk.io/snyk-api/authentication-for-api)
  requires a Snyk token, and Snyk recommends service accounts for automation
  on Enterprise plans.
- [Snyk API overview](https://docs.snyk.io/snyk-api/snyk-api)
  says the API is useful for automation, but local CLI scans are more accurate
  for many package-manager projects because they test the actual dependency
  snapshot. The CLI can emit machine-readable JSON.

## Recommended Local Setup

Keep setup in the user's machine-level Codex configuration or Snyk CLI state,
not in the repository. A user-level Codex MCP entry can map to the local Snyk
MCP command:

```toml
[mcp_servers.snyk]
command = "npx"
args = ["-y", "snyk@latest", "mcp", "-t", "stdio", "--profile=lite"]
```

This intentionally uses no committed token. Authenticate through the Snyk CLI
or the MCP server's supported `snyk_auth` flow when the user chooses to do so.
If a team later standardizes Snyk MCP versions, pin the Snyk CLI/package in the
team-managed setup rather than in this repository's source tree.

## Allowed Uses

- Inspect Snyk authentication state, CLI/MCP version, and selected MCP profile.
- Run Snyk scans on this repository's tracked source and dependency manifests.
- Explain Snyk CI failures using package, manifest, vulnerable path, severity,
  advisory identifier, fixed version, and compatibility risk.
- Propose dependency upgrades, then verify with normal build/test commands.
- Use Snyk API for read-oriented Enterprise triage when the user provides an
  authorized token and the organization/project scope is explicit.

## Approval Required

- Authenticating, logging out, trusting a folder, or changing Snyk local state.
- Uploading or monitoring a project outside the existing CI/security workflow.
- Adding `.snyk` policy files, ignores, severity changes, or dependency
  exceptions.
- Creating Snyk, GitHub, Jira, Slack, or issue-tracker records from findings.
- Scanning directories outside this repository.
- Scanning ignored local financial data, database exports, screenshots, logs,
  or backup files.
- Any Snyk API write, service-account creation, organization configuration, or
  project-setting change.

## Prohibited

- Committing Snyk tokens, API credentials, OAuth material, personal
  `config.toml`, or generated MCP secret files.
- Pasting `SNYK_TOKEN`, API tokens, full Snyk debug logs, personal financial
  snapshots, PostgreSQL rows, or ignored local JSON values into prompts,
  issues, PRs, comments, artifacts, or reports.
- Claiming Snyk passed when authentication, network access, Snyk service
  access, or project discovery was unavailable.
- Claiming the internal Snyk CLI passed on Dependabot-triggered Actions runs
  where GitHub withheld repository secrets; use the external Snyk PR check or
  an owner-approved manual rerun for evidence.
- Treating API-inferred dependency evidence as stronger than local CLI/CI scan
  evidence when the CLI can inspect the actual project.

## Reporting Template

When reporting a Snyk result, include:

- command/tool/profile used;
- authentication state without token values;
- organization/project only when already visible in the authorized Snyk output;
- manifest or package manager scanned;
- package name, installed version, vulnerable path, severity, advisory/CVE, and
  fixed version;
- whether the finding is upgradeable, patchable, ignored by policy, or requires
  manual analysis;
- compatibility risk and verification commands for any proposed upgrade;
- skipped or unavailable checks and why.

## Future Upgrade Path

1. Pin or otherwise standardize the Snyk CLI/action used by CI.
2. If structured Snyk artifacts become necessary, emit machine-readable reports
   to ignored local paths and upload only sanitized CI artifacts.
3. If API automation is needed, use a service account, explicit org/project
   scope, and environment/secret storage managed outside the repository.
4. Revisit repository-scoped MCP only if a non-secret, team-managed
   configuration becomes useful and the project is explicitly trusted.
