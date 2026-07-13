import { useCallback, useEffect, useMemo, useState } from 'react';

import type { ExpenseSnapshot, ExpenseSnapshotRequest } from '../../api/endpoints/financials';
import { isPrimaryPaycheck, isRentReserveAccount, isRentWithdrawal } from './financialsAnchors';
import {
  buildExpenseSnapshotRequest,
  createFinancialsDraft,
  getTodayIso,
  removalItemType,
} from './financialsDraft';
import { buildProjectionSummary } from './financialsProjection';
import type {
  DraftAnnualWithdrawal,
  DraftAssetAccount,
  DraftBill,
  DraftDebtAccount,
  DraftImportantDate,
  DraftIncomeEvent,
  DraftIncomeSummaryItem,
  PendingRemoval,
  ProjectionSummary,
} from './financialsTypes';
import { useAnnualWithdrawalsDraft } from './useAnnualWithdrawalsDraft';
import { useAssetAccountsDraft } from './useAssetAccountsDraft';
import { useDebtAccountsDraft } from './useDebtAccountsDraft';
import { useImportantDatesDraft } from './useImportantDatesDraft';
import { useIncomeCalendarDraft } from './useIncomeCalendarDraft';
import { useIncomeSummaryDraft } from './useIncomeSummaryDraft';
import { useMonthlyWithdrawalsDraft } from './useMonthlyWithdrawalsDraft';

export function useFinancialsDraftWorkspace(snapshot: ExpenseSnapshot | null) {
  const [isDirty, setIsDirty] = useState(false);
  const [pendingRemoval, setPendingRemoval] = useState<PendingRemoval | null>(null);
  const markDirty = useCallback(() => setIsDirty(true), []);
  const todayIso = useMemo(getTodayIso, []);

  const monthlyDraft = useMonthlyWithdrawalsDraft(markDirty);
  const annualDraft = useAnnualWithdrawalsDraft(
    markDirty,
    monthlyDraft.payPeriodStart,
    monthlyDraft.payPeriodEnd
  );
  const assetDraft = useAssetAccountsDraft(markDirty);
  const debtDraft = useDebtAccountsDraft(markDirty);
  const incomeSummaryDraft = useIncomeSummaryDraft(
    markDirty,
    monthlyDraft.totals.totalMonthlyExpenses
  );
  const incomeCalendarDraft = useIncomeCalendarDraft(markDirty, todayIso);
  const importantDatesDraft = useImportantDatesDraft(markDirty, todayIso);
  const loadMonthlyDraft = monthlyDraft.loadDraft;
  const loadAnnualDraft = annualDraft.loadDraft;
  const loadAssetDraft = assetDraft.loadDraft;
  const loadDebtDraft = debtDraft.loadDraft;
  const loadIncomeSummaryDraft = incomeSummaryDraft.loadDraft;
  const loadIncomeCalendarDraft = incomeCalendarDraft.loadDraft;
  const loadImportantDatesDraft = importantDatesDraft.loadDraft;

  const loadSnapshotDraft = useCallback(
    (currentSnapshot: ExpenseSnapshot) => {
      const draft = createFinancialsDraft(currentSnapshot);

      loadMonthlyDraft(draft);
      loadAnnualDraft(draft);
      loadAssetDraft(draft);
      loadDebtDraft(draft);
      loadIncomeSummaryDraft(draft);
      loadIncomeCalendarDraft(draft);
      loadImportantDatesDraft(draft);
    },
    [
      loadAnnualDraft,
      loadAssetDraft,
      loadDebtDraft,
      loadImportantDatesDraft,
      loadIncomeCalendarDraft,
      loadIncomeSummaryDraft,
      loadMonthlyDraft,
    ]
  );

  useEffect(() => {
    if (snapshot) {
      loadSnapshotDraft(snapshot);
      setIsDirty(false);
      setPendingRemoval(null);
    }
  }, [loadSnapshotDraft, snapshot]);

  const totals = useMemo(
    () => ({ ...annualDraft.totals, ...monthlyDraft.totals }),
    [annualDraft.totals, monthlyDraft.totals]
  );
  const netWorth = assetDraft.totalTrackedAssets - debtDraft.totalDebt;
  const cashSavings = assetDraft.assetCategories.find(
    (category) => category.key === 'cash-savings'
  );
  const projection = useMemo<ProjectionSummary>(() => {
    return buildProjectionSummary({
      annualWithdrawals: annualDraft.annualWithdrawals,
      annualWithdrawalsInPayPeriod: annualDraft.annualWithdrawalsInPayPeriod,
      cashSavings,
      paycheckIncome: incomeSummaryDraft.primaryPaycheckIncome?.amount ?? 0,
      payPeriodEnd: monthlyDraft.payPeriodEnd,
      payPeriodStart: monthlyDraft.payPeriodStart,
      sortedBills: monthlyDraft.sortedBills,
      totalDebt: debtDraft.totalDebt,
    });
  }, [
    annualDraft.annualWithdrawals,
    annualDraft.annualWithdrawalsInPayPeriod,
    cashSavings,
    debtDraft.totalDebt,
    incomeSummaryDraft.primaryPaycheckIncome,
    monthlyDraft.payPeriodEnd,
    monthlyDraft.payPeriodStart,
    monthlyDraft.sortedBills,
  ]);

  function buildSaveRequest(): ExpenseSnapshotRequest | null {
    if (!snapshot) {
      return null;
    }

    return buildExpenseSnapshotRequest({
      annualWithdrawals: annualDraft.annualWithdrawals,
      assetCategories: assetDraft.assetCategories,
      bills: monthlyDraft.sortedBills,
      debtAccounts: debtDraft.debtAccounts,
      incomeEvents: incomeCalendarDraft.incomeEvents,
      incomeSummaryItems: incomeSummaryDraft.draftIncomeSummaryItems,
      importantDates: importantDatesDraft.importantDates,
      payPeriodEnd: monthlyDraft.payPeriodEnd,
      payPeriodStart: monthlyDraft.payPeriodStart,
      version: snapshot.version,
    });
  }

  function resetDraft() {
    if (!snapshot) {
      return;
    }

    loadSnapshotDraft(snapshot);
    setPendingRemoval(null);
    setIsDirty(false);
  }

  function requestRemoveBill(bill: DraftBill) {
    if (!isRentWithdrawal(bill)) {
      setPendingRemoval({ id: bill.id, name: bill.bill, type: 'bill' });
    }
  }

  function requestRemoveAnnualWithdrawal(withdrawal: DraftAnnualWithdrawal) {
    setPendingRemoval({ id: withdrawal.id, name: withdrawal.bill, type: 'annual-withdrawal' });
  }

  function requestRemoveAsset(categoryKey: string, account: DraftAssetAccount) {
    if (!isRentReserveAccount(account)) {
      setPendingRemoval({ categoryKey, id: account.id, name: account.account, type: 'asset' });
    }
  }

  function requestRemoveDebt(account: DraftDebtAccount) {
    setPendingRemoval({ id: account.id, name: account.account, type: 'debt' });
  }

  function requestRemoveIncomeSummaryItem(item: DraftIncomeSummaryItem) {
    if (!isPrimaryPaycheck(item)) {
      setPendingRemoval({
        id: item.id,
        name: `${item.category} / ${item.interval}`,
        type: 'income-summary',
      });
    }
  }

  function requestRemoveIncomeEvent(event: DraftIncomeEvent) {
    setPendingRemoval({ id: event.id, name: event.label, type: 'income' });
  }

  function requestRemoveImportantDate(importantDate: DraftImportantDate) {
    setPendingRemoval({ id: importantDate.id, name: importantDate.event, type: 'important-date' });
  }

  function confirmRemoval() {
    if (!pendingRemoval) {
      return;
    }

    switch (pendingRemoval.type) {
      case 'bill':
        monthlyDraft.removeBill(pendingRemoval.id);
        break;
      case 'annual-withdrawal':
        annualDraft.removeAnnualWithdrawal(pendingRemoval.id);
        break;
      case 'asset':
        assetDraft.removeAsset(pendingRemoval.categoryKey, pendingRemoval.id);
        break;
      case 'debt':
        debtDraft.removeDebt(pendingRemoval.id);
        break;
      case 'income-summary':
        incomeSummaryDraft.removeIncomeSummaryItem(pendingRemoval.id);
        break;
      case 'income':
        incomeCalendarDraft.removeIncomeEvent(pendingRemoval.id);
        break;
      default:
        importantDatesDraft.removeImportantDate(pendingRemoval.id);
    }

    setPendingRemoval(null);
  }

  return {
    annualWithdrawals: {
      annualWithdrawalForm: annualDraft.annualWithdrawalForm,
      annualWithdrawals: annualDraft.annualWithdrawals,
      cancelAnnualWithdrawalEdit: annualDraft.cancelAnnualWithdrawalEdit,
      editingAnnualWithdrawalId: annualDraft.editingAnnualWithdrawalId,
      requestRemoveAnnualWithdrawal,
      startAnnualWithdrawalEdit: annualDraft.startAnnualWithdrawalEdit,
      submitAnnualWithdrawal: annualDraft.submitAnnualWithdrawal,
      totals: annualDraft.totals,
      updateAnnualWithdrawalForm: annualDraft.updateAnnualWithdrawalForm,
    },
    assetAccounts: {
      assetCategories: assetDraft.assetCategories,
      assetForm: assetDraft.assetForm,
      cancelAssetEdit: assetDraft.cancelAssetEdit,
      editingAsset: assetDraft.editingAsset,
      requestRemoveAsset,
      startAssetEdit: assetDraft.startAssetEdit,
      submitAsset: assetDraft.submitAsset,
      updateAssetForm: assetDraft.updateAssetForm,
    },
    buildSaveRequest,
    cancelRemoval: () => setPendingRemoval(null),
    confirmRemoval,
    debtAccounts: {
      cancelDebtEdit: debtDraft.cancelDebtEdit,
      debtAccounts: debtDraft.debtAccounts,
      debtForm: debtDraft.debtForm,
      editingDebtId: debtDraft.editingDebtId,
      requestRemoveDebt,
      startDebtEdit: debtDraft.startDebtEdit,
      submitDebt: debtDraft.submitDebt,
      totalDebt: debtDraft.totalDebt,
      updateDebtForm: debtDraft.updateDebtForm,
    },
    importantDates: {
      cancelImportantDateEdit: importantDatesDraft.cancelImportantDateEdit,
      editingImportantDateId: importantDatesDraft.editingImportantDateId,
      importantDateForm: importantDatesDraft.importantDateForm,
      importantDates: importantDatesDraft.importantDates,
      requestRemoveImportantDate,
      startImportantDateEdit: importantDatesDraft.startImportantDateEdit,
      submitImportantDate: importantDatesDraft.submitImportantDate,
      updateImportantDateForm: importantDatesDraft.updateImportantDateForm,
    },
    incomeCalendar: {
      cancelIncomeEventEdit: incomeCalendarDraft.cancelIncomeEventEdit,
      editingIncomeEventId: incomeCalendarDraft.editingIncomeEventId,
      incomeEventForm: incomeCalendarDraft.incomeEventForm,
      incomeEvents: incomeCalendarDraft.incomeEvents,
      recurringPaydayForm: incomeCalendarDraft.recurringPaydayForm,
      requestRemoveIncomeEvent,
      startIncomeEventEdit: incomeCalendarDraft.startIncomeEventEdit,
      submitIncomeEvent: incomeCalendarDraft.submitIncomeEvent,
      submitRecurringPaydays: incomeCalendarDraft.submitRecurringPaydays,
      updateIncomeEventForm: incomeCalendarDraft.updateIncomeEventForm,
      updateRecurringPaydayForm: incomeCalendarDraft.updateRecurringPaydayForm,
    },
    incomeSummary: {
      cancelIncomeSummaryItemEdit: incomeSummaryDraft.cancelIncomeSummaryItemEdit,
      derivedIncomeSummaryItems: incomeSummaryDraft.derivedIncomeSummaryItems,
      editingIncomeSummaryItemId: incomeSummaryDraft.editingIncomeSummaryItemId,
      incomeSummaryForm: incomeSummaryDraft.incomeSummaryForm,
      requestRemoveIncomeSummaryItem,
      sourceIncomeSummaryItems: incomeSummaryDraft.sourceIncomeSummaryItems,
      startIncomeSummaryItemEdit: incomeSummaryDraft.startIncomeSummaryItemEdit,
      submitIncomeSummaryItem: incomeSummaryDraft.submitIncomeSummaryItem,
      updateIncomeSummaryForm: incomeSummaryDraft.updateIncomeSummaryForm,
    },
    isDirty,
    monthlyWithdrawals: {
      annualPayPeriodTotal: annualDraft.totals.annualPayPeriodTotal,
      annualWithdrawalsInPayPeriod: annualDraft.annualWithdrawalsInPayPeriod,
      cancelEdit: monthlyDraft.cancelEdit,
      editingId: monthlyDraft.editingId,
      form: monthlyDraft.form,
      formTitle: monthlyDraft.formTitle,
      payPeriodEnd: monthlyDraft.payPeriodEnd,
      payPeriodStart: monthlyDraft.payPeriodStart,
      requestRemoveBill,
      sortedBills: monthlyDraft.sortedBills,
      startEdit: monthlyDraft.startEdit,
      submitBill: monthlyDraft.submitBill,
      totals: monthlyDraft.totals,
      updateForm: monthlyDraft.updateForm,
      updatePayPeriodEnd: monthlyDraft.updatePayPeriodEnd,
      updatePayPeriodStart: monthlyDraft.updatePayPeriodStart,
    },
    overview: {
      annualTotal: totals.totalAnnualWithdrawals,
      assetCategories: assetDraft.assetCategories,
      currentPaycheck: incomeCalendarDraft.currentPaycheck,
      netWorth,
      nextImportantDate: importantDatesDraft.nextImportantDate,
      primaryPaycheckIncome: incomeSummaryDraft.primaryPaycheckIncome?.amount,
      projection,
      totalDebt: debtDraft.totalDebt,
      totalTrackedAssets: assetDraft.totalTrackedAssets,
      withdrawalTotal: totals.totalMonthlyExpenses,
    },
    removalConfirmation: pendingRemoval
      ? { itemName: pendingRemoval.name, itemType: removalItemType(pendingRemoval) }
      : null,
    projection,
    resetDraft,
  };
}

export type FinancialsDraftWorkspace = ReturnType<typeof useFinancialsDraftWorkspace>;
