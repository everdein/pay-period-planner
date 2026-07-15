package com.example.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.domain.financials.PayCadence;
import com.example.backend.dto.financials.FinancialPlanningSettingsRequest;
import com.example.backend.dto.financials.PayPeriodRequest;
import com.example.backend.repository.WorkspaceFinancialSnapshotStore;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class WorkspaceFinancialSnapshotInitializationServiceTests {

  private static final long WORKSPACE_ID = 41;

  private TestSnapshotStore snapshotStore;
  private WorkspaceFinancialSnapshotInitializationService service;

  @BeforeEach
  void setUp() {
    snapshotStore = new TestSnapshotStore();
    service =
        new WorkspaceFinancialSnapshotInitializationService(snapshotStore, () -> WORKSPACE_ID);
  }

  @Test
  void createsAProjectionReadyVersionOneSnapshotForTheAuthenticatedWorkspace() {
    FinancialSnapshot created =
        service.initialize(
            new PayPeriodRequest(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 23)));

    FinancialSnapshot snapshot = snapshotStore.loadActiveSnapshot(WORKSPACE_ID).orElseThrow();
    assertThat(created).isSameAs(snapshot);
    assertThat(snapshot.version()).isEqualTo(1);
    assertThat(snapshot.payPeriodStart()).isEqualTo(LocalDate.of(2026, 7, 10));
    assertThat(snapshot.payPeriodEnd()).isEqualTo(LocalDate.of(2026, 7, 23));
    assertThat(snapshot.planningSettings().payCadence()).isEqualTo(PayCadence.BIWEEKLY);
    assertThat(snapshot.planningSettings().timeZone()).isEqualTo("UTC");
    assertThat(snapshot.bills())
        .singleElement()
        .extracting((bill) -> bill.bill())
        .isEqualTo("Rent");
    assertThat(snapshot.annualWithdrawals()).isEmpty();
    assertThat(snapshot.assetAccounts())
        .singleElement()
        .extracting((account) -> account.account())
        .isEqualTo("Rent Reserve");
    assertThat(snapshot.debtAccounts()).isEmpty();
    assertThat(snapshot.incomeSummaryItems())
        .singleElement()
        .satisfies(
            (item) -> {
              assertThat(item.category()).isEqualTo("Net Income");
              assertThat(item.interval()).isEqualTo("Bi-Weekly");
            });
    assertThat(snapshot.incomeEvents()).isEmpty();
    assertThat(snapshot.importantDates()).isEmpty();
    assertThat(snapshot.projectionRoles().rentBillId()).isEqualTo(snapshot.bills().getFirst().id());
  }

  @Test
  void createsTheSnapshotWithTheRequestedCadenceAndTimeZone() {
    FinancialSnapshot snapshot =
        service.initialize(
            new PayPeriodRequest(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                new FinancialPlanningSettingsRequest("monthly", "America/New_York")));

    assertThat(snapshot.planningSettings().payCadence()).isEqualTo(PayCadence.MONTHLY);
    assertThat(snapshot.planningSettings().timeZone()).isEqualTo("America/New_York");
  }

  @Test
  void rejectsAnInvalidPayPeriodAndAnExistingSnapshot() {
    assertThatThrownBy(
            () ->
                service.initialize(
                    new PayPeriodRequest(LocalDate.of(2026, 7, 23), LocalDate.of(2026, 7, 10))))
        .isInstanceOf(FinancialRequestException.class)
        .hasMessageContaining("must be on or after");

    service.initialize(new PayPeriodRequest(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 23)));

    assertThatThrownBy(
            () ->
                service.initialize(
                    new PayPeriodRequest(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 14))))
        .isInstanceOf(WorkspaceFinancialSnapshotConflictException.class)
        .hasMessage("This workspace already has an active financial snapshot");
  }

  private static final class TestSnapshotStore implements WorkspaceFinancialSnapshotStore {

    private final Map<Long, FinancialSnapshot> snapshots = new HashMap<>();

    @Override
    public Optional<FinancialSnapshot> loadActiveSnapshot(long workspaceId) {
      return Optional.ofNullable(snapshots.get(workspaceId));
    }

    @Override
    public long createInitialSnapshot(long workspaceId, FinancialSnapshot snapshot) {
      if (snapshots.putIfAbsent(workspaceId, snapshot) != null) {
        throw new DuplicateKeyException("active workspace snapshot");
      }
      return workspaceId;
    }

    @Override
    public boolean deactivateSnapshotIfUnchanged(
        long workspaceId, long snapshotId, long expectedVersion) {
      return false;
    }
  }
}
