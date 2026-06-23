import './FinancialsPage.css';

import { type FormEvent, useEffect, useMemo, useState } from 'react';

import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { AnnualWithdrawalsTab } from './AnnualWithdrawalsTab';
import { AssetTable } from './AssetTable';
import { ConfirmRemoveModal } from './ConfirmRemoveModal';
import { DebtTab } from './DebtTab';
import {
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
  getCurrentPaycheck,
  getNextImportantDate,
  getTodayIso,
  recalculateAssetCategory,
  removalItemType,
  toAnnualWithdrawalForm,
  toAssetForm,
  toDebtForm,
  toDraftAnnualWithdrawal,
  toDraftAssetCategory,
  toDraftBill,
  toForm,
  toImportantDateForm,
  toIncomeEventForm,
  toIncomeSummaryForm,
  toSnapshotAnnualWithdrawal,
  toSnapshotBill,
  toSnapshotCategory,
  toSnapshotDebtAccount,
  toSnapshotImportantDate,
  toSnapshotIncomeEvent,
  toSnapshotIncomeSummaryItem,
  withImportantDateStatuses,
  withIncomeEventStatuses,
  withIncomeMonthlyCounts,
} from './financialsDraft';
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
  ProjectionPeriod,
  ProjectionSummary,
} from './financialsTypes';
import { ImportantDatesTab } from './ImportantDatesTab';
import { IncomeCalendarTab } from './IncomeCalendarTab';
import { IncomeSummaryTab } from './IncomeSummaryTab';
import { MonthlyWithdrawalsTab } from './MonthlyWithdrawalsTab';
import { Overview } from './OverviewTab';
import { ProjectionTab } from './ProjectionTab';
import { SaveControls } from './SaveControls';

export default function FinancialsPage() {
  const dispatch = useAppDispatch();
  const { snapshot, status, saving, error } = useAppSelector((state) => state.financials);
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
  const [editingIncomeSummaryId, setEditingIncomeSummaryId] = useState<number | null>(null);
  const [incomeSummaryForm, setIncomeSummaryForm] =
    useState<IncomeSummaryFormState>(emptyIncomeSummaryForm);
  const [nextDraftIncomeSummaryId, setNextDraftIncomeSummaryId] = useState(-1);
  const [draftIncomeEvents, setDraftIncomeEvents] = useState<DraftIncomeEvent[]>([]);
  const [editingIncomeEventId, setEditingIncomeEventId] = useState<number | null>(null);
  const [incomeEventForm, setIncomeEventForm] =
    useState<IncomeEventFormState>(emptyIncomeEventForm);
  const [nextDraftIncomeEventId, setNextDraftIncomeEventId] = useState(-1);
  const [draftImportantDates, setDraftImportantDates] = useState<DraftImportantDate[]>([]);
  const [editingImportantDateId, setEditingImportantDateId] = useState<number | null>(null);
  const [importantDateForm, setImportantDateForm] =
    useState<ImportantDateFormState>(emptyImportantDateForm);
  const [nextDraftImportantDateId, setNextDraftImportantDateId] = useState(-1);
  const [pendingRemoval, setPendingRemoval] = useState<PendingRemoval | null>(null);
  const todayIso = useMemo(getTodayIso, []);

  useEffect(() => {
    dispatch(fetchMonthlyExpenses());
  }, [dispatch]);

  useEffect(() => {
    if (snapshot) {
      setPayPeriodStart(snapshot.payPeriodStart);
      setPayPeriodEnd(snapshot.payPeriodEnd);
      setDraftBills(
        snapshot.bills.map((bill) =>
          toDraftBill(bill, snapshot.payPeriodStart, snapshot.payPeriodEnd)
        )
      );
      setDraftAnnualWithdrawals(
        (snapshot.annualWithdrawals ?? []).map((withdrawal) =>
          toDraftAnnualWithdrawal(withdrawal, snapshot.payPeriodStart, snapshot.payPeriodEnd)
        )
      );
      setEditingAnnualWithdrawalId(null);
      setAnnualWithdrawalForm(emptyAnnualWithdrawalForm);
      setIsDirty(false);
      setEditingId(null);
      setForm(emptyForm);
      setDraftAssetCategories(snapshot.assetCategories.map(toDraftAssetCategory));
      setEditingAsset(null);
      setAssetForm(emptyAssetForm);
      setDraftDebtAccounts((snapshot.debtAccounts ?? []).map((account) => ({ ...account })));
      setEditingDebtId(null);
      setDebtForm(emptyAssetForm);
      setDraftIncomeSummaryItems((snapshot.incomeSummaryItems ?? []).map((item) => ({ ...item })));
      setEditingIncomeSummaryId(null);
      setIncomeSummaryForm(emptyIncomeSummaryForm);
      setDraftIncomeEvents(
        withIncomeMonthlyCounts((snapshot.incomeEvents ?? []).map((event) => ({ ...event })))
      );
      setEditingIncomeEventId(null);
      setIncomeEventForm(emptyIncomeEventForm);
      setDraftImportantDates(
        (snapshot.importantDates ?? []).map((importantDate) => ({ ...importantDate }))
      );
      setEditingImportantDateId(null);
      setImportantDateForm(emptyImportantDateForm);
      setPendingRemoval(null);
    }
  }, [snapshot]);

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
  const incomeSummaryItems = useMemo(
    () =>
      [...draftIncomeSummaryItems].sort(
        (left, right) =>
          left.category.localeCompare(right.category) || left.interval.localeCompare(right.interval)
      ),
    [draftIncomeSummaryItems]
  );
  const biWeeklyDisposableIncome = incomeSummaryItems.find(
    (item) => item.category === 'Disposable Income' && item.interval === 'Bi-Weekly'
  );
  const biWeeklyNetIncome = incomeSummaryItems.find(
    (item) => item.category === 'Net Income' && item.interval === 'Bi-Weekly'
  );
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
    const rentBill = sortedBills.find((bill) => bill.bill.toLowerCase().includes('rent'));
    const rentSavingsBalance =
      cashSavings?.accounts.find((account) => account.account.toLowerCase().includes('rent'))
        ?.amount ?? 0;
    const paycheckIncome = biWeeklyNetIncome?.amount ?? biWeeklyDisposableIncome?.amount ?? 0;
    const currentPeriod = buildProjectionPeriod(
      'Current Pay Period',
      payPeriodStart,
      payPeriodEnd,
      sortedBills,
      annualWithdrawalsInPayPeriod,
      paycheckIncome,
      rentBill,
      rentSavingsBalance
    );
    const nextPeriodDates = nextPayPeriod(payPeriodStart, payPeriodEnd);
    const nextBills = sortedBills.map((bill) =>
      toDraftBill(bill, nextPeriodDates.start, nextPeriodDates.end)
    );
    const nextAnnualWithdrawals = annualWithdrawals.map((withdrawal) =>
      toDraftAnnualWithdrawal(withdrawal, nextPeriodDates.start, nextPeriodDates.end)
    );
    const nextPeriod = buildProjectionPeriod(
      'Next Pay Period',
      nextPeriodDates.start,
      nextPeriodDates.end,
      nextBills,
      nextAnnualWithdrawals.filter((withdrawal) => withdrawal.inPayPeriod),
      paycheckIncome,
      rentBill,
      currentPeriod.endingRentSavings
    );
    const periods = [currentPeriod.period, nextPeriod.period];
    const nextPayPeriodCashAfterBills = nextPeriod.period.projectedBeforeDebt;
    const nextPayPeriodDebtPayment = Math.min(Math.max(nextPayPeriodCashAfterBills, 0), totalDebt);
    const nextPayPeriodDebtRemaining = Math.max(totalDebt - nextPayPeriodDebtPayment, 0);
    const nextPayPeriodHysaTransfer = Math.max(nextPayPeriodCashAfterBills - totalDebt, 0);

    return {
      currentDebt: totalDebt,
      debtCoveredByProjectedCash: nextPayPeriodDebtPayment,
      debtCoveragePercent: totalDebt === 0 ? 100 : (nextPayPeriodDebtPayment / totalDebt) * 100,
      nextPayPeriodCashAfterBills,
      nextPayPeriodDebtPayment,
      nextPayPeriodDebtRemaining,
      nextPayPeriodHysaTransfer,
      projectedAfterDebt: nextPayPeriodCashAfterBills - totalDebt,
      projectedBeforeDebt: nextPayPeriodCashAfterBills,
      remainingDebtAfterProjectedCash: nextPayPeriodDebtRemaining,
      periods,
    };
  }, [
    annualWithdrawals,
    annualWithdrawalsInPayPeriod,
    biWeeklyDisposableIncome,
    biWeeklyNetIncome,
    cashSavings,
    payPeriodEnd,
    payPeriodStart,
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
            ? formToDraftBill(editingId, form, payPeriodStart, payPeriodEnd)
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
    await dispatch(
      saveExpenseSnapshot({
        payPeriodStart,
        payPeriodEnd,
        bills: sortedBills.map(toSnapshotBill),
        annualWithdrawals: annualWithdrawals.map(toSnapshotAnnualWithdrawal),
        assetCategories: assetCategories.map(toSnapshotCategory),
        debtAccounts: debtAccounts.map(toSnapshotDebtAccount),
        incomeSummaryItems: incomeSummaryItems.map(toSnapshotIncomeSummaryItem),
        incomeEvents: incomeEvents.map(toSnapshotIncomeEvent),
        importantDates: importantDates.map(toSnapshotImportantDate),
      })
    );
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
    setPendingRemoval({ id: bill.id, name: bill.bill, type: 'bill' });
  }

  function removeBill(id: number) {
    setDraftBills((current) => current.filter((bill) => bill.id !== id));
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

    setPayPeriodStart(snapshot.payPeriodStart);
    setPayPeriodEnd(snapshot.payPeriodEnd);
    setDraftBills(
      snapshot.bills.map((bill) =>
        toDraftBill(bill, snapshot.payPeriodStart, snapshot.payPeriodEnd)
      )
    );
    setDraftAnnualWithdrawals(
      (snapshot.annualWithdrawals ?? []).map((withdrawal) =>
        toDraftAnnualWithdrawal(withdrawal, snapshot.payPeriodStart, snapshot.payPeriodEnd)
      )
    );
    setDraftAssetCategories(snapshot.assetCategories.map(toDraftAssetCategory));
    setDraftDebtAccounts((snapshot.debtAccounts ?? []).map((account) => ({ ...account })));
    setDraftIncomeSummaryItems((snapshot.incomeSummaryItems ?? []).map((item) => ({ ...item })));
    setDraftIncomeEvents(
      withIncomeMonthlyCounts((snapshot.incomeEvents ?? []).map((event) => ({ ...event })))
    );
    setDraftImportantDates(
      (snapshot.importantDates ?? []).map((importantDate) => ({ ...importantDate }))
    );
    cancelEdit();
    cancelAnnualWithdrawalEdit();
    cancelAssetEdit();
    cancelDebtEdit();
    cancelIncomeSummaryEdit();
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
              account.id === editingAsset.id ? formToAssetAccount(account.id, assetForm) : account
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
              accounts: category.accounts.filter((account) => account.id !== id),
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

    if (editingIncomeSummaryId) {
      setDraftIncomeSummaryItems((current) =>
        current.map((item) =>
          item.id === editingIncomeSummaryId
            ? formToIncomeSummaryItem(editingIncomeSummaryId, incomeSummaryForm)
            : item
        )
      );
    } else {
      setDraftIncomeSummaryItems((current) => [
        ...current,
        formToIncomeSummaryItem(nextDraftIncomeSummaryId, incomeSummaryForm),
      ]);
      setNextDraftIncomeSummaryId((current) => current - 1);
    }

    cancelIncomeSummaryEdit();
    setIsDirty(true);
  }

  function startIncomeSummaryEdit(item: DraftIncomeSummaryItem) {
    setEditingIncomeSummaryId(item.id);
    setIncomeSummaryForm(toIncomeSummaryForm(item));
  }

  function cancelIncomeSummaryEdit() {
    setEditingIncomeSummaryId(null);
    setIncomeSummaryForm(emptyIncomeSummaryForm);
  }

  function requestRemoveIncomeSummaryItem(item: DraftIncomeSummaryItem) {
    setPendingRemoval({
      id: item.id,
      name: `${item.category} ${item.interval}`,
      type: 'income-summary',
    });
  }

  function removeIncomeSummaryItem(id: number) {
    setDraftIncomeSummaryItems((current) => current.filter((item) => item.id !== id));
    setIsDirty(true);
  }

  function updateIncomeEventForm<K extends keyof IncomeEventFormState>(
    key: K,
    value: IncomeEventFormState[K]
  ) {
    setIncomeEventForm((current) => ({ ...current, [key]: value }));
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
        {snapshot && (
          <SaveControls
            isDirty={isDirty}
            onReset={resetDraft}
            onSave={() => void saveDraft()}
            saving={saving}
          />
        )}
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

          {snapshot && (
            <>
              {activeTab === 'overview' && (
                <Overview
                  annualTotal={totals.totalAnnualWithdrawals}
                  assetCategories={assetCategories}
                  biWeeklyDisposableIncome={biWeeklyDisposableIncome?.amount}
                  currentPaycheck={currentPaycheck}
                  nextImportantDate={nextImportantDate}
                  netWorth={netWorth}
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
                  cancelIncomeSummaryEdit={cancelIncomeSummaryEdit}
                  editingIncomeSummaryId={editingIncomeSummaryId}
                  incomeSummaryForm={incomeSummaryForm}
                  incomeSummaryItems={incomeSummaryItems}
                  requestRemoveIncomeSummaryItem={requestRemoveIncomeSummaryItem}
                  startIncomeSummaryEdit={startIncomeSummaryEdit}
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
                  requestRemoveIncomeEvent={requestRemoveIncomeEvent}
                  startIncomeEventEdit={startIncomeEventEdit}
                  submitIncomeEvent={submitIncomeEvent}
                  updateIncomeEventForm={updateIncomeEventForm}
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

function buildProjectionPeriod(
  title: string,
  payPeriodStart: string,
  payPeriodEnd: string,
  bills: DraftBill[],
  annualWithdrawals: DraftAnnualWithdrawal[],
  paycheckIncome: number,
  rentBill: DraftBill | undefined,
  startingRentSavings: number
): { endingRentSavings: number; period: ProjectionPeriod } {
  const rentBillAmount = rentBill?.amount ?? 0;
  const rentDueInPeriod = bills.some((bill) => bill.id === rentBill?.id && bill.inPayPeriod)
    ? rentBillAmount
    : 0;
  const rentCoveredBySavings = Math.min(startingRentSavings, rentDueInPeriod);
  const rentSavingsAfterDue = Math.max(startingRentSavings - rentDueInPeriod, 0);
  const rentRemainingNeed = Math.max(rentBillAmount - rentSavingsAfterDue, 0);
  const rentContribution = Math.min(rentBillAmount / 2, rentRemainingNeed);
  const projectedBills = bills.filter((bill) => bill.inPayPeriod && bill.id !== rentBill?.id);
  const monthlyWithdrawalsDue = projectedBills.reduce((total, bill) => total + bill.amount, 0);
  const annualWithdrawalsDue = annualWithdrawals.reduce(
    (total, withdrawal) => total + withdrawal.amount,
    0
  );
  const projectedBeforeDebt =
    paycheckIncome - monthlyWithdrawalsDue - annualWithdrawalsDue - rentContribution;

  return {
    endingRentSavings: rentSavingsAfterDue + rentContribution,
    period: {
      annualWithdrawalsDue,
      monthlyWithdrawalsDue,
      paycheckIncome,
      payPeriodEnd,
      payPeriodStart,
      projectedBeforeDebt,
      rentBillAmount,
      rentContribution,
      rentCoveredBySavings,
      rentRemainingNeed,
      rentSavingsBalance: startingRentSavings,
      title,
      withdrawalLines: projectedBills.map((bill) => ({
        amount: bill.amount,
        label: bill.bill,
      })),
    },
  };
}

function nextPayPeriod(payPeriodStart: string, payPeriodEnd: string) {
  if (!payPeriodStart || !payPeriodEnd) {
    return { end: payPeriodEnd, start: payPeriodStart };
  }

  const start = new Date(`${payPeriodStart}T00:00:00`);
  const end = new Date(`${payPeriodEnd}T00:00:00`);
  const periodDays = Math.round((end.getTime() - start.getTime()) / 86_400_000) + 1;
  const nextStart = addDays(end, 1);
  const nextEnd = addDays(nextStart, periodDays - 1);

  return { end: toIsoDate(nextEnd), start: toIsoDate(nextStart) };
}

function addDays(date: Date, days: number) {
  const nextDate = new Date(date);
  nextDate.setDate(nextDate.getDate() + days);
  return nextDate;
}

function toIsoDate(date: Date) {
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 10);
}
