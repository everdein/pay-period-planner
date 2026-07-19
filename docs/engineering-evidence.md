# Engineering Evidence

This page records the latest qualified local evidence for Pay Period Planner.
The completion-gate baseline was refreshed on July 16, 2026, against the
complete working tree using synthetic data and isolated PostgreSQL schemas.
The hosted responsive, accessibility, PostgreSQL, CodeQL, dependency, and
security checks passed for PR 44 on July 19 after responsive table reflow was
introduced. Other local security evidence remains the July 15 point-in-time
result. This page describes what each result demonstrates and, equally
importantly, what it does not demonstrate.

## Evidence Summary

| Area                          | Result                                    | Executed scope                                                                                                                                                                      | Qualification                                                                        |
| ----------------------------- | ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| Local completion gate         | Pass                                      | Environment, spelling, public-corpus validation, dependency compatibility, TypeScript, ESLint, frontend tests and build, backend formatting/build/tests, and PostgreSQL integration | Local Windows environment; hosted Linux behavior is separate                         |
| Frontend tests                | 123 of 123 passed in 21 files             | Components, Redux/API behavior, draft editing, calculations, dates, semantic metric tones, workflow states, and error handling                                                      | jsdom/unit and integration-style tests; not browser coverage                         |
| Backend tests                 | 63 of 63 passed in 12 classes             | Controllers, security/configuration, repositories, services, validation, and calculations                                                                                           | Default suite uses mocks or local collaborators where appropriate                    |
| PostgreSQL integration        | 29 of 29 passed in 8 classes              | Flyway V1-V12, relational persistence, identity/session behavior, workspace isolation, destructive retirement, CSRF, and runtime APIs                                               | Local PostgreSQL 18.4; schemas were isolated and removed after the run               |
| Live browser workflow         | 6 of 6 passed in 3 specifications         | React, Vite proxy, Spring Boot, PostgreSQL, authentication, isolation, save/reload, conflicts, accessibility, and responsive behavior                                               | Chromium only; synthetic data; not cross-browser certification                       |
| Automated accessibility       | 2 of 2 axe scenarios passed               | Account forms, onboarding, twelve financial sections, dialog semantics, and modal focus behavior under WCAG 2.0/2.1/2.2 A/AA rules                                                  | Automated rules cannot validate screen-reader usefulness or certify WCAG conformance |
| Automated responsive behavior | 1 end-to-end scenario passed at 4 widths  | `320x800`, `390x844`, `768x1024`, and `1024x768`; overflow, control containment, responsive table reflow, withdrawal-list fit, and navigation breakpoint                            | Geometry checks do not replace manual readability, orientation, or 200% zoom review  |
| npm audits                    | Pass, 0 reported vulnerabilities          | Root and frontend lockfiles                                                                                                                                                         | Point-in-time advisory database result                                               |
| Authenticated Snyk            | Pass, no vulnerable paths in 3 projects   | Root npm, Maven backend with 76 dependencies, and frontend npm with 13 dependencies using pinned CLI `1.1306.0`                                                                     | Point-in-time dependency result; does not replace CodeQL or future scans             |
| CodeQL                        | Hosted evidence required                  | Hosted Java and JavaScript/TypeScript analysis                                                                                                                                      | No complete local equivalent; inspect uploaded alerts for each commit/PR run         |
| Dependency Review             | Hosted evidence required on pull requests | Hosted pull-request dependency diff                                                                                                                                                 | Runs only on pull requests and applies only to newly introduced dependencies         |

## Aggregate Coverage

The completion gate generated these aggregate reports and enforced every
configured minimum:

| Area     | Statements / Instructions | Branches | Functions / Methods |  Lines | Enforced gate                                          |
| -------- | ------------------------: | -------: | ------------------: | -----: | ------------------------------------------------------ |
| Frontend |                    82.76% |   75.05% |              82.33% | 82.51% | Statements 45%, branches 45%, functions 35%, lines 46% |
| Backend  |                    85.32% |   69.00% |              81.84% | 82.73% | Lines 80%                                              |

Coverage is a regression signal, not a claim that every behavior or risk is
tested. Frontend percentages come from Vitest/V8 and do not include Playwright
execution. Backend percentages come from the default JaCoCo suite. The three
PostgreSQL repository implementations excluded from default instrumentation are
exercised behaviorally by the required isolated integration suite, but those 29
tests do not increase the JaCoCo percentages above.

## Browser Evidence

The Playwright run starts real Spring Boot and Vite processes against a unique
PostgreSQL schema. Its six scenarios prove:

- creation and first save of an empty workspace
- two-account workspace isolation and session recovery
- CSRF-protected edits, save, reload persistence, and removal confirmation
- stale-draft conflict handling after a concurrent save
- axe checks for public account forms and the complete authenticated workflow
- responsive containment and navigation across the four supported widths

The schema is dropped in the wrapper's `finally` block. Screenshots and traces
are failure artifacts only unless an explicit synthetic portfolio capture is
requested.

## Accessibility Qualification

Automated axe rules and keyboard assertions passed. A manual screen-reader
journey was **not performed for this baseline**, so this report does not claim
screen-reader certification or universal WCAG conformance. Before a production
release or after an accessibility-critical interaction change, complete the
Chrome/NVDA or documented equivalent protocol in
[accessibility-verification.md](accessibility-verification.md) and record the
browser, assistive technology, tester, result, and follow-ups.

The three portfolio images were visually inspected at desktop and mobile
sizes. That inspection is publication review, not the complete orientation,
zoom, and readability protocol in
[responsive-verification.md](responsive-verification.md).

## Security Qualification

The authenticated local security run used the repository-pinned Snyk CLI
version and completed successfully. It found no vulnerable dependency paths in
the three discovered projects, and both npm audits reported zero
vulnerabilities. No token values or financial data were included in the scan
evidence.

These are point-in-time software-composition results. They do not analyze
application source like CodeQL, prove the absence of unknown vulnerabilities,
or qualify a GitHub commit by themselves. Before merge, require the hosted CI,
CodeQL, Dependency Review when applicable, and external Snyk checks to complete;
inspect alerts rather than inferring an empty alert list from a successful
upload job.

## Reproduce Locally

From the repository root:

```powershell
$env:DATABASE_USERNAME = "<local application role>"
$env:DATABASE_PASSWORD = "<local application password>"
.\scripts\verify-local.ps1
.\scripts\run-browser-checks.ps1
.\scripts\write-coverage-summary.ps1
.\scripts\run-security-checks.ps1
```

The security command additionally requires network access, the pinned Snyk CLI,
and `SNYK_TOKEN`. A missing token, unavailable service, skipped hosted step, or
failed scan is not a pass. Use only synthetic data for browser evidence and
never publish local database contents, exports, traces, or screenshots that may
contain personal financial information.

## Maintenance Rule

Refresh this page when test counts, coverage thresholds, supported browser
workflows, accessibility scope, or security gates materially change. Record
hosted pass/fail evidence in the corresponding pull request because GitHub
status is commit-specific and should not be copied here as a timeless claim.
