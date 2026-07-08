---
name: review-end-to-end-app
description: Review the end-to-end-app repository, a pull request, or a change set for bugs, regressions, security risks, data-loss risks, code smells, and missing tests. Use for full code reviews and focused frontend, backend, PostgreSQL, API, or pipeline reviews.
---

# Review End-to-End App

1. Establish scope with `git status`, the relevant diff, and nearby tests.
2. Trace affected behavior across React draft state, API DTOs, service rules,
   and the active storage adapter.
3. Prioritize:
   - silent data loss or hidden persisted records;
   - incorrect financial or date calculations;
   - last-write-wins and concurrency behavior;
   - API request and response incompatibility;
   - JSON and PostgreSQL profile divergence;
   - secret exposure and overly broad workflow permissions;
   - tests that pass without exercising the changed path.
4. Verify each issue against executable behavior or a concrete code path. Do
   not report style preferences as defects.
5. Run the narrowest relevant tests. Use `.\scripts\verify.ps1` for a
   repository-wide review and `-IncludePostgres` for persistence findings.
6. Present findings first, ordered P0 through P3, with file and line references,
   impact, and a concise remediation. Then list assumptions and test gaps.

Treat the V1 normalized tables as inactive groundwork unless code proves
otherwise. Do not infer that an authenticated Snyk scan passed from `npm audit`.
