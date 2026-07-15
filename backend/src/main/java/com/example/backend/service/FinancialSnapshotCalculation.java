package com.example.backend.service;

import com.example.backend.domain.financials.AnnualWithdrawal;
import com.example.backend.domain.financials.AssetAccount;
import com.example.backend.domain.financials.DebtAccount;
import com.example.backend.domain.financials.ExpenseBill;
import com.example.backend.domain.financials.FinancialPlanningSettings;
import com.example.backend.domain.financials.FinancialProjectionRoles;
import com.example.backend.domain.financials.ImportantDate;
import com.example.backend.domain.financials.IncomeEvent;
import com.example.backend.domain.financials.IncomeSummaryItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

record FinancialSnapshotCalculation(
    long version,
    PayPeriodDatePolicy.PayPeriod payPeriod,
    LocalDate currentDate,
    FinancialPlanningSettings planningSettings,
    FinancialProjectionRoles projectionRoles,
    BigDecimal totalMonthlyExpenses,
    BigDecimal paidTotal,
    BigDecimal unpaidTotal,
    BigDecimal payPeriodTotal,
    BigDecimal totalAnnualWithdrawals,
    BigDecimal annualPayPeriodTotal,
    BigDecimal totalTrackedAssets,
    BigDecimal totalDebt,
    BigDecimal netWorth,
    List<Bill> bills,
    List<Annual> annualWithdrawals,
    List<AssetCategory> assetCategories,
    List<DebtAccount> debtAccounts,
    List<IncomeSummaryItem> incomeSummaryItems,
    List<Income> incomeEvents,
    List<ImportantDate> importantDates) {

  record Bill(ExpenseBill value, LocalDate dueDate, boolean inPayPeriod) {}

  record Annual(AnnualWithdrawal value, LocalDate dueDate, boolean inPayPeriod) {}

  record AssetCategory(String key, String label, BigDecimal total, List<AssetAccount> accounts) {}

  record Income(IncomeEvent value, int checksInMonth) {}
}
