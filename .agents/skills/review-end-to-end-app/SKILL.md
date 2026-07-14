---
name: review-end-to-end-app
description: Review the end-to-end-app repository, pull requests, or change sets using a consistent frontend, backend/API, PostgreSQL, CI/security, accessibility, and test-coverage checklist. Use for findings-first code reviews, regression and data-loss analysis, security review, architecture review, or validation of changes before merge.
---

# Review End-to-End App

1. Read `AGENTS.md` and
   [references/review-checklist.md](references/review-checklist.md).
2. Establish the review base and scope with `git status`, the relevant diff,
   changed files, commits when applicable, and nearby tests. Do not review
   unrelated pre-existing worktree changes.
3. Trace every changed behavior across the affected boundaries: React draft
   state, API client, DTOs, service rules, PostgreSQL storage, legacy migration
   inputs, and CI when applicable.
4. Apply every relevant checklist section. For a full review, cover frontend,
   backend/API, PostgreSQL, CI/security, accessibility, and test coverage;
   state which sections are not applicable.
5. Verify each finding against executable behavior or a concrete reachable
   code path. Do not report style preferences, speculative risks without a
   trigger, or issues outside the reviewed change.
6. Run the narrowest relevant checks. Use `.\scripts\verify-local.ps1` for a
   repository-wide review; it includes the isolated PostgreSQL integration
   suite.
7. Present findings first, ordered P0 through P3. Include one precise file/line
   reference, trigger, impact, evidence, and concise remediation per finding.
   Then report assumptions, skipped checks, and residual risk. If there are no
   findings, say so explicitly.

Treat the V1 normalized tables as inactive historical groundwork; ADR 0009 keeps
them out of the runtime relational adapter path. Do not infer that an empty V1
table means PostgreSQL persistence is broken. Treat the V3/V4/V6/V7
`financial_record_*` tables as the active PostgreSQL runtime path and V2 JSONB
as a legacy migration source. Do not infer that an authenticated Snyk scan
passed from `npm audit`.
