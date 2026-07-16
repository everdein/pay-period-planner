# GitHub and Codex Configuration

<!-- cspell:ignore everdein -->

## Purpose

This guide records the GitHub and Codex settings that make repository work
reliable without granting unnecessarily broad access. Repository settings are
still external state; verify them again before relying on this audit after a
workflow, plan, integration, or ownership change.

## Current Repository Baseline

The configuration audit on July 13, 2026 confirmed:

| Setting                               | Current state                |
| ------------------------------------- | ---------------------------- |
| Auto-merge                            | Enabled                      |
| Merge method                          | Squash only                  |
| Automatic deletion of merged branches | Enabled                      |
| Issues                                | Enabled                      |
| Projects and wiki                     | Disabled                     |
| Dependabot security updates           | Enabled                      |
| Secret scanning and push protection   | Enabled                      |
| Main ruleset                          | Active                       |
| Ruleset bypass actors                 | None                         |
| Review-thread resolution              | Required                     |
| Required checks                       | Strict/current-branch checks |

The `Protect main` ruleset requires spelling, code quality, type checking,
frontend coverage, frontend/backend builds, coverage summary, CI scans,
dependency review, and Java plus JavaScript/TypeScript CodeQL analysis. Do not
weaken this ruleset to make an agent workflow faster.

GitHub can automatically remove a merged topic branch, although another rule
can still prevent deletion. See GitHub's
[automatic branch deletion guide](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/configuring-pull-request-merges/managing-the-automatic-deletion-of-branches).

## Recommended Changes

### 1. Give the Codex GitHub connector pull-request write access

The GitHub connector can read this repository, but a draft pull-request
creation attempt returned `403 Resource not accessible by integration`. The
authenticated GitHub CLI successfully created the same pull request, so this is
a connector installation-token permission gap rather than a repository, branch,
or user permission problem.

In Codex connector/plugin settings and GitHub's installed-app settings:

1. Confirm `everdein/pay-period-planner` is included in the app's repository access.
2. Review or reauthorize the connector if it offers **Pull requests: Read and
   write**.
3. Keep **Contents** read-only for the connector when local Git plus
   authenticated `git push` remains the publication path.
4. Keep repository administration, secrets, workflow mutation, branch deletion,
   and merge permissions disabled unless a specific workflow requires them.

A GitHub App can act only within the intersection of the user's access and the
permissions requested by the app. If the installed app does not request
pull-request write permission, the repository owner cannot add that permission
from repository settings; continue using authenticated `gh pr create` and
report the connector limitation to its provider. GitHub documents the model in
[Authorizing GitHub Apps](https://docs.github.com/en/apps/using-github-apps/authorizing-github-apps)
and explains how to inspect repository access in
[Reviewing installed GitHub Apps](https://docs.github.com/en/apps/using-github-apps/reviewing-and-modifying-installed-github-apps).

### 2. Always suggest updating pull-request branches

`allow_update_branch` is currently disabled. Consider enabling:

```text
Repository Settings > General > Pull Requests
> Always suggest updating pull request branches
```

This keeps an explicit **Update branch** action available when `main` advances
while a pull request is open. It complements the strict up-to-date required
checks without relaxing them. GitHub documents the behavior in
[Managing suggestions to update pull-request branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/configuring-pull-request-merges/managing-suggestions-to-update-pull-request-branches).

### 3. Consider two optional Secret Protection features

Secret scanning and push protection are already enabled. If the repository's
GitHub plan exposes these controls, consider enabling:

- **Non-provider patterns**, which expand detection to generic credentials such
  as private keys and connection strings.
- **Validity checks**, which help prioritize supported provider-token alerts by
  checking whether a detected credential is still active.

Validity checks may contact the credential issuer, and neither feature replaces
immediate revocation of an exposed credential. Review GitHub's
[non-provider pattern guide](https://docs.github.com/en/code-security/secret-scanning/using-advanced-secret-scanning-and-push-protection-features/non-provider-patterns/enabling-secret-scanning-for-non-provider-patterns)
and
[validity-check guide](https://docs.github.com/en/enterprise-cloud@latest/code-security/secret-scanning/enabling-secret-scanning-features/enabling-validity-checks-for-your-repository)
before enabling them.

## Codex Command Approvals

Persistent approvals can remove repeated prompts for narrow, routine commands.
Reasonable read-only prefixes include:

```text
gh auth status
gh pr view
gh pr checks
git fetch
```

`git push` and `gh pr create` are useful publication approvals only when the
user wants that external write behavior. Do not approve broad `gh api`, shell,
PowerShell, merge, branch-deletion, repository-administration, or secret-setting
prefixes. Those commands can perform actions far beyond routine inspection.

An in-sandbox `gh auth status` may report an invalid token when GitHub network
access is blocked. Retry the same command with approved network access before
refreshing credentials. A successful external check means the keyring token is
healthy; signing out of Windows does not by itself require a new token.

## Pull-Request Lifecycle

Repository publication defaults to a draft pull request. The Copilot review
workflow intentionally skips drafts and runs when the pull request is marked
ready for review. Marking ready, requesting reviewers, enabling auto-merge,
merging, and deleting an unmerged branch remain explicit external actions.

After a squash merge, fetch/prune and return the local checkout to updated
`main` before creating the next `agent/...` branch. Automatic remote branch
deletion handles the normal hosted cleanup path.

## Audit Commands

These commands read settings without exposing tokens or secrets:

```powershell
gh auth status
gh api repos/everdein/pay-period-planner `
  --jq '{allow_auto_merge,delete_branch_on_merge,allow_squash_merge,allow_update_branch,security_and_analysis}'
gh api repos/everdein/pay-period-planner/rulesets
gh pr checks <number> --repo everdein/pay-period-planner
```

Do not print secret values, authorization headers, repository secrets, or
personal financial data while diagnosing integration access.
