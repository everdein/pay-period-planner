# 0013 Add tabular financial snapshot import/export

## Status

Superseded by
[ADR 0018](0018-use-json-as-the-sole-application-backup-format.md)

## Context

The app already had a JSON export for the saved source snapshot, but the next
production-readiness step needed a format that people and agents can inspect,
edit, and restore without reverse-engineering the JSON aggregate. The financial
workspace is still a single snapshot aggregate with optimistic version checks,
and exports/imports can contain personal financial data.

## Decision

Add CSV and XLSX export endpoints plus CSV and XLSX import endpoints under the
existing `/api/v1/financials` resource:

- `GET /api/v1/financials/export/csv`
- `GET /api/v1/financials/export/xlsx`
- `POST /api/v1/financials/import/csv`
- `POST /api/v1/financials/import/xlsx`

Both formats use the same fixed-column tabular representation. The first data
row is snapshot metadata, and each remaining row represents one source record.
Imports are full-snapshot restores, not merges. They pass through the same
service save path as `PUT /api/v1/financials`, including the imported `version`
token.

Use a small internal codec based on JDK CSV parsing and minimal XLSX Open XML
read/write support instead of adding a spreadsheet library dependency. Add
PowerShell wrappers for deterministic local export/import commands that avoid
printing financial contents.

## Consequences

- CSV and XLSX are now portable manual recovery formats for local use.
- Stale import files fail with `409 Conflict` instead of overwriting newer
  snapshots.
- XLSX import expects the first worksheet to keep the documented fixed columns;
  date values should remain ISO text.
- The implementation is intentionally format-specific and not a general Excel
  engine. If future spreadsheet needs include formulas, multi-sheet authoring,
  or richer cell formatting, revisit whether a dedicated library is worth the
  dependency.
- Export/import files remain personal financial data and must not be committed
  or stored in repository folders.
