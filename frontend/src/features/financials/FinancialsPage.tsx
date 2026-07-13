import './FinancialsPage.css';

import { type FormEvent, useCallback, useEffect, useMemo, useState } from 'react';

import { financialsService } from '../../api/endpoints/financials';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { AnnualWithdrawalsTab } from './AnnualWithdrawalsTab';
import { AssetTable } from './AssetTable';
import { ConfirmRemoveModal } from './ConfirmRemoveModal';
import { DebtTab } from './DebtTab';
import {
  isPrimaryPaycheck,
  isRentReserveAccount,
  isRentWithdrawal,
  PRIMARY_PAYCHECK_CATEGORY,
  PRIMARY_PAYCHECK_INTERVAL,
} from './financialsAnchors';
import {
  buildDerivedIncomeSummaryItems,
  buildExpenseSnapshotRequest,
  createFinancialsDraft,
  defaultRecurringPaydayForm,
  emptyAnnualWithdrawalForm,
  emptyAssetForm,
  emptyForm,
  emptyImportantDateForm,
  emptyIncomeEventForm,
  emptyIncomeSummaryForm,
  formToAnnualWithdrawal,
  formToAssetAccount,
  formToDebtAccount,
  formToDraftBill,
  formToImportantDate,
  formToIncomeEvent,
  formToIncomeSummaryItem,
  generateRecurringPaydays,
  getCurrentPaycheck,
  getNextImportantDate,
  getTodayIso,
  isNumberedIncomeEventInYear,
  recalculateAssetCategory,
  removalItemType,
  toAnnualWithdrawalForm,
  toAssetForm,
  toDebtForm,
  toDraftAnnualWithdrawal,
  toDraftBill,
  toForm,
  toImportantDateForm,
  toIncomeEventForm,
  toIncomeSummaryForm,
  withImportantDateStatuses,
  withIncomeEventStatuses,
} from './financialsDraft';
import { buildProjectionSummary } from './financialsProjection';
import { fetchMonthlyExpenses, saveExpenseSnapshot } from './financialsSlice';
import type {
  AnnualWithdrawalFormState,
  AssetFormState,
  BillFormState,
  DraftAnnualWithdrawal,
  DraftAssetAccount,
  DraftAssetCategory,
  DraftBill,
  DraftDebtAccount,
  DraftImportantDate,
  DraftIncomeEvent,
  DraftIncomeSummaryItem,
  FinancialTab,
  ImportantDateFormState,
  IncomeEventFormState,
  IncomeSummaryFormState,
  PendingRemoval,
  ProjectionSummary,
  RecurringPaydayFormState,
} from './financialsTypes';
import { ImportantDatesTab } from './ImportantDatesTab';
import { IncomeCalendarTab } from './IncomeCalendarTab';
import { IncomeSummaryTab } from './IncomeSummaryTab';
import { MonthlyWithdrawalsTab } from './MonthlyWithdrawalsTab';
import { Overview } from './OverviewTab';
import { ProjectionTab } from './ProjectionTab';
import { SaveControls } from './SaveControls';

type FinancialsPageProps = {
  onSignOut?: () => void;
};

export default function FinancialsPage({ onSignOut }: FinancialsPageProps) {
  const dispatch = useAppDispatch();
  const { snapshot, status, saving, error } = useAppSelector((state) => state.financials);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<BillFormState>(emptyForm);
  const [draftBills, setDraftBills] = useState<DraftBill[]>([]);
  const [draftAnnualWithdrawals, setDraftAnnualWithdrawals] = useState<DraftAnnualWithdrawal[]>([]);
  const [editingAnnualWithdrawalId, setEditingAnnualWithdrawalId] = useState<number | null>(null);
  const [annualWithdrawalForm, setAnnualWithdrawalForm] =
    useState<AnnualWithdrawalFormState>(emptyAnnualWithdrawalForm);
  const [nextDraftAnnualWithdrawalId, setNextDraftAnnualWithdrawalId] = useState(-1);
  const [payPeriodStart, setPayPeriodStart] = useState('');
  const [payPeriodEnd, setPayPeriodEnd] = useState('');
  const [isDirty, setIsDirty] = useState(false);
  const [nextDraftId, setNextDraftId] = useState(-1);
  const [activeTab, setActiveTab] = useState<FinancialTab>('overview');
  const [draftAssetCategories, setDraftAssetCategories] = useState<DraftAssetCategory[]>([]);
  const [editingAsset, setEditingAsset] = useState<{ categoryKey: string; id: number } | null>(
    null
  );
  const [assetForm, setAssetForm] = useState<AssetFormState>(emptyAssetForm);
  const [nextDraftAssetId, setNextDraftAssetId] = useState(-1);
  const [draftDebtAccounts, setDraftDebtAccounts] = useState<DraftDebtAccount[]>([]);
  const [editingDebtId, setEditingDebtId] = useState<number | null>(null);
  const [debtForm, setDebtForm] = useState<AssetFormState>(emptyAssetForm);
  const [nextDraftDebtId, setNextDraftDebtId] = useState(-1);
  const [draftIncomeSummaryItems, setDraftIncomeSummaryItems] = useState<DraftIncomeSummaryItem[]>(
    []
  );
  const [incomeSummaryForm, setIncomeSummaryForm] =
    useState<IncomeSummaryFormState>(emptyIncomeSummaryForm);
  const [editingIncomeSummaryItemId, setEditingIncomeSummaryItemId] = useState<number | null>(null);
  const [nextDraftIncomeSummaryItemId, setNextDraftIncomeSummaryItemId] = useState(-1);
  const [draftIncomeEvents, setDraftIncomeEvents] = useState<DraftIncomeEvent[]>([]);
  const [editingIncomeEventId, setEditingIncomeEventId] = useState<number | null>(null);
  const [incomeEventForm, setIncomeEventForm] =
    useState<IncomeEventFormState>(emptyIncomeEventForm);
  const [recurringPaydayForm, setRecurringPaydayForm] = useState<RecurringPaydayFormState>(() =>
    defaultRecurringPaydayForm()
  );
  const [nextDraftIncomeEventId, setNextDraftIncomeEventId] = useState(-1);
  const [draftImportantDates, setDraftImportantDates] = useState<DraftImportantDate[]>([]);
  const [editingImportantDateId, setEditingImportantDateId] = useState<number | null>(null);
  const [importantDateForm, setImportantDateForm] =
    useState<ImportantDateFormState>(emptyImportantDateForm);
  const [nextDraftImportantDateId, setNextDraftImportantDateId] = useState(-1);
  const [pendingRemoval, setPendingRemoval] = useState<PendingRemoval | null>(null);
  const todayIso = useMemo(getTodayIso, []);
  const loadSnapshotDraft = useCallback((currentSnapshot: NonNullable<typeof snapshot>) => {
    const draft = createFinancialsDraft(currentSnapshot);

    setPayPeriodStart(draft.payPeriodStart);
    setPayPeriodEnd(draft.payPeriodEnd);
    setDraftBills(draft.draftBills);
    setDraftAnnualWithdrawals(draft.draftAnnualWithdrawals);
    setAnnualWithdrawalForm(draft.annualWithdrawalForm);
    setDraftAssetCategories(draft.draftAssetCategories);
    setDraftDebtAccounts(draft.draftDebtAccounts);
    setDraftIncomeSummaryItems(draft.draftIncomeSummaryItems);
    setIncomeSummaryForm(draft.incomeSummaryForm);
    setDraftIncomeEvents(draft.draftIncomeEvents);
    setDraftImportantDates(draft.draftImportantDates);
  }, []);

  useEffect(() => {
    dispatch(fetchMonthlyExpenses());
  }, [dispatch]);

  useEffect(() => {
    if (snapshot) {
      loadSnapshotDraft(snapshot);
      setEditingAnnualWithdrawalId(null);
      setIsDirty(false);
      setEditingId(null);
      setForm(emptyForm);
      setEditingAsset(null);
      setAssetForm(emptyAssetForm);
      setEditingDebtId(null);
      setDebtForm(emptyAssetForm);
      setEditingIncomeSummaryItemId(null);
      setEditingIncomeEventId(null);
      setIncomeEventForm(emptyIncomeEventForm);
      setRecurringPaydayForm(defaultRecurringPaydayForm(todayIso));
      setEditingImportantDateId(null);
      setImportantDateForm(emptyImportantDateForm);
      setPendingRemoval(null);
    }
  }, [loadSnapshotDraft, snapshot, todayIso]);

  useEffect(() => {
    if (payPeriodStart && payPeriodEnd) {
      setDraftBills((current) =>
        current.map((bill) => toDraftBill(bill, payPeriodStart, payPeriodEnd))
      );
      setDraftAnnualWithdrawals((current) =>
        current.map((withdrawal) =>
          toDraftAnnualWithdrawal(withdrawal, payPeriodStart, payPeriodEnd)
        )
      );
    }
  }, [payPeriodStart, payPeriodEnd]);

  const sortedBills = useMemo(
    () => [...draftBills].sort((left, right) => left.dueDay - right.dueDay),
    [draftBills]
  );
  const annualWithdrawals = useMemo(
    () =>
      [...draftAnnualWithdrawals].sort(
        (left, right) => left.month - right.month || left.day - right.day
      ),
    [draftAnnualWithdrawals]
  );

  const totals = useMemo(() => {
    const totalMonthlyExpenses = draftBills.reduce((total, bill) => total + bill.amount, 0);
    const paidTotal = draftBills
      .filter((bill) => bill.paid)
      .reduce((total, bill) => total + bill.amount, 0);
    const payPeriodTotal = draftBills
      .filter((bill) => bill.inPayPeriod)
      .reduce((total, bill) => total + bill.amount, 0);
    const totalAnnualWithdrawals = draftAnnualWithdrawals.reduce(
      (total, withdrawal) => total + withdrawal.amount,
      0
    );
    const annualPayPeriodTotal = draftAnnualWithdrawals
      .filter((withdrawal) => withdrawal.inPayPeriod)
      .reduce((total, withdrawal) => total + withdrawal.amount, 0);

    return {
      annualPayPeriodTotal,
      totalMonthlyExpenses,
      totalAnnualWithdrawals,
      paidTotal,
      unpaidTotal: totalMonthlyExpenses - paidTotal,
      payPeriodTotal,
    };
  }, [draftAnnualWithdrawals, draftBills]);

  const selectedBill = sortedBills.find((bill) => bill.id === editingId);
  const formTitle = selectedBill ? `Edit ${selectedBill.bill}` : 'Add Bill';
  const annualWithdrawalsInPayPeriod = annualWithdrawals.filter(
    (withdrawal) => withdrawal.inPayPeriod
  );
  const assetCategories = draftAssetCategories;
  const debtAccounts = useMemo(
    () => [...draftDebtAccounts].sort((left, right) => left.account.localeCompare(right.account)),
    [draftDebtAccounts]
  );
  const sourceIncomeSummaryItems = useMemo(
    () =>
      [...draftIncomeSummaryItems].sort(
        (left, right) =>
          left.category.localeCompare(right.category) || left.interval.localeCompare(right.interval)
      ),
    [draftIncomeSummaryItems]
  );
  const derivedIncomeSummaryItems = useMemo(
    () => buildDerivedIncomeSummaryItems(draftIncomeSummaryItems, totals.totalMonthlyExpenses),
    [draftIncomeSummaryItems, totals.totalMonthlyExpenses]
  );
  const primaryPaycheckIncome = derivedIncomeSummaryItems.find(isPrimaryPaycheck);
  const incomeEvents = useMemo(
    () => withIncomeEventStatuses(draftIncomeEvents, todayIso),
    [draftIncomeEvents, todayIso]
  );
  const currentPaycheck = useMemo(
    () => getCurrentPaycheck(incomeEvents, todayIso),
    [incomeEvents, todayIso]
  );
  const importantDates = useMemo(
    () => withImportantDateStatuses(draftImportantDates, todayIso),
    [draftImportantDates, todayIso]
  );
  const nextImportantDate = useMemo(
    () => getNextImportantDate(importantDates, todayIso),
    [importantDates, todayIso]
  );
  const totalTrackedAssets = assetCategories.reduce((total, category) => total + category.total, 0);
  const totalDebt = debtAccounts.reduce((total, account) => total + account.amount, 0);
  const netWorth = totalTrackedAssets - totalDebt;
  const retirement = assetCategories.find((category) => category.key === 'retirement');
  const investments = assetCategories.find((category) => category.key === 'investments');
  const cashSavings = assetCategories.find((category) => category.key === 'cash-savings');
  const insuranceBenefits = assetCategories.find(
    (category) => category.key === 'insurance-benefits'
  );
  const projection = useMemo<ProjectionSummary>(() => {
    const paycheckIncome = primaryPaycheckIncome?.amount ?? 0;
    return buildProjectionSummary({
      annualWithdrawals,
      annualWithdrawalsInPayPeriod,
      cashSavings,
      paycheckIncome,
      payPeriodEnd,
      payPeriodStart,
      sortedBills,
      totalDebt,
    });
  }, [
    annualWithdrawals,
    annualWithdrawalsInPayPeriod,
    cashSavings,
    payPeriodEnd,
    payPeriodStart,
    primaryPaycheckIncome,
    sortedBills,
    totalDebt,
  ]);
  const navigationSections: Array<{
    items: Array<[FinancialTab, string]>;
    label: string;
  }> = [
    {
      label: 'Snapshot',
      items: [
        ['overview', 'Overview'],
        ['projection', 'Projection'],
      ],
    },
    {
      label: 'Cash Flow',
      items: [
        ['monthly-withdrawals', 'Monthly Withdrawals'],
        ['annual-withdrawals', 'Annual Withdrawals'],
        ['income-summary', 'Income Summary'],
        ['income-calendar', 'Income Calendar'],
      ],
    },
    {
      label: 'Balance Sheet',
      items: [
        ['retirement', 'Retirement'],
        ['investments', 'Investments'],
        ['cash-savings', 'Cash & Savings'],
        ['insurance-benefits', 'Insurance / Benefits'],
        ['debt', 'Debt'],
      ],
    },
    {
      label: 'Calendar',
      items: [['important-dates', 'Important Dates']],
    },
  ];

  function updateForm<K extends keyof BillFormState>(key: K, value: BillFormState[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function submitBill(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (editingId) {
      setDraftBills((current) =>
        current.map((bill) =>
          bill.id === editingId
            ? formToDraftBill(
                editingId,
                isRentWithdrawal(bill) ? { ...form, bill: bill.bill } : form,
                payPeriodStart,
                payPeriodEnd
              )
            : bill
        )
      );
    } else {
      setDraftBills((current) => [
        ...current,
        formToDraftBill(nextDraftId, form, payPeriodStart, payPeriodEnd),
      ]);
      setNextDraftId((current) => current - 1);
    }

    setEditingId(null);
    setForm(emptyForm);
    setIsDirty(true);
  }

  async function saveDraft() {
    if (!snapshot) {
      return;
    }

    await dispatch(
      saveExpenseSnapshot(
        buildExpenseSnapshotRequest({
          annualWithdrawals,
          assetCategories,
          bills: sortedBills,
          debtAccounts,
          incomeEvents,
          incomeSummaryItems: draftIncomeSummaryItems,
          importantDates,
          payPeriodEnd,
          payPeriodStart,
          version: snapshot.version,
        })
      )
    );
  }

  async function exportBackup() {
    if (!snapshot) {
      return;
    }

    setExporting(true);
    setExportError(null);

    try {
      const blob = await financialsService.downloadSnapshotJson();
      downloadBlob(blob, `financial-snapshot-v${snapshot.version}.json`);
    } catch (unknownError) {
      setExportError(
        unknownError instanceof Error ? unknownError.message : 'Unable to export financial backup'
      );
    } finally {
      setExporting(false);
    }
  }

  function startEdit(bill: DraftBill) {
    setEditingId(bill.id);
    setForm(toForm(bill));
  }

  function cancelEdit() {
    setEditingId(null);
    setForm(emptyForm);
  }

  function requestRemoveBill(bill: DraftBill) {
    if (isRentWithdrawal(bill)) {
      return;
    }

    setPendingRemoval({ id: bill.id, name: bill.bill, type: 'bill' });
  }

  function removeBill(id: number) {
    setDraftBills((current) => current.filter((bill) => bill.id !== id || isRentWithdrawal(bill)));
    setIsDirty(true);
  }

  function updateAnnualWithdrawalForm<K extends keyof AnnualWithdrawalFormState>(
    key: K,
    value: AnnualWithdrawalFormState[K]
  ) {
    setAnnualWithdrawalForm((current) => ({ ...current, [key]: value }));
  }

  function submitAnnualWithdrawal(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (editingAnnualWithdrawalId) {
      setDraftAnnualWithdrawals((current) =>
        current.map((withdrawal) =>
          withdrawal.id === editingAnnualWithdrawalId
            ? formToAnnualWithdrawal(
                editingAnnualWithdrawalId,
                annualWithdrawalForm,
                payPeriodStart,
                payPeriodEnd
              )
            : withdrawal
        )
      );
    } else {
      setDraftAnnualWithdrawals((current) => [
        ...current,
        formToAnnualWithdrawal(
          nextDraftAnnualWithdrawalId,
          annualWithdrawalForm,
          payPeriodStart,
          payPeriodEnd
        ),
      ]);
      setNextDraftAnnualWithdrawalId((current) => current - 1);
    }

    cancelAnnualWithdrawalEdit();
    setIsDirty(true);
  }

  function startAnnualWithdrawalEdit(withdrawal: DraftAnnualWithdrawal) {
    setEditingAnnualWithdrawalId(withdrawal.id);
    setAnnualWithdrawalForm(toAnnualWithdrawalForm(withdrawal));
  }

  function cancelAnnualWithdrawalEdit() {
    setEditingAnnualWithdrawalId(null);
    setAnnualWithdrawalForm(emptyAnnualWithdrawalForm);
  }

  function requestRemoveAnnualWithdrawal(withdrawal: DraftAnnualWithdrawal) {
    setPendingRemoval({ id: withdrawal.id, name: withdrawal.bill, type: 'annual-withdrawal' });
  }

  function removeAnnualWithdrawal(id: number) {
    setDraftAnnualWithdrawals((current) => current.filter((withdrawal) => withdrawal.id !== id));
    setIsDirty(true);
  }

  function resetDraft() {
    if (!snapshot) {
      return;
    }

    loadSnapshotDraft(snapshot);
    cancelEdit();
    cancelAnnualWithdrawalEdit();
    cancelAssetEdit();
    cancelDebtEdit();
    cancelIncomeSummaryItemEdit();
    cancelIncomeEventEdit();
    cancelImportantDateEdit();
    setPendingRemoval(null);
    setIsDirty(false);
  }

  function updatePayPeriodStart(value: string) {
    setPayPeriodStart(value);
    setIsDirty(true);
  }

  function updatePayPeriodEnd(value: string) {
    setPayPeriodEnd(value);
    setIsDirty(true);
  }

  function updateAssetForm<K extends keyof AssetFormState>(key: K, value: AssetFormState[K]) {
    setAssetForm((current) => ({ ...current, [key]: value }));
  }

  function submitAsset(categoryKey: string, event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    setDraftAssetCategories((current) =>
      current.map((category) => {
        if (category.key !== categoryKey) {
          return category;
        }

        if (editingAsset?.categoryKey === categoryKey) {
          return recalculateAssetCategory({
            ...category,
            accounts: category.accounts.map((account) =>
              account.id === editingAsset.id
                ? formToAssetAccount(
                    account.id,
                    isRentReserveAccount(account)
                      ? { ...assetForm, account: account.account }
                      : assetForm
                  )
                : account
            ),
          });
        }

        return recalculateAssetCategory({
          ...category,
          accounts: [...category.accounts, formToAssetAccount(nextDraftAssetId, assetForm)],
        });
      })
    );

    if (!editingAsset) {
      setNextDraftAssetId((current) => current - 1);
    }
    cancelAssetEdit();
    setIsDirty(true);
  }

  function startAssetEdit(categoryKey: string, account: DraftAssetAccount) {
    setEditingAsset({ categoryKey, id: account.id });
    setAssetForm(toAssetForm(account));
  }

  function cancelAssetEdit() {
    setEditingAsset(null);
    setAssetForm(emptyAssetForm);
  }

  function requestRemoveAsset(categoryKey: string, account: DraftAssetAccount) {
    if (isRentReserveAccount(account)) {
      return;
    }

    setPendingRemoval({
      categoryKey,
      id: account.id,
      name: account.account,
      type: 'asset',
    });
  }

  function removeAsset(categoryKey: string, id: number) {
    setDraftAssetCategories((current) =>
      current.map((category) =>
        category.key === categoryKey
          ? recalculateAssetCategory({
              ...category,
              accounts: category.accounts.filter(
                (account) => account.id !== id || isRentReserveAccount(account)
              ),
            })
          : category
      )
    );
    setIsDirty(true);
  }

  function updateDebtForm<K extends keyof AssetFormState>(key: K, value: AssetFormState[K]) {
    setDebtForm((current) => ({ ...current, [key]: value }));
  }

  function submitDebt(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (editingDebtId) {
      setDraftDebtAccounts((current) =>
        current.map((account) =>
          account.id === editingDebtId ? formToDebtAccount(editingDebtId, debtForm) : account
        )
      );
    } else {
      setDraftDebtAccounts((current) => [...current, formToDebtAccount(nextDraftDebtId, debtForm)]);
      setNextDraftDebtId((current) => current - 1);
    }

    cancelDebtEdit();
    setIsDirty(true);
  }

  function startDebtEdit(account: DraftDebtAccount) {
    setEditingDebtId(account.id);
    setDebtForm(toDebtForm(account));
  }

  function cancelDebtEdit() {
    setEditingDebtId(null);
    setDebtForm(emptyAssetForm);
  }

  function requestRemoveDebt(account: DraftDebtAccount) {
    setPendingRemoval({ id: account.id, name: account.account, type: 'debt' });
  }

  function removeDebt(id: number) {
    setDraftDebtAccounts((current) => current.filter((account) => account.id !== id));
    setIsDirty(true);
  }

  function updateIncomeSummaryForm<K extends keyof IncomeSummaryFormState>(
    key: K,
    value: IncomeSummaryFormState[K]
  ) {
    setIncomeSummaryForm((current) => ({ ...current, [key]: value }));
  }

  function submitIncomeSummaryItem(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const editingSource =
      editingIncomeSummaryItemId === null
        ? null
        : draftIncomeSummaryItems.find((item) => item.id === editingIncomeSummaryItemId);
    const sourceForm: IncomeSummaryFormState =
      editingSource && isPrimaryPaycheck(editingSource)
        ? {
            ...incomeSummaryForm,
            category: PRIMARY_PAYCHECK_CATEGORY,
            interval: PRIMARY_PAYCHECK_INTERVAL,
          }
        : {
            ...incomeSummaryForm,
            category: incomeSummaryForm.category.trim(),
            interval: incomeSummaryForm.interval.trim(),
          };
    const matchingSource =
      editingIncomeSummaryItemId === null
        ? draftIncomeSummaryItems.find((item) => incomeSummarySourceMatches(item, sourceForm))
        : null;
    const targetId =
      editingIncomeSummaryItemId ?? matchingSource?.id ?? nextDraftIncomeSummaryItemId;
    const updatedSource = formToIncomeSummaryItem(targetId, sourceForm);
    const hasExistingSource = draftIncomeSummaryItems.some((item) => item.id === targetId);
    const nextIncomeSummaryItems = hasExistingSource
      ? draftIncomeSummaryItems.map((item) => (item.id === targetId ? updatedSource : item))
      : [...draftIncomeSummaryItems, updatedSource];

    setDraftIncomeSummaryItems(nextIncomeSummaryItems);
    if (!hasExistingSource) {
      setNextDraftIncomeSummaryItemId((current) => current - 1);
    }
    setEditingIncomeSummaryItemId(null);
    setIncomeSummaryForm(defaultIncomeSummaryForm(nextIncomeSummaryItems));
    setIsDirty(true);
  }

  function startIncomeSummaryItemEdit(item: DraftIncomeSummaryItem) {
    setEditingIncomeSummaryItemId(item.id);
    setIncomeSummaryForm(toIncomeSummaryForm(item));
  }

  function cancelIncomeSummaryItemEdit() {
    setEditingIncomeSummaryItemId(null);
    setIncomeSummaryForm(defaultIncomeSummaryForm(draftIncomeSummaryItems));
  }

  function requestRemoveIncomeSummaryItem(item: DraftIncomeSummaryItem) {
    if (isPrimaryPaycheck(item)) {
      return;
    }

    setPendingRemoval({
      id: item.id,
      name: `${item.category} / ${item.interval}`,
      type: 'income-summary',
    });
  }

  function removeIncomeSummaryItem(id: number) {
    setDraftIncomeSummaryItems((current) =>
      current.filter((item) => item.id !== id || isPrimaryPaycheck(item))
    );
    if (editingIncomeSummaryItemId === id) {
      cancelIncomeSummaryItemEdit();
    }
    setIsDirty(true);
  }

  function updateIncomeEventForm<K extends keyof IncomeEventFormState>(
    key: K,
    value: IncomeEventFormState[K]
  ) {
    setIncomeEventForm((current) => ({ ...current, [key]: value }));
  }

  function updateRecurringPaydayForm<K extends keyof RecurringPaydayFormState>(
    key: K,
    value: RecurringPaydayFormState[K]
  ) {
    setRecurringPaydayForm((current) => {
      const next = { ...current, [key]: value };
      return key === 'year' ? { ...next, firstPayDate: '' } : next;
    });
  }

  function submitIncomeEvent(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (editingIncomeEventId) {
      setDraftIncomeEvents((current) =>
        current.map((incomeEvent) =>
          incomeEvent.id === editingIncomeEventId
            ? formToIncomeEvent(editingIncomeEventId, incomeEventForm)
            : incomeEvent
        )
      );
    } else {
      setDraftIncomeEvents((current) => [
        ...current,
        formToIncomeEvent(nextDraftIncomeEventId, incomeEventForm),
      ]);
      setNextDraftIncomeEventId((current) => current - 1);
    }

    cancelIncomeEventEdit();
    setIsDirty(true);
  }

  function submitRecurringPaydays(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const generatedPaydays = generateRecurringPaydays(recurringPaydayForm, nextDraftIncomeEventId);
    if (generatedPaydays.length === 0) {
      return;
    }

    setDraftIncomeEvents((current) => {
      const retainedEvents = recurringPaydayForm.replaceExistingYear
        ? current.filter(
            (incomeEvent) => !isNumberedIncomeEventInYear(incomeEvent, recurringPaydayForm.year)
          )
        : current;
      return [...retainedEvents, ...generatedPaydays];
    });
    setNextDraftIncomeEventId((current) => current - generatedPaydays.length);
    setIsDirty(true);
  }

  function startIncomeEventEdit(event: DraftIncomeEvent) {
    setEditingIncomeEventId(event.id);
    setIncomeEventForm(toIncomeEventForm(event));
  }

  function cancelIncomeEventEdit() {
    setEditingIncomeEventId(null);
    setIncomeEventForm(emptyIncomeEventForm);
  }

  function requestRemoveIncomeEvent(event: DraftIncomeEvent) {
    setPendingRemoval({ id: event.id, name: event.label, type: 'income' });
  }

  function removeIncomeEvent(id: number) {
    setDraftIncomeEvents((current) => current.filter((event) => event.id !== id));
    setIsDirty(true);
  }

  function updateImportantDateForm<K extends keyof ImportantDateFormState>(
    key: K,
    value: ImportantDateFormState[K]
  ) {
    setImportantDateForm((current) => ({ ...current, [key]: value }));
  }

  function submitImportantDate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (editingImportantDateId) {
      setDraftImportantDates((current) =>
        current.map((importantDate) =>
          importantDate.id === editingImportantDateId
            ? formToImportantDate(editingImportantDateId, importantDateForm)
            : importantDate
        )
      );
    } else {
      setDraftImportantDates((current) => [
        ...current,
        formToImportantDate(nextDraftImportantDateId, importantDateForm),
      ]);
      setNextDraftImportantDateId((current) => current - 1);
    }

    cancelImportantDateEdit();
    setIsDirty(true);
  }

  function startImportantDateEdit(importantDate: DraftImportantDate) {
    setEditingImportantDateId(importantDate.id);
    setImportantDateForm(toImportantDateForm(importantDate));
  }

  function cancelImportantDateEdit() {
    setEditingImportantDateId(null);
    setImportantDateForm(emptyImportantDateForm);
  }

  function requestRemoveImportantDate(importantDate: DraftImportantDate) {
    setPendingRemoval({ id: importantDate.id, name: importantDate.event, type: 'important-date' });
  }

  function removeImportantDate(id: number) {
    setDraftImportantDates((current) => current.filter((importantDate) => importantDate.id !== id));
    setIsDirty(true);
  }

  function confirmRemoval() {
    if (!pendingRemoval) {
      return;
    }

    if (pendingRemoval.type === 'bill') {
      removeBill(pendingRemoval.id);
    } else if (pendingRemoval.type === 'annual-withdrawal') {
      removeAnnualWithdrawal(pendingRemoval.id);
    } else if (pendingRemoval.type === 'asset') {
      removeAsset(pendingRemoval.categoryKey, pendingRemoval.id);
    } else if (pendingRemoval.type === 'debt') {
      removeDebt(pendingRemoval.id);
    } else if (pendingRemoval.type === 'income-summary') {
      removeIncomeSummaryItem(pendingRemoval.id);
    } else if (pendingRemoval.type === 'income') {
      removeIncomeEvent(pendingRemoval.id);
    } else {
      removeImportantDate(pendingRemoval.id);
    }

    setPendingRemoval(null);
  }

  return (
    <main className="expenses-shell">
      <header className="app-header">
        <div>
          <p className="eyebrow">Personal finance</p>
          <h1>Financials</h1>
        </div>
        <div className="header-actions">
          {snapshot && (
            <SaveControls
              exporting={exporting}
              isDirty={isDirty}
              onExport={() => void exportBackup()}
              onReset={resetDraft}
              onSave={() => void saveDraft()}
              saving={saving}
            />
          )}
          {onSignOut && (
            <button className="ghost" onClick={onSignOut} type="button">
              Sign Out
            </button>
          )}
        </div>
      </header>

      <div className="financials-layout">
        <aside className="sidebar" aria-label="Financial sections">
          {navigationSections.map((section) => (
            <div className="sidebar-section" key={section.label}>
              <p>{section.label}</p>
              <nav>
                {section.items.map(([tab, label]) => (
                  <button
                    aria-current={activeTab === tab ? 'page' : undefined}
                    className={activeTab === tab ? 'active' : undefined}
                    key={tab}
                    onClick={() => setActiveTab(tab)}
                    type="button"
                  >
                    {label}
                  </button>
                ))}
              </nav>
            </div>
          ))}
        </aside>

        <section className="financials-content">
          {isDirty && <p className="status">You have unsaved changes.</p>}
          {status === 'loading' && <p className="status">Loading financials...</p>}
          {error && <p className="error">{error}</p>}
          {exportError && <p className="error">{exportError}</p>}

          {snapshot && (
            <>
              {activeTab === 'overview' && (
                <Overview
                  annualTotal={totals.totalAnnualWithdrawals}
                  assetCategories={assetCategories}
                  currentPaycheck={currentPaycheck}
                  nextImportantDate={nextImportantDate}
                  netWorth={netWorth}
                  primaryPaycheckIncome={primaryPaycheckIncome?.amount}
                  projection={projection}
                  totalDebt={totalDebt}
                  totalTrackedAssets={totalTrackedAssets}
                  withdrawalTotal={totals.totalMonthlyExpenses}
                />
              )}

              {activeTab === 'projection' && <ProjectionTab projection={projection} />}

              {activeTab === 'monthly-withdrawals' && (
                <MonthlyWithdrawalsTab
                  annualPayPeriodTotal={totals.annualPayPeriodTotal}
                  annualWithdrawalsInPayPeriod={annualWithdrawalsInPayPeriod}
                  cancelEdit={cancelEdit}
                  editingId={editingId}
                  form={form}
                  formTitle={formTitle}
                  payPeriodEnd={payPeriodEnd}
                  payPeriodStart={payPeriodStart}
                  requestRemoveBill={requestRemoveBill}
                  sortedBills={sortedBills}
                  startEdit={startEdit}
                  submitBill={submitBill}
                  totals={totals}
                  updateForm={updateForm}
                  updatePayPeriodEnd={updatePayPeriodEnd}
                  updatePayPeriodStart={updatePayPeriodStart}
                />
              )}

              {activeTab === 'annual-withdrawals' && (
                <AnnualWithdrawalsTab
                  annualWithdrawalForm={annualWithdrawalForm}
                  annualWithdrawals={annualWithdrawals}
                  cancelAnnualWithdrawalEdit={cancelAnnualWithdrawalEdit}
                  editingAnnualWithdrawalId={editingAnnualWithdrawalId}
                  requestRemoveAnnualWithdrawal={requestRemoveAnnualWithdrawal}
                  startAnnualWithdrawalEdit={startAnnualWithdrawalEdit}
                  submitAnnualWithdrawal={submitAnnualWithdrawal}
                  totals={totals}
                  updateAnnualWithdrawalForm={updateAnnualWithdrawalForm}
                />
              )}

              {activeTab === 'income-summary' && (
                <IncomeSummaryTab
                  cancelIncomeSummaryItemEdit={cancelIncomeSummaryItemEdit}
                  derivedIncomeSummaryItems={derivedIncomeSummaryItems}
                  editingIncomeSummaryItemId={editingIncomeSummaryItemId}
                  incomeSummaryForm={incomeSummaryForm}
                  requestRemoveIncomeSummaryItem={requestRemoveIncomeSummaryItem}
                  sourceIncomeSummaryItems={sourceIncomeSummaryItems}
                  startIncomeSummaryItemEdit={startIncomeSummaryItemEdit}
                  submitIncomeSummaryItem={submitIncomeSummaryItem}
                  updateIncomeSummaryForm={updateIncomeSummaryForm}
                />
              )}

              {activeTab === 'income-calendar' && (
                <IncomeCalendarTab
                  cancelIncomeEventEdit={cancelIncomeEventEdit}
                  editingIncomeEventId={editingIncomeEventId}
                  incomeEventForm={incomeEventForm}
                  incomeEvents={incomeEvents}
                  recurringPaydayForm={recurringPaydayForm}
                  requestRemoveIncomeEvent={requestRemoveIncomeEvent}
                  startIncomeEventEdit={startIncomeEventEdit}
                  submitRecurringPaydays={submitRecurringPaydays}
                  submitIncomeEvent={submitIncomeEvent}
                  updateIncomeEventForm={updateIncomeEventForm}
                  updateRecurringPaydayForm={updateRecurringPaydayForm}
                />
              )}

              {activeTab === 'retirement' && retirement && (
                <AssetTable
                  assetForm={assetForm}
                  cancelAssetEdit={cancelAssetEdit}
                  category={retirement}
                  editingAsset={editingAsset}
                  requestRemoveAsset={requestRemoveAsset}
                  startAssetEdit={startAssetEdit}
                  submitAsset={submitAsset}
                  updateAssetForm={updateAssetForm}
                />
              )}
              {activeTab === 'investments' && investments && (
                <AssetTable
                  assetForm={assetForm}
                  cancelAssetEdit={cancelAssetEdit}
                  category={investments}
                  editingAsset={editingAsset}
                  requestRemoveAsset={requestRemoveAsset}
                  startAssetEdit={startAssetEdit}
                  submitAsset={submitAsset}
                  updateAssetForm={updateAssetForm}
                />
              )}
              {activeTab === 'cash-savings' && cashSavings && (
                <AssetTable
                  assetForm={assetForm}
                  cancelAssetEdit={cancelAssetEdit}
                  category={cashSavings}
                  editingAsset={editingAsset}
                  requestRemoveAsset={requestRemoveAsset}
                  startAssetEdit={startAssetEdit}
                  submitAsset={submitAsset}
                  updateAssetForm={updateAssetForm}
                />
              )}
              {activeTab === 'insurance-benefits' && insuranceBenefits && (
                <AssetTable
                  assetForm={assetForm}
                  cancelAssetEdit={cancelAssetEdit}
                  category={insuranceBenefits}
                  editingAsset={editingAsset}
                  requestRemoveAsset={requestRemoveAsset}
                  startAssetEdit={startAssetEdit}
                  submitAsset={submitAsset}
                  updateAssetForm={updateAssetForm}
                />
              )}

              {activeTab === 'debt' && (
                <DebtTab
                  cancelDebtEdit={cancelDebtEdit}
                  debtAccounts={debtAccounts}
                  debtForm={debtForm}
                  editingDebtId={editingDebtId}
                  requestRemoveDebt={requestRemoveDebt}
                  startDebtEdit={startDebtEdit}
                  submitDebt={submitDebt}
                  totalDebt={totalDebt}
                  updateDebtForm={updateDebtForm}
                />
              )}

              {activeTab === 'important-dates' && (
                <ImportantDatesTab
                  cancelImportantDateEdit={cancelImportantDateEdit}
                  editingImportantDateId={editingImportantDateId}
                  importantDateForm={importantDateForm}
                  importantDates={importantDates}
                  requestRemoveImportantDate={requestRemoveImportantDate}
                  startImportantDateEdit={startImportantDateEdit}
                  submitImportantDate={submitImportantDate}
                  updateImportantDateForm={updateImportantDateForm}
                />
              )}
            </>
          )}
        </section>
      </div>

      {pendingRemoval && (
        <ConfirmRemoveModal
          itemName={pendingRemoval.name}
          itemType={removalItemType(pendingRemoval)}
          onCancel={() => setPendingRemoval(null)}
          onConfirm={confirmRemoval}
        />
      )}
    </main>
  );
}

function defaultIncomeSummaryForm(items: DraftIncomeSummaryItem[]): IncomeSummaryFormState {
  const primaryPaycheck = items.find(isPrimaryPaycheck);
  return primaryPaycheck ? toIncomeSummaryForm(primaryPaycheck) : emptyIncomeSummaryForm;
}

function incomeSummarySourceMatches(
  item: Pick<DraftIncomeSummaryItem, 'category' | 'interval'>,
  form: Pick<IncomeSummaryFormState, 'category' | 'interval'>
) {
  return (
    item.category.trim().toLowerCase() === form.category.trim().toLowerCase() &&
    item.interval.trim().toLowerCase() === form.interval.trim().toLowerCase()
  );
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
