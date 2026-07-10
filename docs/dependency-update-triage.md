# Dependency Update Triage

This repository uses Dependabot for version-update discovery and deterministic
triage packets for human or AI-assisted review. Dependency automation is
assistive; it never auto-merges updates or bypasses CI, Snyk, or human review.

## Dependabot Scope

`.github/dependabot.yml` checks these ecosystems weekly:

- root npm tooling in `/`;
- frontend npm runtime and development dependencies in `/frontend`;
- backend Maven dependencies in `/backend`;
- GitHub Actions in `/`.

Dependabot groups minor and patch updates where that reduces noise. Major
updates remain explicit compatibility work unless an owner decides otherwise.

## Local Triage Packet

Generate a dependency triage packet from the current working tree:

```powershell
.\scripts\triage-dependency-updates.ps1
```

For a hosted comparison, pass base and head refs:

```powershell
.\scripts\triage-dependency-updates.ps1 -BaseRef origin/main -HeadRef HEAD
```

The packet reports:

- dependency-related files changed;
- runtime/tooling classification;
- direct npm and Maven dependency inventory;
- verification and security checklist.

It does not query registries for latest versions. Dependabot, npm, Maven, Snyk,
and GitHub Actions provide live update/security evidence; the packet provides
repeatable triage context.

## Review Checklist

1. Identify ecosystem and manifest: root npm, frontend npm, backend Maven, or
   GitHub Actions.
2. Determine update type: patch, minor, major, security, tooling-only,
   runtime, Maven parent, plugin, or lockfile-only.
3. Read release notes for major updates, security fixes, framework updates,
   build-tool updates, and GitHub Action changes.
4. Confirm the correct lockfile or generated metadata changed.
5. Run targeted checks for the affected surface:
   - frontend dependency: `npm --prefix frontend run test`, typecheck, lint,
     and build when relevant;
   - backend dependency: backend Maven tests/build;
   - root tooling: `npm run spell`, formatting/lint-staged behavior if
     affected;
   - GitHub Actions: local equivalents plus hosted PR run.
6. Run authenticated security checks when available:

   ```powershell
   .\scripts\run-security-checks.ps1
   ```

7. Report skipped checks and why. Missing Snyk access, missing network, or
   unavailable registry evidence is not a pass.

## Boundaries

- Do not auto-merge dependency PRs.
- Do not accept major updates solely because Dependabot opened a PR.
- Do not add `.snyk` ignores, policy exceptions, repository secrets, branch
  protection changes, or GitHub permissions from dependency triage without an
  explicit owner decision.
- Do not paste Snyk tokens, GitHub tokens, full logs, database rows, ignored
  local JSON, or personal financial values into dependency reports.
- If an update touches runtime financial behavior, verify mock/synthetic data
  paths and avoid personal local data.

## Reporting Template

- PR/issue/run link
- Ecosystem and manifest
- Direct dependency and transitive path, if known
- Installed/current version and proposed version
- Update type and compatibility risk
- Security advisory or Snyk finding, if applicable
- Commands run and results
- CI/Snyk status
- Recommendation: accept, split, hold, supersede, or request owner decision
