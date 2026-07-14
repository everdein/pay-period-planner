package com.example.backend.domain.migration;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceSnapshotMigration(
    UUID id,
    String status,
    String sourceKind,
    String sourceFingerprint,
    long sourceVersion,
    long destinationUserId,
    String destinationEmail,
    long workspaceId,
    String workspaceName,
    long snapshotId,
    FinancialSnapshotCounts expectedCounts,
    boolean snapshotActive,
    long currentVersion,
    FinancialSnapshotCounts currentCounts,
    boolean metadataMatches,
    boolean rollbackEligible,
    Instant appliedAt,
    Instant rolledBackAt) {}
