package com.example.backend.service;

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
import com.example.backend.repository.WorkspaceSnapshotMigrationRepository.DestinationWorkspace;
import com.example.backend.repository.WorkspaceSnapshotMigrationRepository.LegacyJsonbSnapshot;
import com.example.backend.repository.WorkspaceSnapshotMigrationRepository.MigrationRecord;
import com.example.backend.repository.WorkspaceSnapshotMigrationRepository.SnapshotMetadata;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToLongFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class WorkspaceSnapshotMigrationService implements WorkspaceSnapshotMigrationOperations {

  public static final String JSON_FILE_SOURCE = "json_file";
  public static final String JSONB_DOCUMENT_SOURCE = "jsonb_document";

  private static final int SNAPSHOT_AMOUNT_INTEGER_DIGITS = 12;
  private static final int PROJECTION_AMOUNT_INTEGER_DIGITS = 18;

  private final WorkspaceSnapshotMigrationRepository repository;
  private final WorkspaceFinancialSnapshotStore snapshotStore;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final FinancialSnapshotNormalizer normalizer;

  @Autowired
  public WorkspaceSnapshotMigrationService(
      WorkspaceSnapshotMigrationRepository repository,
      WorkspaceFinancialSnapshotStore snapshotStore,
      ObjectMapper objectMapper,
      FinancialSnapshotNormalizer normalizer) {
    this(repository, snapshotStore, objectMapper, Clock.systemUTC(), normalizer);
  }

  WorkspaceSnapshotMigrationService(
      WorkspaceSnapshotMigrationRepository repository,
      WorkspaceFinancialSnapshotStore snapshotStore,
      ObjectMapper objectMapper,
      Clock clock) {
    this(repository, snapshotStore, objectMapper, clock, new FinancialSnapshotNormalizer());
  }

  WorkspaceSnapshotMigrationService(
      WorkspaceSnapshotMigrationRepository repository,
      WorkspaceFinancialSnapshotStore snapshotStore,
      ObjectMapper objectMapper,
      Clock clock,
      FinancialSnapshotNormalizer normalizer) {
    this.repository = repository;
    this.snapshotStore = snapshotStore;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.normalizer = normalizer;
  }

  @Transactional(readOnly = true)
  @Override
  public BackupArtifact backupLegacyJsonb() {
    MigrationSource source = legacyJsonbSource();
    return new BackupArtifact(
        source.bytes(), fingerprint(source.bytes()), source.data().version(), source.counts());
  }

  @Transactional
  @Override
  public WorkspaceSnapshotMigration migrateJsonFile(
      byte[] sourceBytes, String expectedFingerprint, String destinationEmail, long workspaceId) {
    if (sourceBytes == null || sourceBytes.length == 0) {
      throw new WorkspaceMigrationRequestException("The JSON migration source is empty");
    }
    FinancialsData data = parse(sourceBytes);
    return migrate(
        new MigrationSource(JSON_FILE_SOURCE, sourceBytes, data, null),
        expectedFingerprint,
        destinationEmail,
        workspaceId);
  }

  @Transactional
  @Override
  public WorkspaceSnapshotMigration migrateJsonbDocument(
      String expectedFingerprint, String destinationEmail, long workspaceId) {
    return migrate(legacyJsonbSource(), expectedFingerprint, destinationEmail, workspaceId);
  }

  @Transactional(readOnly = true)
  @Override
  public WorkspaceSnapshotMigration getMigration(UUID migrationId) {
    MigrationRecord migration = requiredMigration(migrationId);
    SnapshotMetadata metadata = repository.snapshotMetadata(migration.snapshotId());
    boolean metadataMatches =
        metadata.version() == migration.sourceVersion()
            && metadata.counts().equals(migration.expectedCounts());
    boolean rollbackEligible =
        "applied".equals(migration.status()) && metadata.active() && metadataMatches;

    return new WorkspaceSnapshotMigration(
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
        migration.expectedCounts(),
        metadata.active(),
        metadata.version(),
        metadata.counts(),
        metadataMatches,
        rollbackEligible,
        migration.appliedAt(),
        migration.rolledBackAt());
  }

  @Transactional
  @Override
  public WorkspaceSnapshotMigration rollback(UUID migrationId) {
    WorkspaceSnapshotMigration migration = getMigration(migrationId);
    if (!"applied".equals(migration.status())) {
      throw new WorkspaceMigrationConflictException("The migration is not applied");
    }
    if (!migration.rollbackEligible()) {
      throw new WorkspaceMigrationConflictException(
          "The migrated snapshot changed or is no longer active; rollback was refused");
    }

    boolean deactivated =
        snapshotStore.deactivateSnapshotIfUnchanged(
            migration.workspaceId(), migration.snapshotId(), migration.sourceVersion());
    if (!deactivated) {
      throw new WorkspaceMigrationConflictException(
          "The migrated snapshot changed while rollback was being checked");
    }

    if (!repository.markRolledBack(migrationId, clock.instant())) {
      throw new WorkspaceMigrationConflictException(
          "The migration status changed while rollback was being applied");
    }
    return getMigration(migrationId);
  }

  private WorkspaceSnapshotMigration migrate(
      MigrationSource source,
      String expectedFingerprint,
      String destinationEmail,
      long workspaceId) {
    String actualFingerprint = fingerprint(source.bytes());
    requireMatchingFingerprint(expectedFingerprint, actualFingerprint);
    validate(source.data());

    DestinationWorkspace destination =
        repository
            .findOwnerDestination(requireDestinationEmail(destinationEmail), workspaceId)
            .orElseThrow(
                () ->
                    new WorkspaceMigrationNotFoundException(
                        "The destination owner and workspace were not found"));

    if (snapshotStore.loadActiveSnapshot(workspaceId).isPresent()) {
      throw new WorkspaceMigrationConflictException(
          "The destination workspace already has an active financial snapshot");
    }

    FinancialSnapshot migratedSnapshot = normalizer.normalize(source.data().toSnapshot());
    FinancialSnapshotCounts expectedCounts =
        FinancialSnapshotCounts.from(migratedSnapshot, source.data().auditEvents().size());

    long snapshotId;
    try {
      snapshotId = snapshotStore.createInitialSnapshot(workspaceId, migratedSnapshot);
    } catch (DuplicateKeyException exception) {
      throw new WorkspaceMigrationConflictException(
          "The destination workspace changed while migration was starting");
    }

    repository.attachSourceDocument(snapshotId, source.sourceDocumentId());
    repository.saveAuditEvents(snapshotId, source.data().auditEvents());

    SnapshotMetadata metadata = repository.snapshotMetadata(snapshotId);
    if (!metadata.active()
        || metadata.version() != source.data().version()
        || !metadata.counts().equals(expectedCounts)) {
      throw new IllegalStateException("Relational migration metadata did not match the source");
    }

    Instant appliedAt = clock.instant();
    UUID migrationId = UUID.randomUUID();
    repository.createMigration(
        new MigrationRecord(
            migrationId,
            source.sourceKind(),
            actualFingerprint,
            source.data().version(),
            source.sourceDocumentId(),
            destination.userId(),
            destination.email(),
            destination.workspaceId(),
            destination.workspaceName(),
            snapshotId,
            expectedCounts,
            "applied",
            appliedAt,
            null));
    return getMigration(migrationId);
  }

  private MigrationSource legacyJsonbSource() {
    LegacyJsonbSnapshot stored =
        repository
            .findLegacyJsonbSnapshot()
            .orElseThrow(
                () ->
                    new WorkspaceMigrationNotFoundException(
                        "No active legacy JSONB snapshot was found"));
    FinancialsData data =
        parse(stored.snapshotJson().getBytes(StandardCharsets.UTF_8)).withVersion(stored.version());
    validate(data);
    byte[] backupBytes;
    try {
      backupBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
    } catch (RuntimeException exception) {
      throw new WorkspaceMigrationRequestException(
          "The legacy JSONB snapshot could not be serialized for backup", exception);
    }
    return new MigrationSource(JSONB_DOCUMENT_SOURCE, backupBytes, data, stored.documentId());
  }

  private FinancialsData parse(byte[] sourceBytes) {
    try {
      return objectMapper.readValue(sourceBytes, FinancialsData.class);
    } catch (RuntimeException exception) {
      throw new WorkspaceMigrationRequestException(
          "The migration source is not a valid financial snapshot JSON envelope", exception);
    }
  }

  private void requireMatchingFingerprint(String expectedFingerprint, String actualFingerprint) {
    if (expectedFingerprint == null || !expectedFingerprint.matches("(?i)[0-9a-f]{64}")) {
      throw new WorkspaceMigrationRequestException(
          "A 64-character SHA-256 backup fingerprint is required");
    }
    if (!actualFingerprint.equalsIgnoreCase(expectedFingerprint)) {
      throw new WorkspaceMigrationConflictException(
          "The migration source does not match the backup fingerprint");
    }
  }

  private String requireDestinationEmail(String destinationEmail) {
    if (destinationEmail == null || destinationEmail.isBlank()) {
      throw new WorkspaceMigrationRequestException("Destination email is required");
    }
    return destinationEmail.trim();
  }

  private MigrationRecord requiredMigration(UUID migrationId) {
    return repository
        .findMigration(migrationId)
        .orElseThrow(() -> new WorkspaceMigrationNotFoundException("The migration was not found"));
  }

  private String fingerprint(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private void validate(FinancialsData data) {
    if (data.payPeriodStart() == null || data.payPeriodEnd() == null) {
      throw invalidSource("Pay-period dates are required");
    }
    if (data.payPeriodEnd().isBefore(data.payPeriodStart())) {
      throw invalidSource("Pay-period end must be on or after its start");
    }

    validateIds(data.bills(), ExpenseBill::id, "monthly bills");
    for (ExpenseBill bill : data.bills()) {
      requiredText(bill.bill(), "Monthly bill name");
      requiredText(bill.account(), "Monthly bill account");
      if (bill.dueDay() < 1 || bill.dueDay() > 31) {
        throw invalidSource("Monthly bill due days must be between 1 and 31");
      }
      nonNegativeAmount(bill.amount(), "Monthly bill amount", SNAPSHOT_AMOUNT_INTEGER_DIGITS);
    }

    validateIds(data.annualWithdrawals(), AnnualWithdrawal::id, "annual withdrawals");
    for (AnnualWithdrawal withdrawal : data.annualWithdrawals()) {
      requiredText(withdrawal.bill(), "Annual withdrawal name");
      requiredText(withdrawal.account(), "Annual withdrawal account");
      if (withdrawal.month() < 1 || withdrawal.month() > 12) {
        throw invalidSource("Annual withdrawal months must be between 1 and 12");
      }
      if (withdrawal.day() < 1
          || withdrawal.day() > YearMonth.of(2024, withdrawal.month()).lengthOfMonth()) {
        throw invalidSource("Annual withdrawal days must be valid for their month");
      }
      nonNegativeAmount(
          withdrawal.amount(), "Annual withdrawal amount", SNAPSHOT_AMOUNT_INTEGER_DIGITS);
    }

    validateIds(data.assetAccounts(), AssetAccount::id, "asset accounts");
    for (AssetAccount account : data.assetAccounts()) {
      requiredText(account.categoryKey(), "Asset category key");
      requiredText(account.categoryLabel(), "Asset category label");
      requiredText(account.account(), "Asset account name");
      requiredText(account.company(), "Asset company");
      nonNegativeAmount(account.amount(), "Asset amount", SNAPSHOT_AMOUNT_INTEGER_DIGITS);
    }

    validateIds(data.debtAccounts(), DebtAccount::id, "debt accounts");
    for (DebtAccount account : data.debtAccounts()) {
      requiredText(account.account(), "Debt account name");
      requiredText(account.company(), "Debt company");
      nonNegativeAmount(account.amount(), "Debt amount", SNAPSHOT_AMOUNT_INTEGER_DIGITS);
    }

    validateIds(data.incomeSummaryItems(), IncomeSummaryItem::id, "income summary items");
    for (IncomeSummaryItem item : data.incomeSummaryItems()) {
      requiredText(item.category(), "Income category");
      requiredText(item.interval(), "Income interval");
      nonNegativeAmount(item.amount(), "Income amount", SNAPSHOT_AMOUNT_INTEGER_DIGITS);
    }

    validateIds(data.incomeEvents(), IncomeEvent::id, "income events");
    for (IncomeEvent event : data.incomeEvents()) {
      if (event.date() == null) {
        throw invalidSource("Income event dates are required");
      }
      requiredText(event.label(), "Income event label");
      requiredText(event.type(), "Income event type");
      if (event.checkNumber() != null && event.checkNumber() < 1) {
        throw invalidSource("Income check numbers must be positive");
      }
    }

    validateIds(data.importantDates(), ImportantDate::id, "important dates");
    for (ImportantDate importantDate : data.importantDates()) {
      if (importantDate.date() == null) {
        throw invalidSource("Important dates require a date");
      }
      requiredText(importantDate.event(), "Important date event");
      requiredText(importantDate.type(), "Important date type");
    }

    validateAuditEvents(data.auditEvents());
  }

  private <T> void validateIds(List<T> records, ToLongFunction<T> id, String recordType) {
    Set<Long> ids = new HashSet<>();
    for (T record : records) {
      if (record == null || id.applyAsLong(record) < 1 || !ids.add(id.applyAsLong(record))) {
        throw invalidSource("Every " + recordType + " record requires a unique positive ID");
      }
    }
  }

  private void validateAuditEvents(List<FinancialAuditEvent> events) {
    validateIds(events, FinancialAuditEvent::id, "audit events");
    for (FinancialAuditEvent event : events) {
      if (event.occurredAt() == null) {
        throw invalidSource("Audit event timestamps are required");
      }
      requiredText(event.action(), "Audit action");
      requiredText(event.resourceType(), "Audit resource type");
      requiredText(event.summary(), "Audit summary");
      if (event.versionBefore() < 1 || event.versionAfter() <= event.versionBefore()) {
        throw invalidSource("Audit event versions are invalid");
      }
      validateProjection(event.projectionSummary());
    }
  }

  private void validateProjection(FinancialProjectionSummary projection) {
    if (projection == null
        || projection.payPeriodStart() == null
        || projection.payPeriodEnd() == null) {
      throw invalidSource("Audit projection dates are required");
    }
    if (projection.payPeriodEnd().isBefore(projection.payPeriodStart())) {
      throw invalidSource("Audit projection pay periods are invalid");
    }
    if (projection.monthlyBillCount() < 0
        || projection.annualWithdrawalCount() < 0
        || projection.assetAccountCount() < 0
        || projection.debtAccountCount() < 0
        || projection.incomeSummaryItemCount() < 0
        || projection.incomeEventCount() < 0
        || projection.importantDateCount() < 0) {
      throw invalidSource("Audit projection counts cannot be negative");
    }
    preciseAmount(
        projection.totalMonthlyExpenses(),
        "Audit monthly-expense total",
        PROJECTION_AMOUNT_INTEGER_DIGITS);
    preciseAmount(
        projection.totalAnnualWithdrawals(),
        "Audit annual-withdrawal total",
        PROJECTION_AMOUNT_INTEGER_DIGITS);
    preciseAmount(
        projection.totalTrackedAssets(), "Audit asset total", PROJECTION_AMOUNT_INTEGER_DIGITS);
    preciseAmount(projection.totalDebt(), "Audit debt total", PROJECTION_AMOUNT_INTEGER_DIGITS);
    preciseAmount(projection.netWorth(), "Audit net-worth total", PROJECTION_AMOUNT_INTEGER_DIGITS);
  }

  private void requiredText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw invalidSource(field + " is required");
    }
  }

  private void nonNegativeAmount(BigDecimal value, String field, int maxIntegerDigits) {
    preciseAmount(value, field, maxIntegerDigits);
    if (value.signum() < 0) {
      throw invalidSource(field + " cannot be negative");
    }
  }

  private void preciseAmount(BigDecimal value, String field, int maxIntegerDigits) {
    if (value == null) {
      throw invalidSource(field + " is required");
    }
    BigDecimal normalized = value.stripTrailingZeros();
    int fractionalDigits = Math.max(normalized.scale(), 0);
    int integerDigits = Math.max(normalized.precision() - normalized.scale(), 0);
    if (fractionalDigits > 2 || integerDigits > maxIntegerDigits) {
      throw invalidSource(field + " exceeds relational numeric precision");
    }
  }

  private WorkspaceMigrationRequestException invalidSource(String detail) {
    return new WorkspaceMigrationRequestException("Invalid migration source: " + detail);
  }

  private record MigrationSource(
      String sourceKind, byte[] bytes, FinancialsData data, Long sourceDocumentId) {

    FinancialSnapshotCounts counts() {
      return FinancialSnapshotCounts.from(data);
    }
  }
}
