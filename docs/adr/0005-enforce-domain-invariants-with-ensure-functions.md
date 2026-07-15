# 0005 Enforce Domain Invariants with Ensure Functions

## Status

Superseded by ADR 0026

## Context

ADR 0026 replaces name-based ensure functions with typed, versioned projection
role IDs. This record remains as historical context for the compatibility
normalizer used by legacy input without roles.

The financial snapshot aggregate requires certain critical entities to always be present for the projection engine and UI to function correctly:

- A "Rent" withdrawal bill (for rent reserve tracking and projections)
- A "Rent Reserve" asset account within Cash & Savings (for rent savings calculations)
- A "Net Income / Bi-Weekly" income summary item (for primary paycheck metrics)

Historically, these entities were optional in the data model, leading to:

1. Defensive null-checks and default values scattered throughout business logic
2. UI rendering gaps when these entities were missing
3. Projection calculations that failed silently or produced incorrect results
4. No clear contract for "complete" financial data

Additionally, legacy data may have different naming conventions (e.g., "Rent Payment" instead of "Rent", "Emergency Fund" instead of "Rent Reserve"). As the domain evolved, these entries needed to be normalized.

## Decision

Introduce domain invariant functions (`ensureRentWithdrawal`, `ensureRentReserveAccount`, `ensurePrimaryPaycheck`) that enforce the presence and correctness of critical entities. These functions:

1. **Check for presence**: Verify the invariant entity exists (e.g., a bill named "Rent")
2. **Normalize legacy data**: If a legacy entry exists (e.g., a bill with "rent" in the name), rename it to the canonical name
3. **Create if missing**: If neither canonical nor legacy entry exists, create a system-managed entry with a negative synthetic ID (e.g., `-100000`)
4. **Preserve editing**: User-created entries with positive IDs are never auto-created; only system entries use negative IDs

These functions are called:

- In the frontend during snapshot load (draft initialization)
- In the backend during snapshot validation (before persistence)
- Explicitly in the projection engine before calculations

This ensures the financial snapshot always satisfies domain constraints without requiring defensive checks in business logic.

## Rationale

**Why not nullable fields or Optional?**
Nullable fields push the burden to every caller. This pattern centralizes the constraint to one place and guarantees callers can rely on the entity's presence.

**Why support legacy data migration?**
Many users may have "Rent Payment" or "Home Payment" as their bill name. Auto-normalizing preserves their data while establishing the domain language. This is a soft migration—existing data is updated, but users are not blocked.

**Why negative synthetic IDs?**
Synthetic IDs distinguish system-managed entries from user-created ones. Negative IDs are unambiguous and reserved for system use. When the user explicitly creates a "Rent" bill (positive ID), the system-created one (negative ID) can be quietly replaced or merged.

**Why in both frontend and backend?**

- Frontend: Ensures the draft state passed to the projection engine is always valid
- Backend: Ensures persisted data always satisfies constraints, even if the frontend is bypassed

## Consequences

**Positive:**

- Projection engine and dependent business logic can assume invariants are satisfied
- No defensive null-checks in critical calculations
- Clear domain contract: "A valid snapshot has rent, rent reserve, and primary paycheck"
- Backward compatible with legacy data naming conventions
- User-created entries (positive IDs) are never overwritten

**Negative:**

- Adds implicit behavior: data is auto-normalized on load
- Developers must understand that negative IDs are reserved for system use
- If a user manually deletes a critical entity, it will be silently recreated on next save (user may be confused)
- Extra function calls during snapshot load/validation (negligible performance impact)

## Notes

Future enhancements could include:

- **Audit trail**: Log when legacy entries are normalized (helps users understand auto-corrections)
- **User notification**: Alert users when system-created entries are added (improves transparency)
- **Configurable invariants**: Allow domain rules to be extended without code changes
- **Data migration CLI**: One-time migration script for bulk data normalization before production deployment
