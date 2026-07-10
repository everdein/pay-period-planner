---
name: audit-end-to-end-docs
description: Audit end-to-end-app documentation against executable repository behavior, including package scripts, PowerShell workflows, frontend configuration, Java controllers and DTOs, Spring profiles, PostgreSQL migrations and storage, tests, and GitHub Actions. Use to find documentation drift, validate onboarding or troubleshooting instructions, review docs after code changes, or prepare documentation corrections.
---

# Audit End-to-End Documentation

1. Read `AGENTS.md` and
   [references/source-map.md](references/source-map.md).
2. Define the audit scope and inventory the affected Markdown, workflow,
   configuration, script, API, persistence, and test files. For a complete
   audit, include root and subproject READMEs, ADRs, and operational docs.
3. Extract testable claims from documentation: commands, prerequisites,
   versions, paths, ports, environment variables, profiles, API routes, schema
   objects, persistence semantics, CI jobs, security behavior, and known
   limitations.
4. Verify every claim against the highest-authority executable source in the
   source map. Run safe, non-mutating commands when static inspection cannot
   establish behavior. Do not initialize databases, edit data, call external
   services, or expose personal financial records merely to validate docs.
5. Classify discrepancies as incorrect, incomplete, ambiguous, obsolete, or
   undocumented behavior. Distinguish intentional limitations from drift.
6. Report findings ordered by user impact. Include the documentation location,
   contradicted source of truth, likely consequence, and exact correction
   direction. Identify duplicate claims that should link to one canonical
   source.
7. Update documentation only when requested. Preserve historical ADR decisions;
   supersede them with a new ADR rather than rewriting history.
8. Run `npm run spell` and validate referenced commands/paths after edits.
   Report claims that remain runtime-, credential-, database-, or hosted-only.

Never infer that a command works because it appears in another document. Never
copy personal database or JSON values into documentation, examples, or reports.
