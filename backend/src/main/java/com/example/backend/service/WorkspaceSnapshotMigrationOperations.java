package com.example.backend.service;

import com.example.backend.domain.migration.FinancialSnapshotCounts;
import com.example.backend.domain.migration.WorkspaceSnapshotMigration;
import java.util.UUID;

public interface WorkspaceSnapshotMigrationOperations {

  BackupArtifact backupLegacyJsonb();

  WorkspaceSnapshotMigration migrateJsonFile(
      byte[] sourceBytes, String expectedFingerprint, String destinationEmail, long workspaceId);

  WorkspaceSnapshotMigration migrateJsonbDocument(
      String expectedFingerprint, String destinationEmail, long workspaceId);

  WorkspaceSnapshotMigration getMigration(UUID migrationId);

  WorkspaceSnapshotMigration rollback(UUID migrationId);

  record BackupArtifact(
      byte[] bytes, String fingerprint, long version, FinancialSnapshotCounts counts) {}
}
