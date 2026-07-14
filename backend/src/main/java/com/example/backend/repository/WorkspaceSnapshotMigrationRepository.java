package com.example.backend.repository;

import com.example.backend.domain.financials.FinancialAuditEvent;
import com.example.backend.domain.migration.FinancialSnapshotCounts;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceSnapshotMigrationRepository {

  Optional<LegacyJsonbSnapshot> findLegacyJsonbSnapshot();

  Optional<DestinationWorkspace> findOwnerDestination(String email, long workspaceId);

  void attachSourceDocument(long snapshotId, Long sourceDocumentId);

  void saveAuditEvents(long snapshotId, List<FinancialAuditEvent> auditEvents);

  SnapshotMetadata snapshotMetadata(long snapshotId);

  void createMigration(MigrationRecord migration);

  Optional<MigrationRecord> findMigration(UUID migrationId);

  boolean markRolledBack(UUID migrationId, Instant rolledBackAt);

  record LegacyJsonbSnapshot(long documentId, long version, String snapshotJson) {}

  record DestinationWorkspace(long userId, String email, long workspaceId, String workspaceName) {}

  record SnapshotMetadata(boolean active, long version, FinancialSnapshotCounts counts) {}

  record MigrationRecord(
      UUID id,
      String sourceKind,
      String sourceFingerprint,
      long sourceVersion,
      Long sourceDocumentId,
      long destinationUserId,
      String destinationEmail,
      long workspaceId,
      String workspaceName,
      long snapshotId,
      FinancialSnapshotCounts expectedCounts,
      String status,
      Instant appliedAt,
      Instant rolledBackAt) {}
}
