# 0020 Use scoped overrides instead of install-time patches

## Status

Accepted - implemented 2026-07-15

## Context

Security hardening on 2026-07-13 added blanket frontend overrides for
`brace-expansion@5.0.7` and `js-yaml@4.3.0`. Several ESLint packages still use
`minimatch@3.1.5`, which declares the CommonJS `brace-expansion@1` API and
expects `require('brace-expansion')` to return a callable function. Forcing
version 5 under those packages replaced that API with named exports.

A frontend `postinstall` script compensated by rewriting the installed
`brace-expansion` CommonJS entry in `node_modules`. That made installs depend on
an untracked derived-file mutation, assumed one package layout, and obscured
which security and compatibility constraints the repository actually owned.

The applicable advisories have patched releases on the compatible 1.x line:

- [GHSA-v6h2-p8h4-qcjw](https://github.com/advisories/GHSA-v6h2-p8h4-qcjw)
  is fixed in `brace-expansion@1.1.12`.
- [GHSA-f886-m6hf-6m8v](https://github.com/advisories/GHSA-f886-m6hf-6m8v)
  is fixed in `brace-expansion@1.1.13`.
- [GHSA-mh29-5h37-fv8m](https://github.com/advisories/GHSA-mh29-5h37-fv8m)
  is fixed in `js-yaml@4.1.1`; the current ESLint parent already declares
  `^4.1.1` and the lockfile resolves `4.3.0`.

## Decision

- Remove the frontend `postinstall` lifecycle hook and the script that edits
  dependency source files.
- Replace the blanket brace-expansion override with one scoped npm override:
  `minimatch@3.1.5 -> brace-expansion@1.1.13`.
- Allow modern `minimatch@10` to resolve its native `brace-expansion@5` line.
- Remove the redundant `js-yaml` override and keep the secure `4.3.0`
  resolution in the lockfile through its parent's compatible declared range.
- Add `npm run check:dependency-compat`. It discovers the current ESLint owners
  of minimatch 3, verifies that each resolves brace-expansion 1.1.13, and
  exercises brace matching through the actual installed modules.
- Run that assertion in the default local verifier and the hosted Code Quality
  job. A clean `npm ci` must succeed without a lifecycle script mutating
  `node_modules`.

## Consequences

- The manifest and lockfile now fully describe installed package contents.
- npm audit reports no known vulnerability in the current frontend graph, and
  legacy CommonJS consumers use the API they declared.
- The exact override remains a temporary development-tool constraint. Remove
  it and the compatibility check after all listed ESLint dependency paths move
  off minimatch 3, or after their upstream dependency declarations select a
  secure compatible brace-expansion release without repository intervention.
- Dependency updates that change the legacy owners or version must update the
  override record and compatibility assertion deliberately instead of
  restoring an install-time patch.
