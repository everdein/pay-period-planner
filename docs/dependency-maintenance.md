# Dependency and runtime maintenance

Checked against the npm registry and official release metadata on October 24, 2026.

On October 27, 2026, the paired typescript-eslint plugin and parser were updated to 8.72.1 after checking their published peer and Node requirements. The patch retains compatibility with the declared ESLint 9, TypeScript 6.0, and Node baselines.

Use Node.js **24.22.1 LTS** (selected by `.node-version`) with its bundled npm 11.19.1. Node 26.12.1 is also supported. CI reads the version file; algorithm projects additionally verify Node 26.12.1. These are patched releases; older 24.x/26.x versions are outside the declared engine range.

## Declared dependency baseline

| Package                          | Version |
| -------------------------------- | ------- |
| cspell                           | 10.4.0  |
| husky                            | 9.1.7   |
| lint-staged                      | 17.6.0  |
| prettier                         | 3.9.10  |
| @reduxjs/toolkit                 | 2.13.0  |
| react                            | 19.3.1  |
| react-dom                        | 19.3.1  |
| react-redux                      | 9.3.0   |
| @axe-core/playwright             | 4.14.0  |
| @eslint/js                       | 9.39.5  |
| @playwright/test                 | 1.64.1  |
| @testing-library/jest-dom        | 7.0.1   |
| @testing-library/react           | 16.3.3  |
| @types/node                      | 26.6.5  |
| @types/react                     | 19.3.0  |
| @types/react-dom                 | 19.3.0  |
| @typescript-eslint/eslint-plugin | 8.72.1  |
| @typescript-eslint/parser        | 8.72.1  |
| @vitejs/plugin-react             | 6.1.2   |
| @vitest/coverage-v8              | 5.0.5   |
| eslint                           | 9.39.5  |
| eslint-config-prettier           | 10.1.8  |
| eslint-plugin-jsx-a11y           | 6.10.2  |
| eslint-plugin-react              | 7.37.5  |
| eslint-plugin-react-hooks        | 7.1.1   |
| eslint-plugin-react-refresh      | 0.5.7   |
| eslint-plugin-simple-import-sort | 14.0.0  |
| globals                          | 17.13.0 |
| jsdom                            | 30.1.3  |
| typescript                       | 6.0.3   |
| vite                             | 8.3.5   |
| vitest                           | 5.0.5   |

## Compatibility constraints

ESLint remains **9.39.5** because the current React and JSX accessibility plugins declare support through ESLint 9, not 10. TypeScript remains **6.0.3** because typescript-eslint 8.72.1 declares `>=4.8.4 <6.1.0`, and TypeScript 7 changes the compiler API used by these integrations. Do not bypass peer dependencies with force or legacy-peer-deps. Revisit when the upstream plugins support the newer majors.

React/React DOM 19.3.1, Vitest/coverage 5.0.5, Playwright 1.64.1, and axe-core Playwright 4.14.0 were already current where used. Lockfiles include current compatible transitive dependencies. Existing security overrides remain scoped and covered by their compatibility checks.

## Backend

Java **25 LTS**, Spring Boot **4.1.2**, Jackson BOM **3.2.4** (including annotations 2.22), Logback **1.6.5**, PostgreSQL JDBC **42.7.14**, Log4j **2.26.1**, Tomcat **11.0.27**, Maven **3.10.0**, JaCoCo **0.8.15**, Spotless **3.10.4**, SortPom **4.0.0**, and Snyk CLI **1.1308.3**. The Spring Boot parent manages Spring Framework, Flyway and remaining platform dependencies.

[ADR 0031](adr/0031-adopt-java-25-lts.md) records the Java baseline change. PostgreSQL remains on the stable 18 release line; the CI service image tracks its current patch. This update does not migrate or modify personal databases. No schema or API contract changes are required.

Run `scripts/verify-local.ps1` with the documented synthetic test database configuration, then the live browser suite. Hosted security and PostgreSQL gates remain required. GitHub actions stay pinned to immutable release commits.

## Update procedure

Check current stable releases and peer/runtime requirements, update manifests and lockfiles together, run a clean install, audit dependencies, and run the project verification gate plus applicable browser checks. Keep historical ADRs unchanged and update current requirements when the runtime baseline changes. Record any intentional version constraint here rather than reporting an incompatible major as installed.
