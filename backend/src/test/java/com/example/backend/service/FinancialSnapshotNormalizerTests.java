package com.example.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.backend.domain.financials.AssetAccount;
import com.example.backend.domain.financials.ExpenseBill;
import com.example.backend.domain.financials.FinancialPlanningSettings;
import com.example.backend.domain.financials.FinancialProjectionRoles;
import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.domain.financials.IncomeSummaryItem;
import com.example.backend.domain.financials.PayCadence;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinancialSnapshotNormalizerTests {

  private final FinancialSnapshotNormalizer normalizer = new FinancialSnapshotNormalizer();

  @Test
  void preservesMutableLabelsWhenConfiguredRolesReferenceTheRecords() {
    FinancialSnapshot normalized =
        normalizer.normalize(snapshot(new FinancialProjectionRoles(4, 5, 6)));

    assertThat(normalized.bills()).extracting(ExpenseBill::bill).containsExactly("Housing");
    assertThat(normalized.assetAccounts())
        .extracting(AssetAccount::account)
        .containsExactly("Housing buffer");
    assertThat(normalized.incomeSummaryItems())
        .extracting(IncomeSummaryItem::category)
        .containsExactly("Main job");
    assertThat(normalized.projectionRoles()).isEqualTo(new FinancialProjectionRoles(4, 5, 6));
  }

  @Test
  void rejectsAProjectionRoleThatDoesNotReferenceItsRecordType() {
    assertThatThrownBy(() -> normalizer.normalize(snapshot(new FinancialProjectionRoles(99, 5, 6))))
        .isInstanceOf(FinancialRequestException.class)
        .hasMessage("Rent bill projection role must reference one monthly withdrawal");
  }

  @Test
  void infersLegacyRolesWithoutRenamingMatchingRecords() {
    FinancialSnapshot normalized = normalizer.normalize(snapshot(null));

    assertThat(normalized.bills()).extracting(ExpenseBill::bill).containsExactly("Housing", "Rent");
    assertThat(normalized.assetAccounts())
        .extracting(AssetAccount::account)
        .containsExactly("Housing buffer", "Rent Reserve");
    assertThat(normalized.incomeSummaryItems())
        .extracting(IncomeSummaryItem::category)
        .containsExactly("Main job", "Net Income");
    assertThat(normalized.projectionRoles()).isEqualTo(new FinancialProjectionRoles(5, 6, 7));
    assertThat(normalized.planningSettings()).isEqualTo(FinancialPlanningSettings.legacyDefaults());
  }

  @Test
  void preservesExplicitPlanningSettings() {
    FinancialSnapshot source = snapshot(new FinancialProjectionRoles(4, 5, 6));
    FinancialSnapshot configured =
        new FinancialSnapshot(
            source.version(),
            source.payPeriodStart(),
            source.payPeriodEnd(),
            new FinancialPlanningSettings(PayCadence.SEMIMONTHLY, "America/New_York"),
            source.projectionRoles(),
            source.bills(),
            source.annualWithdrawals(),
            source.assetAccounts(),
            source.debtAccounts(),
            source.incomeSummaryItems(),
            source.incomeEvents(),
            source.importantDates());

    assertThat(normalizer.normalize(configured).planningSettings())
        .isEqualTo(new FinancialPlanningSettings(PayCadence.SEMIMONTHLY, "America/New_York"));
  }

  private FinancialSnapshot snapshot(FinancialProjectionRoles roles) {
    return new FinancialSnapshot(
        1,
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 14),
        roles,
        List.of(new ExpenseBill(4, "Housing", 1, money("1200"), "Checking", false)),
        List.of(),
        List.of(
            new AssetAccount(
                5,
                "cash-savings",
                "Cash & Savings",
                "Housing buffer",
                "Example Bank",
                money("600"))),
        List.of(),
        List.of(new IncomeSummaryItem(6, "Main job", "Every two weeks", money("2000"))),
        List.of(),
        List.of());
  }

  private BigDecimal money(String value) {
    return new BigDecimal(value);
  }
}
