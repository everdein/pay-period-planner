package com.example.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.backend.domain.migration.FinancialSnapshotCounts;
import com.example.backend.domain.migration.WorkspaceSnapshotMigration;
import com.example.backend.dto.migration.WorkspaceSnapshotMigrationResponse;
import com.example.backend.service.WorkspaceMigrationConflictException;
import com.example.backend.service.WorkspaceMigrationNotFoundException;
import com.example.backend.service.WorkspaceMigrationRequestException;
import com.example.backend.service.WorkspaceSnapshotMigrationOperations;
import com.example.backend.service.WorkspaceSnapshotMigrationOperations.BackupArtifact;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class WorkspaceSnapshotMigrationControllerTests {

  private StubMigrationOperations service;
  private WorkspaceSnapshotMigrationController controller;

  @BeforeEach
  void setUp() {
    service = new StubMigrationOperations();
    controller = new WorkspaceSnapshotMigrationController(service);
  }

  @Test
  void exportsNoStoreJsonbBackupWithFingerprintMetadata() {
    byte[] bytes = "{\"version\":7}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    FinancialSnapshotCounts counts = new FinancialSnapshotCounts(1, 2, 3, 4, 5, 6, 7, 8);
    service.backup = new BackupArtifact(bytes, "a".repeat(64), 7, counts);

    ResponseEntity<byte[]> response = controller.backupLegacyJsonb();

    assertThat(response.getBody()).isEqualTo(bytes);
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .contains("legacy-financial-snapshot.json");
    assertThat(
            response.getHeaders().getFirst(WorkspaceSnapshotMigrationController.FINGERPRINT_HEADER))
        .isEqualTo("a".repeat(64));
    assertThat(response.getHeaders().getFirst(WorkspaceSnapshotMigrationController.VERSION_HEADER))
        .isEqualTo("7");
  }

  @Test
  void appliesReadsAndRollsBackConfirmedMigrations() {
    UUID migrationId = UUID.randomUUID();
    WorkspaceSnapshotMigration migration = migration(migrationId, "applied", true, true);
    WorkspaceSnapshotMigration rolledBack = migration(migrationId, "rolled_back", false, false);
    byte[] source = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    service.migration = migration;
    service.rolledBack = rolledBack;

    WorkspaceSnapshotMigrationResponse jsonResponse =
        controller.migrateJsonFile(
            WorkspaceSnapshotMigrationController.APPLY_CONFIRMATION,
            "b".repeat(64),
            "owner@example.com",
            41,
            source);
    WorkspaceSnapshotMigrationResponse jsonbResponse =
        controller.migrateJsonbDocument(
            WorkspaceSnapshotMigrationController.APPLY_CONFIRMATION,
            "b".repeat(64),
            "owner@example.com",
            41);
    WorkspaceSnapshotMigrationResponse recovered = controller.getMigration(migrationId);
    WorkspaceSnapshotMigrationResponse rollback =
        controller.rollback(
            migrationId, WorkspaceSnapshotMigrationController.ROLLBACK_CONFIRMATION);

    assertThat(jsonResponse.id()).isEqualTo(migrationId);
    assertThat(jsonResponse.currentCounts().auditEvents()).isEqualTo(8);
    assertThat(jsonbResponse.metadataMatches()).isTrue();
    assertThat(recovered.rollbackEligible()).isTrue();
    assertThat(rollback.status()).isEqualTo("rolled_back");
  }

  @Test
  void requiresExactApplyAndRollbackConfirmations() {
    UUID migrationId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                controller.migrateJsonFile(
                    "apply", "c".repeat(64), "owner@example.com", 41, new byte[] {1}))
        .isInstanceOf(WorkspaceMigrationRequestException.class)
        .hasMessage("X-Confirm-Financial-Migration must be exactly APPLY");
    assertThatThrownBy(() -> controller.rollback(migrationId, "rollback"))
        .isInstanceOf(WorkspaceMigrationRequestException.class)
        .hasMessage("X-Confirm-Financial-Migration must be exactly ROLLBACK");
  }

  @Test
  void mapsMigrationFailuresToProblemDetails() {
    ApiExceptionHandler handler = new ApiExceptionHandler();

    ProblemDetail request =
        handler.handleWorkspaceMigrationRequest(new WorkspaceMigrationRequestException("bad"));
    ProblemDetail conflict =
        handler.handleWorkspaceMigrationConflict(new WorkspaceMigrationConflictException("busy"));
    ProblemDetail notFound =
        handler.handleWorkspaceMigrationNotFound(
            new WorkspaceMigrationNotFoundException("missing"));

    assertThat(request.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(conflict.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(notFound.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
  }

  private WorkspaceSnapshotMigration migration(
      UUID id, String status, boolean active, boolean rollbackEligible) {
    FinancialSnapshotCounts counts = new FinancialSnapshotCounts(1, 2, 3, 4, 5, 6, 7, 8);
    Instant appliedAt = Instant.parse("2026-07-14T18:30:00Z");
    return new WorkspaceSnapshotMigration(
        id,
        status,
        "json_file",
        "b".repeat(64),
        7,
        17,
        "owner@example.com",
        41,
        "Personal",
        91,
        counts,
        active,
        7,
        counts,
        true,
        rollbackEligible,
        appliedAt,
        "rolled_back".equals(status) ? appliedAt.plusSeconds(60) : null);
  }

  private static final class StubMigrationOperations
      implements WorkspaceSnapshotMigrationOperations {

    private BackupArtifact backup;
    private WorkspaceSnapshotMigration migration;
    private WorkspaceSnapshotMigration rolledBack;

    @Override
    public BackupArtifact backupLegacyJsonb() {
      return backup;
    }

    @Override
    public WorkspaceSnapshotMigration migrateJsonFile(
        byte[] sourceBytes, String expectedFingerprint, String destinationEmail, long workspaceId) {
      return migration;
    }

    @Override
    public WorkspaceSnapshotMigration migrateJsonbDocument(
        String expectedFingerprint, String destinationEmail, long workspaceId) {
      return migration;
    }

    @Override
    public WorkspaceSnapshotMigration getMigration(UUID migrationId) {
      return migration;
    }

    @Override
    public WorkspaceSnapshotMigration rollback(UUID migrationId) {
      return rolledBack;
    }
  }
}
