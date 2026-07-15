import { useEffect, useMemo, useReducer } from 'react';

import type {
  ExpenseSnapshot,
  ExpenseSnapshotRequest,
  FinancialPlanningSettings,
} from '../../api/endpoints/financials';
import { todayIso as todayInTimeZone } from './financialsDatePolicy';
import {
  financialsDraftReducer,
  initialFinancialsDraftWorkspaceState,
  selectDraftRevision,
  selectFinancialsDraft,
  selectFinancialsDraftIsDirty,
  selectFinancialsSaveRequest,
  selectRemovalConfirmation,
} from './financialsDraftReducer';
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

export function useFinancialsDraftWorkspace(
  snapshot: ExpenseSnapshot | null,
  savedDraftRevision: number | null = null
) {
  const [draftState, dispatch] = useReducer(
    financialsDraftReducer,
    initialFinancialsDraftWorkspaceState
  );
  const draft = selectFinancialsDraft(draftState);
  const planningSettings = draft?.planningSettings ?? snapshot?.planningSettings ?? null;
  const todayIso = useMemo(() => {
    if (
      snapshot?.currentDate &&
      planningSettings?.timeZone === snapshot.planningSettings.timeZone
    ) {
      return snapshot.currentDate;
    }
    return todayInTimeZone(planningSettings?.timeZone ?? 'UTC');
  }, [planningSettings?.timeZone, snapshot]);

  const monthlyDraft = useMonthlyWithdrawalsDraft(draft, dispatch, draftState.resetGeneration);
  const annualDraft = useAnnualWithdrawalsDraft(draft, dispatch, draftState.resetGeneration);
  const assetDraft = useAssetAccountsDraft(draft, dispatch, draftState.resetGeneration);
  const debtDraft = useDebtAccountsDraft(draft, dispatch, draftState.resetGeneration);
  const incomeSummaryDraft = useIncomeSummaryDraft(
    draft,
    dispatch,
    draftState.resetGeneration,
    monthlyDraft.totals.totalMonthlyExpenses,
    draft?.projectionRoles.primaryPaycheckIncomeSummaryItemId ?? 0,
    planningSettings?.payCadence ?? 'BIWEEKLY'
  );
  const incomeCalendarDraft = useIncomeCalendarDraft(
    draft,
    dispatch,
    draftState.resetGeneration,
    todayIso,
    planningSettings?.payCadence ?? 'BIWEEKLY'
  );
  const importantDatesDraft = useImportantDatesDraft(
    draft,
    dispatch,
    draftState.resetGeneration,
    todayIso
  );

  useEffect(() => {
    if (snapshot) {
      dispatch({ savedRevision: savedDraftRevision, snapshot, type: 'synchronize' });
    }
  }, [savedDraftRevision, snapshot]);

  const totals = useMemo(
    () => ({ ...annualDraft.totals, ...monthlyDraft.totals }),
    [annualDraft.totals, monthlyDraft.totals]
  );
  const netWorth = assetDraft.totalTrackedAssets - debtDraft.totalDebt;
  const projection = useMemo<ProjectionSummary>(() => {
    return buildProjectionSummary({
      annualWithdrawals: annualDraft.annualWithdrawals,
      annualWithdrawalsInPayPeriod: annualDraft.annualWithdrawalsInPayPeriod,
      assetCategories: assetDraft.assetCategories,
      paycheckIncome: incomeSummaryDraft.primaryPaycheckIncome?.amount ?? 0,
      payPeriodEnd: monthlyDraft.payPeriodEnd,
      payPeriodStart: monthlyDraft.payPeriodStart,
      projectionRoles: draft?.projectionRoles ?? {
        primaryPaycheckIncomeSummaryItemId: 0,
        rentBillId: 0,
        rentReserveAssetAccountId: 0,
      },
      sortedBills: monthlyDraft.sortedBills,
      totalDebt: debtDraft.totalDebt,
    });
  }, [
    annualDraft.annualWithdrawals,
    annualDraft.annualWithdrawalsInPayPeriod,
    assetDraft.assetCategories,
    debtDraft.totalDebt,
    draft?.projectionRoles,
    incomeSummaryDraft.primaryPaycheckIncome,
    monthlyDraft.payPeriodEnd,
    monthlyDraft.payPeriodStart,
    monthlyDraft.sortedBills,
  ]);

  function buildSaveRequest(): ExpenseSnapshotRequest | null {
    return selectFinancialsSaveRequest(draftState);
  }

  function resetDraft() {
    dispatch({ type: 'reset' });
  }

  function updateProjectionRole(
    role: 'primaryPaycheckIncomeSummaryItemId' | 'rentBillId' | 'rentReserveAssetAccountId',
    recordId: number
  ) {
    dispatch({ recordId, role, type: 'update-projection-role' });
  }

  function updatePlanningSetting(
    setting: keyof FinancialPlanningSettings,
    value: FinancialPlanningSettings[keyof FinancialPlanningSettings]
  ) {
    dispatch({ setting, type: 'update-planning-setting', value });
  }

  function requestRemoveBill(bill: DraftBill) {
    requestRemoval({ id: bill.id, name: bill.bill, type: 'bill' });
  }

  function requestRemoveAnnualWithdrawal(withdrawal: DraftAnnualWithdrawal) {
    requestRemoval({ id: withdrawal.id, name: withdrawal.bill, type: 'annual-withdrawal' });
  }

  function requestRemoveAsset(categoryKey: string, account: DraftAssetAccount) {
    requestRemoval({ categoryKey, id: account.id, name: account.account, type: 'asset' });
  }

  function requestRemoveDebt(account: DraftDebtAccount) {
    requestRemoval({ id: account.id, name: account.account, type: 'debt' });
  }

  function requestRemoveIncomeSummaryItem(item: DraftIncomeSummaryItem) {
    requestRemoval({
      id: item.id,
      name: `${item.category} / ${item.interval}`,
      type: 'income-summary',
    });
  }

  function requestRemoveIncomeEvent(event: DraftIncomeEvent) {
    requestRemoval({ id: event.id, name: event.label, type: 'income' });
  }

  function requestRemoveImportantDate(importantDate: DraftImportantDate) {
    requestRemoval({
      id: importantDate.id,
      name: importantDate.event,
      type: 'important-date',
    });
  }

  function requestRemoval(removal: PendingRemoval) {
    dispatch({ removal, type: 'request-removal' });
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
      rentReserveAssetAccountId: draft?.projectionRoles.rentReserveAssetAccountId ?? 0,
      startAssetEdit: assetDraft.startAssetEdit,
      submitAsset: assetDraft.submitAsset,
      updateAssetForm: assetDraft.updateAssetForm,
    },
    buildSaveRequest,
    cancelRemoval: () => dispatch({ type: 'cancel-removal' }),
    confirmRemoval: () => dispatch({ type: 'confirm-removal' }),
    draftRevision: selectDraftRevision(draftState),
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
      primaryPaycheckIncomeSummaryItemId:
        draft?.projectionRoles.primaryPaycheckIncomeSummaryItemId ?? 0,
      requestRemoveIncomeSummaryItem,
      sourceIncomeSummaryItems: incomeSummaryDraft.sourceIncomeSummaryItems,
      startIncomeSummaryItemEdit: incomeSummaryDraft.startIncomeSummaryItemEdit,
      submitIncomeSummaryItem: incomeSummaryDraft.submitIncomeSummaryItem,
      updateIncomeSummaryForm: incomeSummaryDraft.updateIncomeSummaryForm,
    },
    isDirty: selectFinancialsDraftIsDirty(draftState),
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
      rentBillId: draft?.projectionRoles.rentBillId ?? 0,
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
    removalConfirmation: selectRemovalConfirmation(draftState),
    projection,
    projectionSettings: {
      assetAccounts: assetDraft.assetCategories.flatMap((category) =>
        category.accounts.map((account) => ({
          ...account,
          categoryLabel: category.label,
        }))
      ),
      bills: monthlyDraft.sortedBills,
      incomeSummaryItems: incomeSummaryDraft.sourceIncomeSummaryItems,
      planningSettings,
      roles: draft?.projectionRoles ?? null,
      updatePlanningSetting,
      updateProjectionRole,
    },
    resetDraft,
  };
}

export type FinancialsDraftWorkspace = ReturnType<typeof useFinancialsDraftWorkspace>;
