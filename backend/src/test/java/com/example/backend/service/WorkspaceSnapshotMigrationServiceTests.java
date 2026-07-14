package com.example.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.backend.domain.financials.AnnualWithdrawal;
import com.example.backend.domain.financials.AssetAccount;
import com.example.backend.domain.financials.DebtAccount;
import com.example.backend.domain.financials.ExpenseBill;
import com.example.backend.domain.financials.FinancialAuditEvent;
import com.example.backend.domain.financials.FinancialProjectionSummary;
import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.domain.financials.ImportantDate;
import com.example.backend.domain.financials.IncomeEvent;
import com.example.backend.domain.financials.IncomeSummaryItem;
import com.example.backend.domain.migration.FinancialSnapshotCounts;
import com.example.backend.domain.migration.WorkspaceSnapshotMigration;
import com.example.backend.repository.FinancialsData;
import com.example.backend.repository.WorkspaceFinancialSnapshotStore;
import com.example.backend.repository.WorkspaceSnapshotMigrationRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.ObjectMapper;

class WorkspaceSnapshotMigrationServiceTests {

  private static final long WORKSPACE_ID = 41;
  private static final long USER_ID = 17;
  private static final Instant NOW = Instant.parse("2026-07-14T18:30:00Z");

  private ObjectMapper objectMapper;
  private TestSnapshotStore snapshotStore;
  private TestMigrationRepository repository;
  private WorkspaceSnapshotMigrationService service;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    snapshotStore = new TestSnapshotStore();
    repository = new TestMigrationRepository(snapshotStore);
    repository.destination =
        new WorkspaceSnapshotMigrationRepository.DestinationWorkspace(
            USER_ID, "owner@example.com", WORKSPACE_ID, "Personal");
    service =
        new WorkspaceSnapshotMigrationService(
            repository, snapshotStore, objectMapper, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void migratesBackedUpJsonAndRollsBackOnlyTheUnchangedSnapshot() throws Exception {
    byte[] source = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(validData(7));

    WorkspaceSnapshotMigration migrated =
        service.migrateJsonFile(source, sha256(source), " owner@example.com ", WORKSPACE_ID);

    assertThat(migrated.status()).isEqualTo("applied");
    assertThat(migrated.sourceKind()).isEqualTo("json_file");
    assertThat(migrated.sourceVersion()).isEqualTo(7);
    assertThat(migrated.destinationUserId()).isEqualTo(USER_ID);
    assertThat(migrated.metadataMatches()).isTrue();
    assertThat(migrated.rollbackEligible()).isTrue();
    assertThat(migrated.currentCounts())
        .isEqualTo(new FinancialSnapshotCounts(1, 1, 1, 1, 1, 1, 1, 1));
    assertThat(repository.sourceDocumentBySnapshot).containsEntry(migrated.snapshotId(), null);

    WorkspaceSnapshotMigration rolledBack = service.rollback(migrated.id());

    assertThat(rolledBack.status()).isEqualTo("rolled_back");
    assertThat(rolledBack.snapshotActive()).isFalse();
    assertThat(rolledBack.metadataMatches()).isTrue();
    assertThat(rolledBack.rollbackEligible()).isFalse();
    assertThat(rolledBack.rolledBackAt()).isEqualTo(NOW);
    assertThatThrownBy(() -> service.rollback(migrated.id()))
        .isInstanceOf(WorkspaceMigrationConflictException.class)
        .hasMessage("The migration is not applied");
  }

  @Test
  void createsCanonicalJsonbBackupAndRequiresItsExactFingerprint() throws Exception {
    FinancialsData storedData = validData(3);
    repository.legacyJsonb =
        new WorkspaceSnapshotMigrationRepository.LegacyJsonbSnapshot(
            91, 8, objectMapper.writeValueAsString(storedData));

    WorkspaceSnapshotMigrationOperations.BackupArtifact backup = service.backupLegacyJsonb();

    assertThat(backup.version()).isEqualTo(8);
    assertThat(backup.fingerprint()).isEqualTo(sha256(backup.bytes()));
    assertThat(objectMapper.readValue(backup.bytes(), FinancialsData.class).version()).isEqualTo(8);

    assertThatThrownBy(
            () -> service.migrateJsonbDocument("0".repeat(64), "owner@example.com", WORKSPACE_ID))
        .isInstanceOf(WorkspaceMigrationConflictException.class)
        .hasMessage("The migration source does not match the backup fingerprint");

    WorkspaceSnapshotMigration migrated =
        service.migrateJsonbDocument(backup.fingerprint(), "owner@example.com", WORKSPACE_ID);

    assertThat(migrated.sourceKind()).isEqualTo("jsonb_document");
    assertThat(migrated.sourceVersion()).isEqualTo(8);
    assertThat(repository.sourceDocumentBySnapshot).containsEntry(migrated.snapshotId(), 91L);
  }

  @Test
  void refusesUnknownOwnersNonEmptyDestinationsAndMalformedSources() throws Exception {
    byte[] source = objectMapper.writeValueAsBytes(validData(7));
    String fingerprint = sha256(source);
    repository.destination = null;

    assertThatThrownBy(
            () -> service.migrateJsonFile(source, fingerprint, "other@example.com", WORKSPACE_ID))
        .isInstanceOf(WorkspaceMigrationNotFoundException.class)
        .hasMessage("The destination owner and workspace were not found");

    repository.destination =
        new WorkspaceSnapshotMigrationRepository.DestinationWorkspace(
            USER_ID, "owner@example.com", WORKSPACE_ID, "Personal");
    snapshotStore.createInitialSnapshot(WORKSPACE_ID, validData(2).toSnapshot());

    assertThatThrownBy(
            () -> service.migrateJsonFile(source, fingerprint, "owner@example.com", WORKSPACE_ID))
        .isInstanceOf(WorkspaceMigrationConflictException.class)
        .hasMessage("The destination workspace already has an active financial snapshot");

    assertThatThrownBy(
            () ->
                service.migrateJsonFile(
                    "not-json".getBytes(StandardCharsets.UTF_8),
                    sha256("not-json".getBytes(StandardCharsets.UTF_8)),
                    "owner@example.com",
                    WORKSPACE_ID))
        .isInstanceOf(WorkspaceMigrationRequestException.class)
        .hasMessage("The migration source is not a valid financial snapshot JSON envelope");
  }

  @Test
  void refusesRollbackAfterTheMigratedSnapshotVersionChanges() throws Exception {
    byte[] source = objectMapper.writeValueAsBytes(validData(7));
    WorkspaceSnapshotMigration migrated =
        service.migrateJsonFile(source, sha256(source), "owner@example.com", WORKSPACE_ID);

    snapshotStore.changeVersion(migrated.snapshotId(), 8);

    WorkspaceSnapshotMigration changed = service.getMigration(migrated.id());
    assertThat(changed.metadataMatches()).isFalse();
    assertThat(changed.rollbackEligible()).isFalse();
    assertThatThrownBy(() -> service.rollback(migrated.id()))
        .isInstanceOf(WorkspaceMigrationConflictException.class)
        .hasMessage("The migrated snapshot changed or is no longer active; rollback was refused");
  }

  private FinancialsData validData(long version) {
    LocalDate start = LocalDate.of(2026, 7, 1);
    LocalDate end = LocalDate.of(2026, 7, 14);
    FinancialProjectionSummary projection =
        new FinancialProjectionSummary(
            start,
            end,
            1,
            1,
            1,
            1,
            1,
            1,
            1,
            new BigDecimal("25.00"),
            new BigDecimal("50.00"),
            new BigDecimal("1000.00"),
            new BigDecimal("200.00"),
            new BigDecimal("800.00"));
    return new FinancialsData(
        version,
        start,
        end,
        List.of(new ExpenseBill(1, "Synthetic Bill", 5, new BigDecimal("25.00"), "Cash", false)),
        List.of(
            new AnnualWithdrawal(
                2, "Synthetic Annual", 6, 15, new BigDecimal("50.00"), "Cash", false)),
        List.of(
            new AssetAccount(
                3,
                "cash-savings",
                "Cash & Savings",
                "Synthetic Asset",
                "Synthetic Bank",
                new BigDecimal("1000.00"))),
        List.of(new DebtAccount(4, "Synthetic Debt", "Synthetic Lender", new BigDecimal("200.00"))),
        List.of(new IncomeSummaryItem(5, "Synthetic Income", "Monthly", new BigDecimal("500.00"))),
        List.of(new IncomeEvent(6, LocalDate.of(2026, 7, 3), "Synthetic Pay", "Paycheck", 10)),
        List.of(new ImportantDate(7, LocalDate.of(2026, 12, 1), "Synthetic Date", "Reminder")),
        List.of(
            new FinancialAuditEvent(
                8,
                Instant.parse("2026-07-10T12:00:00Z"),
                "UPDATE",
                "snapshot",
                null,
                version - 1,
                version,
                "Synthetic migration fixture",
                projection)));
  }

  private String sha256(byte[] bytes) throws Exception {
    return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private static final class TestSnapshotStore implements WorkspaceFinancialSnapshotStore {

    private final Map<Long, StoredSnapshot> byWorkspace = new HashMap<>();
    private final Map<Long, Long> workspaceBySnapshot = new HashMap<>();
    private long nextId = 1;

    @Override
    public Optional<FinancialSnapshot> loadActiveSnapshot(long workspaceId) {
      StoredSnapshot stored = byWorkspace.get(workspaceId);
      return stored == null || !stored.active ? Optional.empty() : Optional.of(stored.snapshot);
    }

    @Override
    public long createInitialSnapshot(long workspaceId, FinancialSnapshot snapshot) {
      if (loadActiveSnapshot(workspaceId).isPresent()) {
        throw new DuplicateKeyException("active workspace snapshot");
      }
      long id = nextId++;
      byWorkspace.put(workspaceId, new StoredSnapshot(id, snapshot, true));
      workspaceBySnapshot.put(id, workspaceId);
      return id;
    }

    @Override
    public boolean deactivateSnapshotIfUnchanged(
        long workspaceId, long snapshotId, long expectedVersion) {
      StoredSnapshot stored = byWorkspace.get(workspaceId);
      if (stored == null
          || stored.id != snapshotId
          || !stored.active
          || stored.snapshot.version() != expectedVersion) {
        return false;
      }
      byWorkspace.put(workspaceId, new StoredSnapshot(stored.id, stored.snapshot, false));
      return true;
    }

    private StoredSnapshot required(long snapshotId) {
      Long workspaceId = workspaceBySnapshot.get(snapshotId);
      if (workspaceId == null) {
        throw new IllegalStateException("missing snapshot");
      }
      return byWorkspace.get(workspaceId);
    }

    private void changeVersion(long snapshotId, long version) {
      Long workspaceId = workspaceBySnapshot.get(snapshotId);
      StoredSnapshot stored = required(snapshotId);
      byWorkspace.put(
          workspaceId,
          new StoredSnapshot(stored.id, stored.snapshot.withVersion(version), stored.active));
    }

    private record StoredSnapshot(long id, FinancialSnapshot snapshot, boolean active) {}
  }

  private static final class TestMigrationRepository
      implements WorkspaceSnapshotMigrationRepository {

    private final TestSnapshotStore snapshotStore;
    private final Map<Long, List<FinancialAuditEvent>> auditBySnapshot = new HashMap<>();
    private final Map<UUID, MigrationRecord> migrations = new HashMap<>();
    private final Map<Long, Long> sourceDocumentBySnapshot = new HashMap<>();
    private LegacyJsonbSnapshot legacyJsonb;
    private DestinationWorkspace destination;

    private TestMigrationRepository(TestSnapshotStore snapshotStore) {
      this.snapshotStore = snapshotStore;
    }

    @Override
    public Optional<LegacyJsonbSnapshot> findLegacyJsonbSnapshot() {
      return Optional.ofNullable(legacyJsonb);
    }

    @Override
    public Optional<DestinationWorkspace> findOwnerDestination(String email, long workspaceId) {
      if (destination == null
          || !destination.email().equalsIgnoreCase(email)
          || destination.workspaceId() != workspaceId) {
        return Optional.empty();
      }
      return Optional.of(destination);
    }

    @Override
    public void attachSourceDocument(long snapshotId, Long sourceDocumentId) {
      sourceDocumentBySnapshot.put(snapshotId, sourceDocumentId);
    }

    @Override
    public void saveAuditEvents(long snapshotId, List<FinancialAuditEvent> auditEvents) {
      auditBySnapshot.put(snapshotId, List.copyOf(auditEvents));
    }

    @Override
    public SnapshotMetadata snapshotMetadata(long snapshotId) {
      TestSnapshotStore.StoredSnapshot stored = snapshotStore.required(snapshotId);
      FinancialSnapshot snapshot = stored.snapshot();
      return new SnapshotMetadata(
          stored.active(),
          snapshot.version(),
          new FinancialSnapshotCounts(
              snapshot.bills().size(),
              snapshot.annualWithdrawals().size(),
              snapshot.assetAccounts().size(),
              snapshot.debtAccounts().size(),
              snapshot.incomeSummaryItems().size(),
              snapshot.incomeEvents().size(),
              snapshot.importantDates().size(),
              auditBySnapshot.getOrDefault(snapshotId, List.of()).size()));
    }

    @Override
    public void createMigration(MigrationRecord migration) {
      migrations.put(migration.id(), migration);
    }

    @Override
    public Optional<MigrationRecord> findMigration(UUID migrationId) {
      return Optional.ofNullable(migrations.get(migrationId));
    }

    @Override
    public boolean markRolledBack(UUID migrationId, Instant rolledBackAt) {
      MigrationRecord migration = migrations.get(migrationId);
      if (migration == null || !"applied".equals(migration.status())) {
        return false;
      }
      migrations.put(
          migrationId,
          new MigrationRecord(
              migration.id(),
              migration.sourceKind(),
              migration.sourceFingerprint(),
              migration.sourceVersion(),
              migration.sourceDocumentId(),
              migration.destinationUserId(),
              migration.destinationEmail(),
              migration.workspaceId(),
              migration.workspaceName(),
              migration.snapshotId(),
              migration.expectedCounts(),
              "rolled_back",
              migration.appliedAt(),
              rolledBackAt));
      return true;
    }
  }
}
