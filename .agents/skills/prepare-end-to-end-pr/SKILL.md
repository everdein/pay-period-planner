---
name: prepare-end-to-end-pr
description: Prepare end-to-end-app changes for review by scoping the diff, checking sensitive financial data, updating required documentation, running relevant verification, creating an intentional commit, pushing a topic branch, and opening a draft pull request. Use when work is ready for commit, release handoff, publication, or a draft PR; perform external Git and GitHub mutations only when the user requests them.
---

# Prepare End-to-End Pull Request

1. Read `AGENTS.md` and
   [references/pr-checklist.md](references/pr-checklist.md).
2. Inspect `git status`, branch, remotes, staged and unstaged diffs, untracked
   files, and commits relative to the intended base. Preserve unrelated user
   changes and never stage the whole worktree blindly.
3. Review the final change as one coherent unit. Resolve accidental generated
   artifacts, debug logs, stale comments, and incomplete TODOs. Check that no
   local financial JSON, database export, credentials, tokens, or personal
   values are present in tracked or staged content.
4. Update owning documentation and add an ADR only for a new or superseding
   architectural decision. Do not rewrite accepted ADR history.
5. Run focused tests while preparing, then `.\scripts\verify-local.ps1`. Add
   `-IncludePostgres` for persistence changes. Run
   `.\scripts\run-security-checks.ps1` only when authenticated tooling and
   network access are available. Record every skipped check.
6. Before committing, inspect the exact staged diff and run `git diff --check`.
   Write an imperative commit subject that describes the completed outcome.
   Commit only when the user requested a commit.
7. Push only the intended topic branch when the user requested publication.
   Never force-push, rewrite published history, or push directly to `main`
   without explicit authorization.
8. Search for a repository pull-request template before drafting the body. Use
   the GitHub connector to open a draft PR when available. Include summary,
   rationale, test evidence, data/migration impact, security status,
   screenshots when UI behavior changed, and known risks. Create the PR only
   when requested.
9. Inspect the resulting draft PR and initial checks. Report commit SHA, branch,
   PR link, checks run/skipped, documentation changes, and residual risk.

Do not claim hosted CI or Snyk passed before the corresponding authenticated
checks complete. Keep the PR draft until required checks and review are ready.
