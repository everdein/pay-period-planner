# 0026 Use versioned record IDs for projection roles

## Status

Accepted

## Context

The paycheck projection requires one monthly withdrawal as the rent
obligation, one asset account as the rent reserve, and one income-summary item
as the primary paycheck. Those responsibilities were previously inferred from
the display labels `Rent`, `Rent Reserve`, and `Net Income` / `Bi-Weekly`.

Display labels are user-owned data. Treating them as identity prevented normal
editing, made deletion rules surprising, and coupled product behavior to one
household's terminology. New records also use temporary browser IDs until a
whole-snapshot save assigns their persistent application IDs.

## Decision

Store three projection roles in every versioned financial snapshot:

- rent bill ID
- rent-reserve asset-account ID
- primary-paycheck income-summary-item ID

V8 persists these values in `financial_record_projection_role`, keyed by
snapshot and role. The API, JSON backup, canonical frontend draft, and
projection settings carry the same role object. Role values must reference
exactly one record in the correct collection. Selected records cannot be
removed until their role is reassigned, but all display fields remain editable.

Temporary negative IDs are preserved in frontend save requests. The aggregate
repository assigns positive IDs and remaps role references in the same save.

V8 backfills exact historical anchor labels. Legacy JSON, JSONB, and
backup input is upgraded once by compatibility normalization: matching legacy
labels are selected without being renamed, and missing responsibilities receive
zero-value default records. Once roles exist, runtime behavior never infers
identity from labels.

## Consequences

- Renaming projection inputs no longer changes calculations.
- Workspaces can use their own terminology and choose different source rows.
- Projection-role configuration is versioned, backed up, restored, audited as
  part of aggregate replacement, and isolated by workspace.
- The role table cannot use direct foreign keys to three different child-table
  types, so application validation and integration tests enforce typed
  references.
- Legacy label matching remains only at import and upgrade boundaries without roles.
