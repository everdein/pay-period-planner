# CI and Snyk Triage Guide

## Current Job Map

| Job                  | Local equivalent                               | Common failure ownership                   |
| -------------------- | ---------------------------------------------- | ------------------------------------------ |
| Code Coverage        | `npm --prefix frontend run test -- --coverage` | Tests, coverage thresholds, runner         |
| Code Quality         | `npm --prefix frontend run lint`               | Frontend code or ESLint configuration      |
| Spell Check          | `npm run spell`                                | Documentation/code spelling or dictionary  |
| Type Check           | `npm --prefix frontend run type-check`         | TypeScript source/configuration            |
| Build & Test Backend | Backend formatting and `mvnw clean verify`     | Java, tests, formatting, dependencies      |
| Build Frontend       | `npm --prefix frontend run build`              | TypeScript, Vite, dependencies             |
| Scans                | `scripts/run-security-checks.ps1`              | Vulnerability, auth, tooling, service      |
| CodeQL               | Hosted only                                    | Source, build, permissions, GitHub service |
| Dependency Review    | Hosted only                                    | Lockfile, advisory, dependency scope       |
| Deploy               | Manual placeholder                             | Workflow configuration only                |

`build-backend` and `build-frontend` depend on the four frontend quality jobs.
`scans` depends on both builds. A skipped downstream job usually reflects an
upstream failure rather than a second defect.

## Failure Classification

- **Code/test:** Assertion, compiler, lint, formatting, build, or coverage
  failure reproducible at the same commit.
- **Runner/tooling:** Disk, process spawn, image, cache corruption, tool
  installation, or incompatible hosted runtime.
- **Workflow/configuration:** Invalid YAML, wrong path, incorrect cache key,
  missing job dependency, event condition, or unpinned command behavior.
- **Permissions/secrets:** Denied API operation, unavailable fork secret,
  missing `SNYK_TOKEN`, or insufficient workflow permission.
- **Dependency/security:** Verified vulnerable installed version or resolution
  conflict in a committed manifest/lockfile.
- **External service:** Snyk/GitHub outage, rate limit, registry failure, or
  transient network error.

Retry only runner or external-service failures with evidence of transience.
Never use reruns to hide a deterministic failure.

## Snyk Decision Path

1. Confirm the Snyk CLI version matches `.snyk-cli-version`, the scan
   authenticated, and it discovered the intended root npm, frontend npm, and
   Maven projects.
2. Separate findings by manifest and deduplicate the same vulnerable package
   reached through multiple paths.
3. Prefer updating a direct dependency to the nearest compatible fixed version.
4. For transitive findings, first upgrade the owning direct dependency. Use an
   override only when the ecosystem supports it and tests demonstrate
   compatibility.
5. Identify major-version upgrades and behavioral migration work before
   proposing them as automatic fixes.
6. If no fix exists, document exposure, reachable usage, compensating controls,
   and an explicit risk-acceptance owner; do not silently ignore the finding.
7. Regenerate and commit the corresponding lockfile with its package manager.
8. Run local verification plus the authenticated hosted scan.

`npm audit` and Snyk have different advisory sources, project discovery, and
reachability context. One cannot stand in for the other.

## GitHub Security Check Decision Path

For CodeQL, identify the language category, confirm initialization and the Java
manual build completed, then inspect the uploaded alert in GitHub code
scanning. A successful workflow only confirms that analysis results were
uploaded. For Dependency Review, confirm the pull request changes a supported
manifest or lockfile, then record the dependency scope, advisory, severity,
introduced version, and first patched version. The repository blocks new high-
or critical-severity findings in runtime, development, and unknown scopes.

Neither check has a complete local equivalent. Use local builds, tests, audits,
and authenticated Snyk scans as supporting evidence, then verify the hosted
rerun at the same commit.

## Reporting Template

- Run, attempt, SHA, job, and step
- Root-cause category
- First actionable error and supporting log context
- Local reproduction result
- Snyk package/path/fixed version when applicable
- Proposed or applied change and compatibility risk
- Local checks passed, failed, or skipped
- Hosted checks still required

Use the hosted CI failure summary packet, when present, as orientation only. It
lists run metadata and failed job links, but it does not replace inspecting the
smallest relevant log window around the first actionable failure. Do not paste
full logs, tokens, Snyk token values, local financial data, or database rows
into AI prompts, PR comments, or issue bodies.
