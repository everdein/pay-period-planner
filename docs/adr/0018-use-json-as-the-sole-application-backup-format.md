# 0018 Use JSON as the sole application backup format

## Status

Accepted - implemented 2026-07-15

## Context

The application exposed one source-shaped JSON export, CSV import/export, and
XLSX export. The tabular formats duplicated the same aggregate contract and
kept a custom CSV/Open XML codec, format-specific endpoints, client helpers,
tests, metrics, scripts, and documentation alive even though the product does
not provide a demonstrated spreadsheet-editing workflow.

The old CSV restore also used the backup's embedded snapshot version as the
target concurrency token. That prevented an older backup from restoring after
later writes, which is the main reason to retain a backup, while still failing
to state explicitly which current target version the operator intended to
replace.

## Decision

Use the versioned JSON backup envelope as the sole application-level backup and
restore artifact.

- Keep `GET /api/v1/financials/export` as the authenticated, workspace-scoped
  JSON backup download.
- Add `POST /api/v1/financials/restore?expectedVersion=<current-version>` and
  accept the exported JSON envelope unchanged as its request body.
- Treat `snapshot.version` inside the backup as source metadata. Use the
  separately supplied `expectedVersion` as the optimistic concurrency token
  for the target workspace, then persist the restored aggregate at the next
  target version.
- Validate the backup format identifier and complete nested snapshot before
  replacement. Route restore through the same aggregate validation,
  normalization, optimistic replacement, workspace authorization, CSRF, and
  audit path as an ordinary full-snapshot save.
- Retire CSV and XLSX export, CSV import, all unsupported import routes, the
  custom tabular codec, format-specific DTOs and frontend helpers, and the raw
  frontend POST transport that no supported workflow uses.
- Keep database-native backup and the explicit legacy JSON/JSONB migration
  workflow separate. An application snapshot backup does not contain complete
  relational audit history and is not a PostgreSQL disaster-recovery artifact.

This ADR supersedes ADR 0013 and ADR 0017. Their implementation history remains
useful evidence of why the simpler recovery boundary was selected.

## Consequences

- An older JSON backup can deliberately replace a newer workspace only when
  the operator supplies the workspace's current version. A concurrent write
  still produces `409 Conflict` and leaves the newer state intact.
- Backup and restore now share one schema, one validation path, one format
  identifier, and one set of data-safety rules.
- Removing tabular behavior reduces custom parsing and Open XML code without
  removing any workflow used by the browser product.
- Spreadsheet editing is unsupported. Adding it later requires demonstrated
  product need, a new format/concurrency decision, and maintained round-trip
  tests.
- JSON backups remain personal financial data and must stay outside the
  repository unless they contain explicitly synthetic data.
