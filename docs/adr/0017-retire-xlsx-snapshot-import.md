# 0017 Retire XLSX snapshot import

## Status

Superseded by
[ADR 0018](0018-use-json-as-the-sole-application-backup-format.md)

## Context

ADR 0013 introduced a small internal Open XML reader so a saved financial
snapshot could be restored from XLSX without adding a spreadsheet dependency.
The reader expanded every ZIP entry into memory before selecting worksheet
content. The compressed HTTP request limit therefore did not bound entry count
or cumulative decompressed size, leaving a disproportionate denial-of-service
risk for a pre-production convenience feature.

CSV already preserves the same fixed-column snapshot representation and uses
the same optimistic version check. XLSX export remains useful for viewing and
does not require the server to decompress untrusted input.

## Decision

Retire `POST /api/v1/financials/import/xlsx` and remove its controller, service,
frontend client, PowerShell option, observability classification, and Open XML
decoding code. Keep CSV as the sole tabular restore format. Keep XLSX export as
an output-only convenience format.

Do not reintroduce workbook upload support unless a future product requirement
justifies a proven parser with strict compressed and decompressed byte, entry,
worksheet, row, cell, and XML-processing limits.

## Consequences

- The backend no longer decompresses user-supplied XLSX archives.
- The custom tabular codec is smaller and retains only CSV parsing plus CSV and
  XLSX generation.
- Users must export or convert a trusted workbook to the documented CSV format
  before restoring it.
- Retiring the pre-production endpoint is a deliberate V1 contract reduction;
  no migration or persisted-data change is required.
