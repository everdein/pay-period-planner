package com.example.backend.service;

import com.example.backend.domain.financials.FinancialAuditEvent;
import com.example.backend.domain.financials.FinancialProjectionSummary;
import com.example.backend.dto.financials.AnnualWithdrawalResponse;
import com.example.backend.dto.financials.AssetAccountResponse;
import com.example.backend.dto.financials.AssetCategoryResponse;
import com.example.backend.dto.financials.DebtAccountResponse;
import com.example.backend.dto.financials.ExpenseBillResponse;
import com.example.backend.dto.financials.ExpenseSnapshotResponse;
import com.example.backend.dto.financials.FinancialAuditEventResponse;
import com.example.backend.dto.financials.FinancialAuditHistoryResponse;
import com.example.backend.dto.financials.FinancialPlanningSettingsResponse;
import com.example.backend.dto.financials.FinancialProjectionRolesResponse;
import com.example.backend.dto.financials.FinancialProjectionSummaryResponse;
import com.example.backend.dto.financials.ImportantDateResponse;
import com.example.backend.dto.financials.IncomeEventResponse;
import com.example.backend.dto.financials.IncomeSummaryItemResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FinancialSnapshotResponseMapper {

  private static final DateTimeFormatter DISPLAY_DATE_FORMATTER =
      DateTimeFormatter.ofPattern("MM/dd/yyyy");

  ExpenseSnapshotResponse toResponse(FinancialSnapshotCalculation calculation) {
    return new ExpenseSnapshotResponse(
        calculation.version(),
        calculation.payPeriod().startDate(),
        calculation.payPeriod().endDate(),
        calculation.currentDate(),
        new FinancialPlanningSettingsResponse(
            calculation.planningSettings().payCadence().name(),
            calculation.planningSettings().timeZone()),
        new FinancialProjectionRolesResponse(
            calculation.projectionRoles().rentBillId(),
            calculation.projectionRoles().rentReserveAssetAccountId(),
            calculation.projectionRoles().primaryPaycheckIncomeSummaryItemId()),
        calculation.totalMonthlyExpenses(),
        calculation.paidTotal(),
        calculation.unpaidTotal(),
        calculation.payPeriodTotal(),
        calculation.totalAnnualWithdrawals(),
        calculation.annualPayPeriodTotal(),
        calculation.totalTrackedAssets(),
        calculation.totalDebt(),
        calculation.netWorth(),
        calculation.assetCategories().stream().map(this::toResponse).toList(),
        calculation.debtAccounts().stream()
            .map(
                (account) ->
                    new DebtAccountResponse(
                        account.id(), account.account(), account.company(), account.amount()))
            .toList(),
        calculation.incomeSummaryItems().stream()
            .map(
                (item) ->
                    new IncomeSummaryItemResponse(
                        item.id(), item.category(), item.interval(), item.amount()))
            .toList(),
        calculation.bills().stream().map(this::toResponse).toList(),
        calculation.annualWithdrawals().stream().map(this::toResponse).toList(),
        calculation.incomeEvents().stream().map(this::toResponse).toList(),
        calculation.importantDates().stream()
            .map(
                (date) ->
                    new ImportantDateResponse(date.id(), date.date(), date.event(), date.type()))
            .toList());
  }

  FinancialAuditHistoryResponse toAuditHistory(List<FinancialAuditEvent> events) {
    return new FinancialAuditHistoryResponse(events.stream().map(this::toResponse).toList());
  }

  private ExpenseBillResponse toResponse(FinancialSnapshotCalculation.Bill calculation) {
    var bill = calculation.value();
    return new ExpenseBillResponse(
        bill.id(),
        bill.bill(),
        bill.dueDay(),
        ordinal(bill.dueDay()),
        calculation.dueDate(),
        bill.amount(),
        bill.account(),
        bill.paid(),
        calculation.inPayPeriod());
  }

  private AnnualWithdrawalResponse toResponse(FinancialSnapshotCalculation.Annual calculation) {
    var withdrawal = calculation.value();
    return new AnnualWithdrawalResponse(
        withdrawal.id(),
        withdrawal.bill(),
        withdrawal.month(),
        withdrawal.day(),
        dateLabel(calculation.dueDate()),
        calculation.dueDate(),
        withdrawal.amount(),
        withdrawal.account(),
        withdrawal.paid(),
        calculation.inPayPeriod());
  }

  private AssetCategoryResponse toResponse(FinancialSnapshotCalculation.AssetCategory calculation) {
    List<AssetAccountResponse> accounts =
        calculation.accounts().stream()
            .map(
                (account) ->
                    new AssetAccountResponse(
                        account.id(), account.account(), account.company(), account.amount()))
            .toList();
    return new AssetCategoryResponse(
        calculation.key(), calculation.label(), calculation.total(), accounts);
  }

  private IncomeEventResponse toResponse(FinancialSnapshotCalculation.Income calculation) {
    var event = calculation.value();
    return new IncomeEventResponse(
        event.id(),
        event.date(),
        event.label(),
        event.type(),
        event.checkNumber(),
        calculation.checksInMonth());
  }

  private FinancialAuditEventResponse toResponse(FinancialAuditEvent event) {
    return new FinancialAuditEventResponse(
        event.id(),
        event.occurredAt(),
        event.action(),
        event.resourceType(),
        event.resourceId(),
        event.versionBefore(),
        event.versionAfter(),
        event.summary(),
        toResponse(event.projectionSummary()));
  }

  private FinancialProjectionSummaryResponse toResponse(FinancialProjectionSummary summary) {
    return new FinancialProjectionSummaryResponse(
        summary.payPeriodStart(),
        summary.payPeriodEnd(),
        summary.monthlyBillCount(),
        summary.annualWithdrawalCount(),
        summary.assetAccountCount(),
        summary.debtAccountCount(),
        summary.incomeSummaryItemCount(),
        summary.incomeEventCount(),
        summary.importantDateCount(),
        summary.totalMonthlyExpenses(),
        summary.totalAnnualWithdrawals(),
        summary.totalTrackedAssets(),
        summary.totalDebt(),
        summary.netWorth());
  }

  private String dateLabel(LocalDate date) {
    return date.format(DISPLAY_DATE_FORMATTER);
  }

  private String ordinal(int day) {
    if (day >= 11 && day <= 13) {
      return day + "th";
    }

    return switch (day % 10) {
      case 1 -> day + "st";
      case 2 -> day + "nd";
      case 3 -> day + "rd";
      default -> day + "th";
    };
  }
}
