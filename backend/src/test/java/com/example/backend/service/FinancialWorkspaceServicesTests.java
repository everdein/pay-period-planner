package com.example.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.backend.domain.financials.FinancialAuditEvent;
import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.dto.financials.AnnualWithdrawalSnapshotRequest;
import com.example.backend.dto.financials.AssetAccountSnapshotRequest;
import com.example.backend.dto.financials.AssetCategorySnapshotRequest;
import com.example.backend.dto.financials.DebtAccountSnapshotRequest;
import com.example.backend.dto.financials.ExpenseBillSnapshotRequest;
import com.example.backend.dto.financials.ExpenseSnapshotRequest;
import com.example.backend.dto.financials.FinancialPlanningSettingsRequest;
import com.example.backend.dto.financials.FinancialSnapshotBackup;
import com.example.backend.dto.financials.ImportantDateSnapshotRequest;
import com.example.backend.dto.financials.IncomeEventSnapshotRequest;
import com.example.backend.dto.financials.IncomeSummaryItemSnapshotRequest;
import com.example.backend.repository.FinancialsData;
import com.example.backend.repository.FinancialsRepository;
import com.example.backend.repository.FinancialsSnapshotStore;
import com.example.backend.repository.SnapshotVersionConflictException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FinancialWorkspaceServicesTests {

  @Test
  void returnsSeededMonthlyExpenseSnapshot() throws IOException {
    Services services = services(Clock.systemDefaultZone());

    var snapshot = services.queries().getSnapshot();

    assertThat(snapshot.version()).isEqualTo(1);
    assertThat(snapshot.totalMonthlyExpenses()).isEqualByComparingTo("1450.00");
    assertThat(snapshot.totalAnnualWithdrawals()).isEqualByComparingTo("99.00");
    assertThat(snapshot.totalTrackedAssets()).isEqualByComparingTo("15000.00");
    assertThat(snapshot.totalDebt()).isEqualByComparingTo("500.00");
    assertThat(snapshot.netWorth()).isEqualByComparingTo("14500.00");
    assertThat(snapshot.bills()).isNotEmpty();
    assertThat(snapshot.annualWithdrawals()).hasSize(1);
    assertThat(snapshot.debtAccounts()).hasSize(1);
    assertThat(snapshot.assetCategories())
        .extracting((category) -> category.key())
        .containsExactly("retirement", "investments", "cash-savings", "insurance-benefits");
    assertThat(snapshot.incomeSummaryItems()).hasSize(2);
    assertThat(snapshot.incomeEvents()).hasSize(2);
    assertThat(snapshot.importantDates()).hasSize(1);
    assertThat(snapshot.bills()).anyMatch((bill) -> bill.bill().equals("Example Rent"));
    assertThat(snapshot.projectionRoles().rentBillId()).isEqualTo(1);
    assertThat(snapshot.planningSettings().payCadence()).isEqualTo("BIWEEKLY");
    assertThat(snapshot.planningSettings().timeZone()).isEqualTo("UTC");
    assertThat(snapshot.assetCategories())
        .anyMatch(
            (category) ->
                category.key().equals("cash-savings")
                    && category.accounts().stream()
                        .anyMatch((account) -> account.account().equals("Rent Reserve")));
    assertThat(snapshot.incomeSummaryItems())
        .anyMatch(
            (item) -> item.category().equals("Net Income") && item.interval().equals("Bi-Weekly"));
  }

  @Test
  void rejectsInvalidPlanningSettings() {
    FinancialSnapshotRequestMapper mapper =
        new FinancialSnapshotRequestMapper(new FinancialSnapshotNormalizer());

    assertThatThrownBy(
            () ->
                mapper.toPlanningSettings(
                    new FinancialPlanningSettingsRequest("every-so-often", "UTC")))
        .isInstanceOf(FinancialRequestException.class)
        .hasMessageContaining("WEEKLY, BIWEEKLY, SEMIMONTHLY, or MONTHLY");
    assertThatThrownBy(
            () ->
                mapper.toPlanningSettings(
                    new FinancialPlanningSettingsRequest("WEEKLY", "Eastern-ish")))
        .isInstanceOf(FinancialRequestException.class)
        .hasMessageContaining("valid IANA zone");
  }

  @Test
  void savesPlanningSettingsAndUsesTheirTimeZoneForTheCurrentDate() throws IOException {
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:30:00Z"), ZoneOffset.UTC);
    Services services = services(clock);
    ExpenseSnapshotRequest source = services.queries().exportSnapshot().snapshot();

    var saved =
        services
            .commands()
            .saveSnapshot(
                new ExpenseSnapshotRequest(
                    source.version(),
                    source.payPeriodStart(),
                    source.payPeriodEnd(),
                    new FinancialPlanningSettingsRequest("WEEKLY", "America/Los_Angeles"),
                    source.projectionRoles(),
                    source.bills(),
                    source.annualWithdrawals(),
                    source.assetCategories(),
                    source.debtAccounts(),
                    source.incomeSummaryItems(),
                    source.incomeEvents(),
                    source.importantDates()));

    assertThat(saved.currentDate()).isEqualTo(LocalDate.of(2025, 12, 31));
    assertThat(saved.planningSettings().payCadence()).isEqualTo("WEEKLY");
    assertThat(saved.planningSettings().timeZone()).isEqualTo("America/Los_Angeles");
  }

  @Test
  void exportsSourceSnapshotForBackup() throws IOException {
    Clock clock = Clock.fixed(Instant.parse("2026-07-11T10:15:30Z"), ZoneOffset.UTC);
    Services services = services(clock);

    var backup = services.queries().exportSnapshot();

    assertThat(backup.format()).isEqualTo("end-to-end-app.financial-snapshot.v1");
    assertThat(backup.exportedAt()).isEqualTo(Instant.parse("2026-07-11T10:15:30Z"));
    assertThat(backup.snapshot().version()).isEqualTo(1);
    assertThat(backup.snapshot().payPeriodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(backup.snapshot().payPeriodEnd()).isEqualTo(LocalDate.of(2026, 1, 15));
    assertThat(backup.snapshot().bills())
        .anyMatch((bill) -> bill.id() == 1L && bill.bill().equals("Example Rent"));
    assertThat(backup.snapshot().assetCategories())
        .anyMatch(
            (category) ->
                category.key().equals("cash-savings")
                    && category.accounts().stream()
                        .anyMatch((account) -> account.account().equals("Emergency Fund")));
    assertThat(backup.snapshot().incomeEvents())
        .anyMatch((event) -> event.label().equals("Paycheck") && event.checkNumber() == 1);
  }

  @Test
  void restoresOlderJsonBackupAgainstCurrentTargetVersion() throws IOException {
    Services services = services(Clock.systemDefaultZone());
    FinancialSnapshotBackup backup = services.queries().exportSnapshot();
    ExpenseSnapshotRequest source = backup.snapshot();

    var changed =
        services
            .commands()
            .saveSnapshot(
                new ExpenseSnapshotRequest(
                    source.version(),
                    source.payPeriodStart(),
                    source.payPeriodEnd(),
                    List.of(
                        new ExpenseBillSnapshotRequest(
                            null, "Temporary Bill", 20, money("77"), "Check", false)),
                    source.annualWithdrawals(),
                    source.assetCategories(),
                    source.debtAccounts(),
                    source.incomeSummaryItems(),
                    source.incomeEvents(),
                    source.importantDates()));
    var restored = services.commands().restoreSnapshot(changed.version(), backup);

    assertThat(backup.snapshot().version()).isEqualTo(1);
    assertThat(changed.version()).isEqualTo(2);
    assertThat(restored.version()).isEqualTo(3);
    assertThat(restored.bills()).noneMatch((bill) -> bill.bill().equals("Temporary Bill"));
    assertThat(restored.bills()).anyMatch((bill) -> bill.bill().equals("Example Rent"));
    assertThat(restored.assetCategories())
        .anyMatch(
            (category) ->
                category.key().equals("cash-savings")
                    && category.accounts().stream()
                        .anyMatch((account) -> account.account().equals("Emergency Fund")));
  }

  @Test
  void rejectsStaleJsonRestoreTargetVersion() throws IOException {
    Services services = services(Clock.systemDefaultZone());
    FinancialSnapshotBackup backup = services.queries().exportSnapshot();

    services.commands().saveSnapshot(backup.snapshot());

    assertThatThrownBy(() -> services.commands().restoreSnapshot(1, backup))
        .isInstanceOf(FinancialSnapshotVersionConflictException.class)
        .hasMessageContaining("Reload before saving");
  }

  @Test
  void rejectsUnsupportedJsonBackupFormat() throws IOException {
    Services services = services(Clock.systemDefaultZone());
    FinancialSnapshotBackup backup = services.queries().exportSnapshot();

    assertThatThrownBy(
            () ->
                services
                    .commands()
                    .restoreSnapshot(
                        1,
                        new FinancialSnapshotBackup(
                            "unsupported-format", backup.exportedAt(), backup.snapshot())))
        .isInstanceOf(FinancialRequestException.class)
        .hasMessageContaining("format is not supported");
  }

  @Test
  void recordsAuditHistoryWithProjectionSummary() throws IOException {
    Services services = services(Clock.systemDefaultZone());
    ExpenseSnapshotRequest source = services.queries().exportSnapshot().snapshot();
    List<ExpenseBillSnapshotRequest> bills = new ArrayList<>(source.bills());
    bills.add(
        new ExpenseBillSnapshotRequest(null, "Audit Bill", 5, money("42.50"), "Check", false));
    services
        .commands()
        .saveSnapshot(
            new ExpenseSnapshotRequest(
                source.version(),
                source.payPeriodStart(),
                source.payPeriodEnd(),
                bills,
                source.annualWithdrawals(),
                source.assetCategories(),
                source.debtAccounts(),
                source.incomeSummaryItems(),
                source.incomeEvents(),
                source.importantDates()));
    var history = services.queries().getAuditHistory(10);

    assertThat(history.events()).hasSize(1);
    var event = history.events().getFirst();
    assertThat(event.action()).isEqualTo("REPLACE");
    assertThat(event.resourceType()).isEqualTo("snapshot");
    assertThat(event.resourceId()).isNull();
    assertThat(event.versionBefore()).isEqualTo(source.version());
    assertThat(event.versionAfter()).isEqualTo(source.version() + 1);
    assertThat(event.summary()).isEqualTo("Replaced full financial snapshot");
    assertThat(event.projectionSummary().monthlyBillCount()).isEqualTo(3);
    assertThat(event.projectionSummary().totalMonthlyExpenses()).isEqualByComparingTo("1492.50");
    assertThat(event.projectionSummary().totalTrackedAssets()).isEqualByComparingTo("15000.0");
    assertThat(event.projectionSummary().totalDebt()).isEqualByComparingTo("500.0");
    assertThat(event.projectionSummary().netWorth()).isEqualByComparingTo("14500.0");
  }

  @Test
  void rollsSavedPayPeriodForwardToCurrentDate() throws IOException {
    Clock clock = Clock.fixed(Instant.parse("2026-06-22T12:00:00Z"), ZoneOffset.UTC);
    Services services = services(clock);

    var snapshot = services.queries().getSnapshot();

    assertThat(snapshot.payPeriodStart()).isEqualTo(LocalDate.of(2026, 6, 15));
    assertThat(snapshot.payPeriodEnd()).isEqualTo(LocalDate.of(2026, 6, 29));
    assertThat(snapshot.bills())
        .anyMatch(
            (bill) ->
                bill.bill().equals("Example Savings Transfer")
                    && bill.dueDate().equals(LocalDate.of(2026, 6, 15))
                    && bill.inPayPeriod());
  }

  @Test
  void savesSnapshotInOneBatch() throws IOException {
    Services services = services(Clock.systemDefaultZone());
    long loadedVersion = services.queries().getSnapshot().version();

    var saved =
        services
            .commands()
            .saveSnapshot(
                new ExpenseSnapshotRequest(
                    loadedVersion,
                    LocalDate.of(2026, 6, 12),
                    LocalDate.of(2026, 6, 26),
                    List.of(
                        new ExpenseBillSnapshotRequest(1L, "Rent", 1, money("2600"), "Check", true),
                        new ExpenseBillSnapshotRequest(
                            null, "New Bill", 15, money("25"), "Example Checking", false)),
                    List.of(
                        new AnnualWithdrawalSnapshotRequest(
                            null, "Annual Renewal", 9, 18, money("99"), "Check", false)),
                    List.of(
                        new AssetCategorySnapshotRequest(
                            "retirement",
                            "Retirement",
                            List.of(
                                new AssetAccountSnapshotRequest(
                                    1L,
                                    "Workplace Retirement",
                                    "Example Retirement Provider",
                                    money("110653.42")),
                                new AssetAccountSnapshotRequest(
                                    null, "Pension", "Example", money("1000"))))),
                    List.of(
                        new DebtAccountSnapshotRequest(
                            null, "Rewards Card", "Example Card Issuer", money("2130.03")),
                        new DebtAccountSnapshotRequest(
                            null, "Line of Credit", "Example Credit Union", money("0"))),
                    List.of(
                        new IncomeSummaryItemSnapshotRequest(
                            null, "Disposable Income", "Bi-Weekly", money("1901.58"))),
                    List.of(
                        new IncomeEventSnapshotRequest(
                            1L, LocalDate.of(2026, 6, 12), "Paycheck", "Paycheck", 12),
                        new IncomeEventSnapshotRequest(
                            null, LocalDate.of(2026, 6, 26), "Paycheck", "Paycheck", 13)),
                    List.of(
                        new ImportantDateSnapshotRequest(
                            null, LocalDate.of(2026, 12, 25), "Christmas", "Holiday"))));

    assertThat(saved.version()).isEqualTo(loadedVersion + 1);
    assertThat(saved.bills()).hasSize(2);
    assertThat(saved.bills()).anyMatch((bill) -> bill.bill().equals("Rent"));
    assertThat(saved.annualWithdrawals()).hasSize(1);
    assertThat(saved.totalMonthlyExpenses()).isEqualByComparingTo("2625");
    assertThat(saved.totalAnnualWithdrawals()).isEqualByComparingTo("99");
    assertThat(saved.totalTrackedAssets()).isEqualByComparingTo("111653.42");
    assertThat(saved.totalDebt()).isEqualByComparingTo("2130.03");
    assertThat(saved.netWorth()).isEqualByComparingTo("109523.39");
    assertThat(saved.bills()).anyMatch((bill) -> bill.bill().equals("New Bill") && bill.id() > 0);
    assertThat(saved.assetCategories().getFirst().accounts())
        .anyMatch((account) -> account.account().equals("Pension") && account.id() > 0);
    assertThat(saved.debtAccounts())
        .anyMatch((account) -> account.account().equals("Rewards Card") && account.id() > 0);
    assertThat(saved.incomeSummaryItems())
        .anyMatch((item) -> item.category().equals("Disposable Income") && item.id() > 0);
    assertThat(saved.incomeSummaryItems())
        .anyMatch(
            (item) ->
                item.category().equals("Net Income")
                    && item.interval().equals("Bi-Weekly")
                    && item.id() > 0);
    assertThat(saved.incomeEvents()).hasSize(2);
    assertThat(saved.incomeEvents().getFirst().checksInMonth()).isEqualTo(2);
    assertThat(saved.importantDates())
        .anyMatch((importantDate) -> importantDate.event().equals("Christmas"));
  }

  @Test
  void rejectsStaleSnapshotVersion() throws IOException {
    Services services = services(Clock.systemDefaultZone());
    ExpenseSnapshotRequest staleSnapshot = services.queries().exportSnapshot().snapshot();

    services.commands().saveSnapshot(staleSnapshot);

    assertThatThrownBy(() -> services.commands().saveSnapshot(staleSnapshot))
        .isInstanceOf(FinancialSnapshotVersionConflictException.class)
        .hasMessageContaining("Reload before saving");
  }

  private FinancialsRepository repository() throws IOException {
    FinancialsData seedData =
        new ObjectMapper()
            .readValue(
                """
        {
          "version": 1,
          "payPeriodStart": "2026-01-01",
          "payPeriodEnd": "2026-01-15",
          "bills": [
            {
              "id": 1,
              "bill": "Example Rent",
              "dueDay": 1,
              "amount": 1200.0,
              "account": "Checking",
              "paid": false
            },
            {
              "id": 2,
              "bill": "Example Savings Transfer",
              "dueDay": 15,
              "amount": 250.0,
              "account": "Savings",
              "paid": false
            }
          ],
          "annualWithdrawals": [
            {
              "id": 1,
              "bill": "Example Membership",
              "month": 1,
              "day": 15,
              "amount": 99.0,
              "account": "Checking",
              "paid": false
            }
          ],
          "assetAccounts": [
            {
              "id": 1,
              "categoryKey": "retirement",
              "categoryLabel": "Retirement",
              "account": "Example 401k",
              "company": "Example Provider",
              "amount": 10000.0
            },
            {
              "id": 2,
              "categoryKey": "cash-savings",
              "categoryLabel": "Cash & Savings",
              "account": "Emergency Fund",
              "company": "Example Bank",
              "amount": 5000.0
            }
          ],
          "debtAccounts": [
            {
              "id": 1,
              "account": "Example Credit Card",
              "company": "Example Bank",
              "amount": 500.0
            }
          ],
          "incomeSummaryItems": [
            {
              "id": 1,
              "category": "Net Income",
              "interval": "Annual",
              "amount": 75000.0
            }
          ],
          "incomeEvents": [
            {
              "id": 1,
              "date": "2026-01-09",
              "label": "Paycheck",
              "type": "Paycheck",
              "checkNumber": 1
            },
            {
              "id": 2,
              "date": "2026-01-23",
              "label": "Paycheck",
              "type": "Paycheck",
              "checkNumber": 2
            }
          ],
          "importantDates": [
            {
              "id": 1,
              "date": "2026-01-01",
              "event": "New Years",
              "type": "Holiday"
            }
          ]
        }
        """,
                FinancialsData.class);
    return new FinancialsRepository(new InMemoryFinancialsSnapshotStore(seedData));
  }

  private Services services(Clock clock) throws IOException {
    FinancialsRepository repository = repository();
    FinancialSnapshotNormalizer normalizer = new FinancialSnapshotNormalizer();
    FinancialSnapshotRequestMapper requestMapper = new FinancialSnapshotRequestMapper(normalizer);
    FinancialSnapshotResponseMapper responseMapper = new FinancialSnapshotResponseMapper();
    FinancialSnapshotPresenter snapshotPresenter =
        new CalculatedFinancialSnapshotPresenter(
            new FinancialSnapshotCalculator(normalizer), responseMapper, clock);
    FinancialWorkspaceQueries queries =
        new FinancialWorkspaceQueryService(
            repository, snapshotPresenter, responseMapper, requestMapper, clock);
    FinancialWorkspaceCommands commands =
        new FinancialWorkspaceCommandService(repository, requestMapper, queries);
    return new Services(queries, commands);
  }

  private record Services(FinancialWorkspaceQueries queries, FinancialWorkspaceCommands commands) {}

  private static final class InMemoryFinancialsSnapshotStore implements FinancialsSnapshotStore {

    private FinancialSnapshot snapshot;
    private final List<FinancialAuditEvent> auditEvents;

    private InMemoryFinancialsSnapshotStore(FinancialsData data) {
      snapshot = data.toSnapshot();
      auditEvents = new ArrayList<>(data.auditEvents());
    }

    @Override
    public FinancialSnapshot loadCurrentSnapshot() {
      return snapshot;
    }

    @Override
    public List<FinancialAuditEvent> loadAuditHistory(int limit) {
      return auditEvents.reversed().stream().limit(limit).toList();
    }

    @Override
    public void replaceSnapshot(
        long expectedVersion, FinancialSnapshot replacement, FinancialAuditEvent auditEvent) {
      if (snapshot.version() != expectedVersion) {
        throw new SnapshotVersionConflictException(expectedVersion, snapshot.version());
      }

      long eventId = auditEvents.stream().mapToLong(FinancialAuditEvent::id).max().orElse(0) + 1;
      auditEvents.add(
          new FinancialAuditEvent(
              eventId,
              auditEvent.occurredAt(),
              auditEvent.action(),
              auditEvent.resourceType(),
              auditEvent.resourceId(),
              auditEvent.versionBefore(),
              auditEvent.versionAfter(),
              auditEvent.summary(),
              auditEvent.projectionSummary()));
      snapshot = replacement;
    }
  }

  private BigDecimal money(String value) {
    return new BigDecimal(value);
  }
}
