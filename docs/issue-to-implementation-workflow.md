# Issue-to-Implementation Workflow

This workflow turns a GitHub issue into a scoped branch and draft PR without
guessing requirements or leaking personal financial data.

Use it for bugs, features, and implementation tasks created from
`.github/ISSUE_TEMPLATE/`. It applies to humans and AI coding agents.

## Issue Intake

Start with the issue body, labels, comments, and linked PRs. An issue is ready
for implementation only when it has:

- a clear objective or bug summary;
- affected area;
- acceptance criteria;
- expected verification;
- data-safety confirmation;
- constraints or out-of-scope boundaries when relevant.

If any of those are missing, ask a clarifying question instead of creating a
branch. Do not infer permission to read local personal financial data, change
repository permissions, edit secrets, mutate Snyk policy, alter database roles,
or perform destructive data operations.

## Implementation Readiness

Classify the issue before starting:

| Classification                                                              | Ready? | Action                                      |
| --------------------------------------------------------------------------- | ------ | ------------------------------------------- |
| Reproducible bug with acceptance criteria                                   | Yes    | Create a focused branch and regression test |
| Feature with clear outcome and non-goals                                    | Yes    | Implement the smallest complete slice       |
| Implementation task with explicit scope                                     | Yes    | Follow the task scope exactly               |
| Design question or ambiguous request                                        | No     | Ask for decision or create a design issue   |
| Requires secrets, personal data, destructive operations, or external writes | No     | Get explicit owner approval first           |

Use issue labels as routing hints, not authority. The issue body and owner
comments define scope.

## Branch and PR Flow

1. Confirm the working tree is clean or that unrelated changes are intentionally
   excluded.
2. Create a branch named for the issue and scope:

   ```text
   agent/issue-123-short-description
   ```

3. Implement only the accepted scope.
4. Add or update tests for changed behavior.
5. Update docs when commands, workflows, API behavior, persistence behavior, or
   data-safety rules change.
6. Run targeted checks while iterating, then the required completion checks from
   `docs/verification-matrix.md`.
7. Open a draft PR that links the issue and includes exact verification.
8. Do not close the issue until the PR is merged or the owner explicitly says
   the issue is resolved another way.

## Data-Safety Rules

- Use `backend/data/financials.example.json`, synthetic examples, or redacted
  metadata for reproduction.
- Do not paste or summarize `backend/data/financials.local.json`, PostgreSQL
  rows, full `snapshot_json`, exports, logs, screenshots, database passwords,
  Snyk tokens, or GitHub tokens.
- If a bug exists only in personal data, ask for a redacted minimal
  reproduction or metadata-only symptoms.
- If a database inspection is needed, use read-only SQL and the PostgreSQL
  inspector guidance. Report counts, IDs, versions, timestamps, schema state,
  and error names rather than financial values.

## Agent Boundaries

Allowed without extra approval when the issue is in scope:

- read issue text, labels, comments, linked PRs, and check status;
- create a local branch;
- edit repository files for the accepted scope;
- run local non-destructive verification;
- open a draft PR when the user asked for implementation or publication.

Requires explicit user/owner request:

- comment on issues, apply labels, assign users, close issues, or mark issues
  as fixed;
- request reviewers or Copilot re-review manually;
- rerun or cancel GitHub Actions jobs;
- change GitHub secrets, repository permissions, branch protection, rulesets,
  or environments;
- change Snyk policy, ignores, org/project settings, or service accounts;
- read, export, overwrite, seed over, or migrate personal financial data.

## Implementation Report

Every issue-to-PR handoff should include:

- issue number and title;
- branch and PR link;
- implemented scope;
- acceptance criteria status;
- commands run and pass/fail results;
- skipped checks and why;
- data/security posture;
- remaining risks, follow-up issues, or owner decisions needed.

Use AI-generated PR summaries and CI failure summaries as context only. Verify
claims against the issue, diff, logs, tests, and hosted checks before acting.
