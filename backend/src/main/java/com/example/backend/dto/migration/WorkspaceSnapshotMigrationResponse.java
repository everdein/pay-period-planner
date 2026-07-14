package com.example.backend.dto.migration;

import com.example.backend.domain.migration.WorkspaceSnapshotMigration;
import java.time.Instant;
import java.util.UUID;

public record WorkspaceSnapshotMigrationResponse(
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
    FinancialSnapshotCountsResponse expectedCounts,
    boolean snapshotActive,
    long currentVersion,
    FinancialSnapshotCountsResponse currentCounts,
    boolean metadataMatches,
    boolean rollbackEligible,
    Instant appliedAt,
    Instant rolledBackAt) {

  public static WorkspaceSnapshotMigrationResponse from(WorkspaceSnapshotMigration migration) {
    return new WorkspaceSnapshotMigrationResponse(
        migration.id(),
        migration.status(),
        migration.sourceKind(),
        migration.sourceFingerprint(),
        migration.sourceVersion(),
        migration.destinationUserId(),
        migration.destinationEmail(),
        migration.workspaceId(),
        migration.workspaceName(),
        migration.snapshotId(),
        FinancialSnapshotCountsResponse.from(migration.expectedCounts()),
        migration.snapshotActive(),
        migration.currentVersion(),
        FinancialSnapshotCountsResponse.from(migration.currentCounts()),
        migration.metadataMatches(),
        migration.rollbackEligible(),
        migration.appliedAt(),
        migration.rolledBackAt());
  }
}
