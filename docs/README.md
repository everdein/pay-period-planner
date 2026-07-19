# Documentation Guide

This directory separates the portfolio narrative, current architecture,
historical decisions, operational runbooks, and repository automation. Use the
canonical owner below instead of copying the same claim into several files.

## Recommended Reading Order

1. [Root README](../README.md) - product problem, user value, engineering story,
   quick start, and current boundaries.
2. [Portfolio case study](portfolio-case-study.md) - synthetic walkthrough,
   architecture summary, and JSON-to-PostgreSQL STAR narrative.
3. [Architecture map](architecture-map.md) - runtime boundaries, data flow,
   persistence, verification, and change routing.
4. [Architecture decisions](adr/README.md) - decisions and supersession history.
5. [Engineering evidence](engineering-evidence.md) - current test, coverage,
   browser, accessibility, and security results with qualifications.
6. [Known limitations](known-limitations.md) - accepted gaps, mitigations, and
   revisit triggers.

## Canonical Ownership

| Question                                                | Canonical source                                                    |
| ------------------------------------------------------- | ------------------------------------------------------------------- |
| What problem does the product solve?                    | [Root README](../README.md)                                         |
| What does the product look like and how did it evolve?  | [Portfolio case study](portfolio-case-study.md)                     |
| How do runtime components and requests fit together?    | [Architecture map](architecture-map.md)                             |
| Why was an architectural tradeoff chosen?               | [ADR index](adr/README.md) and the owning ADR                       |
| What do domain terms mean?                              | [Domain glossary](domain-glossary.md)                               |
| What is the current HTTP contract?                      | [API contract](api-contract.md)                                     |
| How is data stored, migrated, backed up, and inspected? | [Database storage guide](database-storage-guide.md)                 |
| Which checks are required for a change?                 | [Verification matrix](verification-matrix.md)                       |
| What evidence currently passes, and with what caveats?  | [Engineering evidence](engineering-evidence.md)                     |
| What is intentionally incomplete?                       | [Known limitations](known-limitations.md)                           |
| How are logs, metrics, and request IDs handled?         | [Observability guide](observability-guide.md)                       |
| What provider research and deployment decision remain?  | [Deployment provider assessment](deployment-provider-assessment.md) |
| What remains on the product and deployment path?        | [Production-readiness roadmap](production-readiness-roadmap.md)     |

The root README intentionally does not duplicate detailed API tables, database
commands, coverage thresholds, or troubleshooting branches. Those details live
with the owner above.

## Public Portfolio Corpus

[`public-corpus.json`](public-corpus.json) is the exact, machine-readable
allowlist for future portfolio search, retrieval, or chatbot ingestion. Its
default policy is deny: a repository file is excluded unless its path appears
as an entry.

The allowlist contains four evidence classes:

- `document` - approved product, architecture, contract, evidence, limitation,
  roadmap, and ADR narratives.
- `source` - selected frontend, backend, and migration code that demonstrates
  the documented boundaries.
- `test` - representative unit, integration, PostgreSQL, browser,
  accessibility, and responsive tests.
- `workflow` - selected local and hosted verification/security automation.

Validate it from the repository root:

```powershell
.\scripts\check-public-corpus.ps1
```

The validator rejects duplicate, absolute, wildcard, ignored, missing,
generated, lockfile, environment, local-data, and traversal paths. It also
checks local links in approved Markdown documents. CI runs the same validation.

The manifest approves source context, not unrestricted repository access. A
future ingestion workflow should read only the listed files, record the commit
SHA, chunk source without changing it, and retain repository/file/line metadata
for every answer citation.

## Material Outside the Public Corpus

These files remain useful in the repository but are deliberately excluded from
portfolio ingestion unless reviewed and added by exact path:

| Material                                                                       | Why it is excluded by default                                                                                            |
| ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------ |
| `backend/README.md`, `frontend/README.md`                                      | Detailed workstation and subproject operation; the public narrative links to canonical architecture and evidence instead |
| Troubleshooting and local database runbooks                                    | May describe machine-specific or credentialed operational procedures                                                     |
| Automation operations and dependency-triage guides                             | Contributor automation rather than application architecture evidence                                                     |
| `.agents/`, `.codex/`, and `AGENTS.md`                                         | Agent policy and repository instructions, not portfolio content                                                          |
| Dependency locks, generated coverage/build output, reports, and caches         | Large or generated context with poor explanatory value                                                                   |
| Local JSON, PostgreSQL contents, exports, logs, traces, and ad hoc screenshots | May contain personal financial data                                                                                      |
| Environment files, tokens, credentials, and private configuration              | Secrets or deployment-specific state                                                                                     |

The committed screenshots under `docs/images/portfolio/` are intentionally not
text-ingestion entries. Their synthetic provenance and meaning are documented
in the case study, which is in the corpus.

## Operational and Contributor References

- [Accessibility verification](accessibility-verification.md)
- [Responsive verification](responsive-verification.md)
- [Troubleshooting decision tree](troubleshooting-decision-tree.md)
- [Automation and agent operations](automation-operations.md)
- [Dependency update triage](dependency-update-triage.md)
- [Snyk MCP/API assessment](snyk-integration-assessment.md)
- [Deployment provider assessment](deployment-provider-assessment.md)

These documents are maintained and link-checked, but they are not automatically
part of the public corpus.

## ADR History

ADRs are historical records. Do not rewrite an old decision to match current
code. Update its status or supersession reference when necessary, add a new ADR
for a new architectural decision, and use the architecture map for the current
runtime view. Default public retrieval includes the ADR index plus accepted or
partially surviving decisions. Fully superseded records remain available
through the index when historical context is specifically needed.

## Maintenance

When behavior changes:

1. Update the executable source and focused tests.
2. Update the canonical document that owns the claim.
3. Add or supersede an ADR when the decision changes.
4. Update `public-corpus.json` only after reviewing a new file for public data,
   relevance, and citation value.
5. Run the corpus validator, spell check, documentation-drift check, and the
   verification required by the changed surface.

Absence from the manifest is not a defect by itself. The corpus is intentionally
smaller than the repository.
