## Summary

<!--
AI-assisted summaries are welcome, but keep them evidence-based. Describe what
changed, why it changed, and the user/developer impact. Do not include secrets,
personal financial values, raw database rows, or full local JSON snapshots.
-->

-

## Verification

<!-- List exact commands and hosted checks. Mark unavailable/skipped checks with why. -->

- [ ] `npm run spell`
- [ ] `git diff --check`
- [ ] `.\scripts\verify-local.ps1`
- [ ] PostgreSQL check, if relevant: `.\scripts\verify-local.ps1 -IncludePostgres`
- [ ] Browser check, if relevant: `.\scripts\run-browser-checks.ps1`
- [ ] Security check, if available: `.\scripts\run-security-checks.ps1`

## Data and security notes

<!-- State whether the change touches mock data, local personal financial data, credentials, Snyk, GitHub permissions, or database roles. -->

- Uses synthetic/mock financial data only:
- Touches ignored local data or personal financial data:
- Changes secrets, permissions, security policy, or dependency posture:

## Follow-up

<!-- Link issues, known limitations, ADRs, or stacked PRs. -->

-
