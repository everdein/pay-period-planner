package com.example.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.domain.financials.AssetAccount;
import com.example.backend.domain.financials.ExpenseBill;
import com.example.backend.domain.financials.FinancialAuditEvent;
import com.example.backend.domain.financials.FinancialProjectionRoles;
import com.example.backend.domain.financials.FinancialProjectionSummary;
import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.domain.financials.IncomeSummaryItem;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinancialsRepositoryTests {

  private static final Instant AUDIT_TIME = Instant.parse("2026-07-15T12:00:00Z");

  @Test
  void loadsBoundedAuditHistoryWithoutLoadingTheCurrentSnapshot() {
    FinancialAuditEvent event = auditEvent(4, 3, 4);
    RecordingSnapshotStore store = new RecordingSnapshotStore(emptySnapshot(4), List.of(event));
    FinancialsRepository repository = new FinancialsRepository(store);

    assertThat(repository.auditEvents(1)).containsExactly(event);
    assertThat(store.currentSnapshotLoads).isZero();
    assertThat(store.lastAuditLimit).isEqualTo(1);
  }

  @Test
  void persistsOneNewAuditEventWithTheReplacementSnapshot() {
    RecordingSnapshotStore store = new RecordingSnapshotStore(emptySnapshot(5), List.of());
    FinancialsRepository repository =
        new FinancialsRepository(store, Clock.fixed(AUDIT_TIME, ZoneOffset.UTC));

    FinancialSnapshot current = repository.currentSnapshot();
    repository.replaceSnapshot(current.version(), current);

    assertThat(store.expectedVersion).isEqualTo(5);
    assertThat(store.replacement.version()).isEqualTo(6);
    assertThat(store.auditEvent.id()).isZero();
    assertThat(store.auditEvent.occurredAt()).isEqualTo(AUDIT_TIME);
    assertThat(store.auditEvent.versionBefore()).isEqualTo(5);
    assertThat(store.auditEvent.versionAfter()).isEqualTo(6);
  }

  @Test
  void remapsTemporaryProjectionRoleIdsWithTheirAssignedRecordIds() {
    RecordingSnapshotStore store = new RecordingSnapshotStore(emptySnapshot(5), List.of());
    FinancialsRepository repository = new FinancialsRepository(store);
    FinancialSnapshot replacement =
        new FinancialSnapshot(
            5,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 15),
            new FinancialProjectionRoles(-1, -2, -3),
            List.of(new ExpenseBill(-1, "Housing", 1, BigDecimal.TEN, "Checking", false)),
            List.of(),
            List.of(
                new AssetAccount(
                    -2,
                    "cash-savings",
                    "Cash & Savings",
                    "Buffer",
                    "Example Bank",
                    BigDecimal.TEN)),
            List.of(),
            List.of(new IncomeSummaryItem(-3, "Salary", "Pay Period", BigDecimal.TEN)),
            List.of(),
            List.of());

    repository.replaceSnapshot(5, replacement);

    assertThat(store.replacement.projectionRoles())
        .isEqualTo(new FinancialProjectionRoles(1, 1, 1));
    assertThat(store.replacement.bills()).extracting(ExpenseBill::id).containsExactly(1L);
    assertThat(store.replacement.assetAccounts()).extracting(AssetAccount::id).containsExactly(1L);
    assertThat(store.replacement.incomeSummaryItems())
        .extracting(IncomeSummaryItem::id)
        .containsExactly(1L);
  }

  private static FinancialSnapshot emptySnapshot(long version) {
    return new FinancialSnapshot(
        version,
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 15),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static FinancialAuditEvent auditEvent(long id, long versionBefore, long versionAfter) {
    return new FinancialAuditEvent(
        id,
        AUDIT_TIME,
        "REPLACE",
        "snapshot",
        null,
        versionBefore,
        versionAfter,
        "Replaced full financial snapshot",
        new FinancialProjectionSummary(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 15),
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO));
  }

  private static final class RecordingSnapshotStore implements FinancialsSnapshotStore {

    private FinancialSnapshot current;
    private final List<FinancialAuditEvent> history;
    private int currentSnapshotLoads;
    private int lastAuditLimit;
    private long expectedVersion;
    private FinancialSnapshot replacement;
    private FinancialAuditEvent auditEvent;

    private RecordingSnapshotStore(FinancialSnapshot current, List<FinancialAuditEvent> history) {
      this.current = current;
      this.history = history;
    }

    @Override
    public FinancialSnapshot loadCurrentSnapshot() {
      currentSnapshotLoads += 1;
      return current;
    }

    @Override
    public List<FinancialAuditEvent> loadAuditHistory(int limit) {
      lastAuditLimit = limit;
      return history.stream().limit(limit).toList();
    }

    @Override
    public void replaceSnapshot(
        long expectedVersion, FinancialSnapshot replacement, FinancialAuditEvent auditEvent) {
      this.expectedVersion = expectedVersion;
      this.replacement = replacement;
      this.auditEvent = auditEvent;
      current = replacement;
    }
  }
}
