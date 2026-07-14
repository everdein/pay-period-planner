package com.example.backend.api;

import com.example.backend.dto.migration.WorkspaceSnapshotMigrationResponse;
import com.example.backend.service.WorkspaceMigrationRequestException;
import com.example.backend.service.WorkspaceSnapshotMigrationOperations;
import com.example.backend.service.WorkspaceSnapshotMigrationOperations.BackupArtifact;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/workspace-migrations")
public class WorkspaceSnapshotMigrationController {

  public static final String CONFIRMATION_HEADER = "X-Confirm-Financial-Migration";
  public static final String APPLY_CONFIRMATION = "APPLY";
  public static final String ROLLBACK_CONFIRMATION = "ROLLBACK";
  public static final String FINGERPRINT_HEADER = "X-Snapshot-SHA256";
  public static final String VERSION_HEADER = "X-Snapshot-Version";

  private final WorkspaceSnapshotMigrationOperations migrationService;

  public WorkspaceSnapshotMigrationController(
      WorkspaceSnapshotMigrationOperations migrationService) {
    this.migrationService = migrationService;
  }

  @GetMapping(value = "/legacy-jsonb-backup", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<byte[]> backupLegacyJsonb() {
    BackupArtifact backup = migrationService.backupLegacyJsonb();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename("legacy-financial-snapshot.json")
                .build()
                .toString())
        .header(FINGERPRINT_HEADER, backup.fingerprint())
        .header(VERSION_HEADER, Long.toString(backup.version()))
        .body(backup.bytes());
  }

  @PostMapping(
      value = "/apply/json-file",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public WorkspaceSnapshotMigrationResponse migrateJsonFile(
      @RequestHeader(CONFIRMATION_HEADER) String confirmation,
      @RequestParam String expectedFingerprint,
      @RequestParam String destinationEmail,
      @RequestParam long workspaceId,
      @RequestBody byte[] sourceBytes) {
    requireConfirmation(confirmation, APPLY_CONFIRMATION);
    return WorkspaceSnapshotMigrationResponse.from(
        migrationService.migrateJsonFile(
            sourceBytes, expectedFingerprint, destinationEmail, workspaceId));
  }

  @PostMapping(value = "/apply/jsonb-document", produces = MediaType.APPLICATION_JSON_VALUE)
  public WorkspaceSnapshotMigrationResponse migrateJsonbDocument(
      @RequestHeader(CONFIRMATION_HEADER) String confirmation,
      @RequestParam String expectedFingerprint,
      @RequestParam String destinationEmail,
      @RequestParam long workspaceId) {
    requireConfirmation(confirmation, APPLY_CONFIRMATION);
    return WorkspaceSnapshotMigrationResponse.from(
        migrationService.migrateJsonbDocument(expectedFingerprint, destinationEmail, workspaceId));
  }

  @GetMapping(value = "/{migrationId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public WorkspaceSnapshotMigrationResponse getMigration(@PathVariable UUID migrationId) {
    return WorkspaceSnapshotMigrationResponse.from(migrationService.getMigration(migrationId));
  }

  @PostMapping(value = "/{migrationId}/rollback", produces = MediaType.APPLICATION_JSON_VALUE)
  public WorkspaceSnapshotMigrationResponse rollback(
      @PathVariable UUID migrationId, @RequestHeader(CONFIRMATION_HEADER) String confirmation) {
    requireConfirmation(confirmation, ROLLBACK_CONFIRMATION);
    return WorkspaceSnapshotMigrationResponse.from(migrationService.rollback(migrationId));
  }

  private void requireConfirmation(String actual, String expected) {
    if (!expected.equals(actual)) {
      throw new WorkspaceMigrationRequestException(
          CONFIRMATION_HEADER + " must be exactly " + expected);
    }
  }
}
