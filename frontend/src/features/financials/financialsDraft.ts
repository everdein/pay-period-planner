import type {
  AnnualWithdrawal,
  AnnualWithdrawalSnapshotRequest,
  AssetCategory,
  AssetCategorySnapshotRequest,
  DebtAccountSnapshotRequest,
  ExpenseBill,
  ExpenseBillSnapshotRequest,
  ExpenseSnapshot,
  ExpenseSnapshotRequest,
  ImportantDateSnapshotRequest,
  IncomeEventSnapshotRequest,
  IncomeSummaryItemSnapshotRequest,
} from '../../api/endpoints/financials';
import {
  isPrimaryPaycheck,
  PRIMARY_PAYCHECK_CATEGORY,
  PRIMARY_PAYCHECK_INTERVAL,
} from './financialsAnchors';
import {
  addDaysIso,
  annualDateLabel,
  annualDueDateForPeriod,
  annualInputDate,
  monthlyDueDateForPeriod,
  parseAnnualDate,
  todayIso,
} from './financialsDatePolicy';
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
  ImportantDateFormState,
  IncomeEventFormState,
  IncomeSummaryFormState,
  PendingRemoval,
  RecurringPaydayFormState,
} from './financialsTypes';

type BuildExpenseSnapshotInput = {
  annualWithdrawals: DraftAnnualWithdrawal[];
  assetCategories: DraftAssetCategory[];
  debtAccounts: DraftDebtAccount[];
  incomeEvents: DraftIncomeEvent[];
  incomeSummaryItems: DraftIncomeSummaryItem[];
  importantDates: DraftImportantDate[];
  payPeriodEnd: string;
  payPeriodStart: string;
  bills: DraftBill[];
  version: number;
};

export type FinancialsDraftState = {
  annualWithdrawalForm: AnnualWithdrawalFormState;
  draftAnnualWithdrawals: DraftAnnualWithdrawal[];
  draftAssetCategories: DraftAssetCategory[];
  draftBills: DraftBill[];
  draftDebtAccounts: DraftDebtAccount[];
  draftImportantDates: DraftImportantDate[];
  draftIncomeEvents: DraftIncomeEvent[];
  draftIncomeSummaryItems: DraftIncomeSummaryItem[];
  incomeSummaryForm: IncomeSummaryFormState;
  payPeriodEnd: string;
  payPeriodStart: string;
};

export const emptyForm: BillFormState = {
  bill: '',
  dueDay: '1',
  amount: '',
  account: 'Check',
  paid: false,
};
export const emptyAnnualWithdrawalForm: AnnualWithdrawalFormState = {
  bill: '',
  date: annualInputDate(1, 1),
  amount: '',
  account: 'Check',
  paid: false,
};
export const emptyAssetForm: AssetFormState = {
  account: '',
  company: '',
  amount: '',
};
export const emptyIncomeEventForm: IncomeEventFormState = {
  date: '',
  label: '',
  type: 'Paycheck',
  checkNumber: '',
};
export function defaultRecurringPaydayForm(today = todayIso()): RecurringPaydayFormState {
  return {
    firstPayDate: '',
    label: 'Paycheck',
    replaceExistingYear: true,
    startingCheckNumber: '1',
    type: 'Paycheck',
    year: today.slice(0, 4),
  };
}
export const emptyIncomeSummaryForm: IncomeSummaryFormState = {
  category: 'Net Income',
  interval: 'Bi-Weekly',
  amount: '',
};
export const emptyImportantDateForm: ImportantDateFormState = {
  date: '',
  event: '',
  type: 'Holiday',
};

export function createFinancialsDraft(snapshot: ExpenseSnapshot): FinancialsDraftState {
  const draftIncomeSummaryItems = (snapshot.incomeSummaryItems ?? []).map((item) => ({ ...item }));
  const primaryPaycheck = draftIncomeSummaryItems.find(isPrimaryPaycheck);

  return {
    annualWithdrawalForm: emptyAnnualWithdrawalForm,
    draftAnnualWithdrawals: (snapshot.annualWithdrawals ?? []).map((withdrawal) =>
      toDraftAnnualWithdrawal(withdrawal, snapshot.payPeriodStart, snapshot.payPeriodEnd)
    ),
    draftAssetCategories: snapshot.assetCategories.map(toDraftAssetCategory),
    draftBills: snapshot.bills.map((bill) =>
      toDraftBill(bill, snapshot.payPeriodStart, snapshot.payPeriodEnd)
    ),
    draftDebtAccounts: (snapshot.debtAccounts ?? []).map((account) => ({ ...account })),
    draftImportantDates: (snapshot.importantDates ?? []).map((importantDate) => ({
      ...importantDate,
    })),
    draftIncomeEvents: withIncomeMonthlyCounts(
      (snapshot.incomeEvents ?? []).map((event) => ({ ...event }))
    ),
    draftIncomeSummaryItems,
    incomeSummaryForm: primaryPaycheck
      ? toIncomeSummaryForm(primaryPaycheck)
      : emptyIncomeSummaryForm,
    payPeriodEnd: snapshot.payPeriodEnd,
    payPeriodStart: snapshot.payPeriodStart,
  };
}

export function buildExpenseSnapshotRequest({
  annualWithdrawals,
  assetCategories,
  bills,
  debtAccounts,
  incomeEvents,
  incomeSummaryItems,
  importantDates,
  payPeriodEnd,
  payPeriodStart,
  version,
}: BuildExpenseSnapshotInput): ExpenseSnapshotRequest {
  return {
    version,
    payPeriodStart,
    payPeriodEnd,
    bills: bills.map(toSnapshotBill),
    annualWithdrawals: annualWithdrawals.map(toSnapshotAnnualWithdrawal),
    assetCategories: assetCategories.map(toSnapshotCategory),
    debtAccounts: debtAccounts.map(toSnapshotDebtAccount),
    incomeSummaryItems: toSnapshotIncomeSummaryItems(incomeSummaryItems),
    incomeEvents: incomeEvents.map(toSnapshotIncomeEvent),
    importantDates: importantDates.map(toSnapshotImportantDate),
  };
}

export function toForm(bill: DraftBill): BillFormState {
  return {
    bill: bill.bill,
    dueDay: String(bill.dueDay),
    amount: String(bill.amount),
    account: bill.account,
    paid: bill.paid,
  };
}

export function toDraftBill(
  bill: ExpenseBill | DraftBill,
  payPeriodStart: string,
  payPeriodEnd: string
): DraftBill {
  const dueDate = monthlyDueDateForPeriod(bill.dueDay, payPeriodStart, payPeriodEnd);
  return {
    id: bill.id,
    bill: bill.bill,
    dueDay: bill.dueDay,
    dueLabel: ordinal(bill.dueDay),
    dueDate,
    amount: bill.amount,
    account: bill.account,
    paid: bill.paid,
    inPayPeriod: dueDate >= payPeriodStart && dueDate <= payPeriodEnd,
  };
}

export function formToDraftBill(
  id: number,
  form: BillFormState,
  payPeriodStart: string,
  payPeriodEnd: string
): DraftBill {
  return toDraftBill(
    {
      id,
      bill: form.bill.trim(),
      dueDay: Number(form.dueDay),
      dueLabel: '',
      dueDate: '',
      amount: Number(form.amount),
      account: form.account.trim(),
      paid: form.paid,
      inPayPeriod: false,
    },
    payPeriodStart,
    payPeriodEnd
  );
}

export function toSnapshotBill(bill: DraftBill): ExpenseBillSnapshotRequest {
  return {
    id: bill.id > 0 ? bill.id : null,
    bill: bill.bill,
    dueDay: bill.dueDay,
    amount: bill.amount,
    account: bill.account,
    paid: bill.paid,
  };
}

export function toAnnualWithdrawalForm(
  withdrawal: DraftAnnualWithdrawal
): AnnualWithdrawalFormState {
  return {
    bill: withdrawal.bill,
    date: annualInputDate(withdrawal.month, withdrawal.day),
    amount: String(withdrawal.amount),
    account: withdrawal.account,
    paid: withdrawal.paid,
  };
}

export function toDraftAnnualWithdrawal(
  withdrawal: AnnualWithdrawal | DraftAnnualWithdrawal,
  payPeriodStart: string,
  payPeriodEnd: string
): DraftAnnualWithdrawal {
  const dueDate = annualDueDateForPeriod(
    withdrawal.month,
    withdrawal.day,
    payPeriodStart,
    payPeriodEnd
  );
  return {
    id: withdrawal.id,
    bill: withdrawal.bill,
    month: withdrawal.month,
    day: withdrawal.day,
    dateLabel: annualDateLabel(dueDate),
    dueDate,
    amount: withdrawal.amount,
    account: withdrawal.account,
    paid: withdrawal.paid,
    inPayPeriod: dueDate >= payPeriodStart && dueDate <= payPeriodEnd,
  };
}

export function formToAnnualWithdrawal(
  id: number,
  form: AnnualWithdrawalFormState,
  payPeriodStart: string,
  payPeriodEnd: string
): DraftAnnualWithdrawal {
  const { month, day } = parseAnnualDate(form.date);
  return toDraftAnnualWithdrawal(
    {
      id,
      bill: form.bill.trim(),
      month,
      day,
      dateLabel: '',
      dueDate: '',
      amount: Number(form.amount),
      account: form.account.trim(),
      paid: form.paid,
      inPayPeriod: false,
    },
    payPeriodStart,
    payPeriodEnd
  );
}

export function toSnapshotAnnualWithdrawal(
  withdrawal: DraftAnnualWithdrawal
): AnnualWithdrawalSnapshotRequest {
  return {
    id: withdrawal.id > 0 ? withdrawal.id : null,
    bill: withdrawal.bill,
    month: withdrawal.month,
    day: withdrawal.day,
    amount: withdrawal.amount,
    account: withdrawal.account,
    paid: withdrawal.paid,
  };
}

export function toDraftAssetCategory(category: AssetCategory): DraftAssetCategory {
  return category;
}

export function toAssetForm(account: DraftAssetAccount): AssetFormState {
  return {
    account: account.account,
    company: account.company,
    amount: String(account.amount),
  };
}

export function formToAssetAccount(id: number, form: AssetFormState): DraftAssetAccount {
  return {
    id,
    account: form.account.trim(),
    company: form.company.trim(),
    amount: Number(form.amount),
  };
}

export function recalculateAssetCategory(category: DraftAssetCategory): DraftAssetCategory {
  return {
    ...category,
    total: category.accounts.reduce((total, account) => total + account.amount, 0),
  };
}

export function toSnapshotCategory(category: DraftAssetCategory): AssetCategorySnapshotRequest {
  return {
    key: category.key,
    label: category.label,
    accounts: category.accounts.map((account) => ({
      id: account.id > 0 ? account.id : null,
      account: account.account,
      company: account.company,
      amount: account.amount,
    })),
  };
}

export function toDebtForm(account: DraftDebtAccount): AssetFormState {
  return {
    account: account.account,
    company: account.company,
    amount: String(account.amount),
  };
}

export function formToDebtAccount(id: number, form: AssetFormState): DraftDebtAccount {
  return {
    id,
    account: form.account.trim(),
    company: form.company.trim(),
    amount: Number(form.amount),
  };
}

export function toSnapshotDebtAccount(account: DraftDebtAccount): DebtAccountSnapshotRequest {
  return {
    id: account.id > 0 ? account.id : null,
    account: account.account,
    company: account.company,
    amount: account.amount,
  };
}

export function toIncomeSummaryForm(item: DraftIncomeSummaryItem): IncomeSummaryFormState {
  return {
    category: item.category,
    interval: item.interval,
    amount: String(item.amount),
  };
}

export function formToIncomeSummaryItem(
  id: number,
  form: IncomeSummaryFormState
): DraftIncomeSummaryItem {
  return {
    id,
    category: form.category.trim(),
    interval: form.interval.trim(),
    amount: Number(form.amount),
  };
}

export function toSnapshotIncomeSummaryItem(
  item: DraftIncomeSummaryItem
): IncomeSummaryItemSnapshotRequest {
  return {
    id: item.id > 0 ? item.id : null,
    category: item.category,
    interval: item.interval,
    amount: item.amount,
  };
}

export function toSnapshotIncomeSummaryItems(
  items: DraftIncomeSummaryItem[]
): IncomeSummaryItemSnapshotRequest[] {
  return items.map(toSnapshotIncomeSummaryItem);
}

export function buildDerivedIncomeSummaryItems(
  items: DraftIncomeSummaryItem[],
  totalMonthlyWithdrawals: number
) {
  const primaryPaycheck = items.find(
    (item) =>
      item.category === PRIMARY_PAYCHECK_CATEGORY && item.interval === PRIMARY_PAYCHECK_INTERVAL
  );
  const biWeeklyNetIncome = primaryPaycheck?.amount ?? 0;
  const monthlyNetIncome = biWeeklyNetIncome * 2;
  const annualNetIncome = biWeeklyNetIncome * 26;
  const monthlyDisposableIncome = monthlyNetIncome - totalMonthlyWithdrawals;
  const biWeeklyDisposableIncome = monthlyDisposableIncome / 2;
  const weeklyDisposableIncome = biWeeklyDisposableIncome / 2;

  return [
    {
      id: -100003,
      category: PRIMARY_PAYCHECK_CATEGORY,
      interval: 'Annual',
      amount: annualNetIncome,
    },
    {
      id: -100004,
      category: PRIMARY_PAYCHECK_CATEGORY,
      interval: 'Month',
      amount: monthlyNetIncome,
    },
    {
      id: primaryPaycheck?.id ?? -100002,
      category: PRIMARY_PAYCHECK_CATEGORY,
      interval: PRIMARY_PAYCHECK_INTERVAL,
      amount: biWeeklyNetIncome,
    },
    {
      id: -100005,
      category: PRIMARY_PAYCHECK_CATEGORY,
      interval: 'Weekly',
      amount: biWeeklyNetIncome / 2,
    },
    {
      id: -100006,
      category: 'Disposable Income',
      interval: 'Annual',
      amount: monthlyDisposableIncome * 12,
    },
    {
      id: -100007,
      category: 'Disposable Income',
      interval: 'Month',
      amount: monthlyDisposableIncome,
    },
    {
      id: -100008,
      category: 'Disposable Income',
      interval: 'Bi-Weekly',
      amount: biWeeklyDisposableIncome,
    },
    {
      id: -100009,
      category: 'Disposable Income',
      interval: 'Weekly',
      amount: weeklyDisposableIncome,
    },
  ];
}

export function toIncomeEventForm(event: DraftIncomeEvent): IncomeEventFormState {
  return {
    date: event.date,
    label: event.label,
    type: event.type,
    checkNumber: event.checkNumber === null ? '' : String(event.checkNumber),
  };
}

export function formToIncomeEvent(id: number, form: IncomeEventFormState): DraftIncomeEvent {
  return {
    id,
    date: form.date,
    label: form.label.trim(),
    type: form.type.trim(),
    checkNumber: form.checkNumber ? Number(form.checkNumber) : null,
    checksInMonth: 0,
  };
}

export function withIncomeMonthlyCounts(events: DraftIncomeEvent[]): DraftIncomeEvent[] {
  const paycheckCounts = events.reduce<Record<string, number>>((counts, event) => {
    if (event.checkNumber !== null) {
      const monthKey = event.date.slice(0, 7);
      counts[monthKey] = (counts[monthKey] ?? 0) + 1;
    }
    return counts;
  }, {});
  return events
    .map((event) => ({ ...event, checksInMonth: paycheckCounts[event.date.slice(0, 7)] ?? 0 }))
    .sort((left, right) => left.date.localeCompare(right.date));
}

export function generateRecurringPaydays(
  form: RecurringPaydayFormState,
  nextDraftId: number
): DraftIncomeEvent[] {
  const year = Number(form.year);
  const firstPayYear = Number(form.firstPayDate.slice(0, 4));
  if (!Number.isInteger(year) || year < 1900 || firstPayYear !== year) {
    return [];
  }

  const label = form.label.trim() || 'Paycheck';
  const type = form.type.trim() || 'Paycheck';
  const startingCheckNumber = Math.max(1, Number(form.startingCheckNumber) || 1);
  const lastDate = `${year}-12-31`;
  const paydays: DraftIncomeEvent[] = [];
  let nextDate = form.firstPayDate;
  let nextId = nextDraftId;
  let checkNumber = startingCheckNumber;

  while (nextDate <= lastDate) {
    paydays.push({
      checkNumber,
      checksInMonth: 0,
      date: nextDate,
      id: nextId,
      label,
      type,
    });
    nextDate = addDaysIso(nextDate, 14);
    nextId -= 1;
    checkNumber += 1;
  }

  return withIncomeMonthlyCounts(paydays);
}

export function isNumberedIncomeEventInYear(event: DraftIncomeEvent, year: string) {
  return event.checkNumber !== null && event.date.startsWith(`${year}-`);
}

export function getTodayIso() {
  return todayIso();
}

export function getCurrentPaycheck(events: DraftIncomeEvent[], todayIso: string) {
  return events
    .filter((event) => event.checkNumber !== null && event.date <= todayIso)
    .sort((left, right) => right.date.localeCompare(left.date))[0];
}

export function withIncomeEventStatuses(
  events: DraftIncomeEvent[],
  todayIso: string
): DraftIncomeEvent[] {
  const currentPaycheck = getCurrentPaycheck(events, todayIso);

  return withIncomeMonthlyCounts(events).map((event) => {
    if (currentPaycheck && event.id === currentPaycheck.id) {
      return { ...event, status: 'current' };
    }

    return {
      ...event,
      status: event.date < todayIso ? 'received' : 'upcoming',
    };
  });
}

export function toSnapshotIncomeEvent(event: DraftIncomeEvent): IncomeEventSnapshotRequest {
  return {
    id: event.id > 0 ? event.id : null,
    date: event.date,
    label: event.label,
    type: event.type,
    checkNumber: event.checkNumber,
  };
}

export function toImportantDateForm(importantDate: DraftImportantDate): ImportantDateFormState {
  return { date: importantDate.date, event: importantDate.event, type: importantDate.type };
}

export function formToImportantDate(id: number, form: ImportantDateFormState): DraftImportantDate {
  return { id, date: form.date, event: form.event.trim(), type: form.type.trim() };
}

export function getNextImportantDate(dates: DraftImportantDate[], todayIso: string) {
  return dates
    .filter((importantDate) => importantDate.date >= todayIso)
    .sort((left, right) => left.date.localeCompare(right.date))[0];
}

export function withImportantDateStatuses(
  dates: DraftImportantDate[],
  todayIso: string
): DraftImportantDate[] {
  const nextImportantDate = getNextImportantDate(dates, todayIso);

  return [...dates]
    .sort((left, right) => left.date.localeCompare(right.date))
    .map((importantDate) => {
      if (nextImportantDate && importantDate.id === nextImportantDate.id) {
        return { ...importantDate, status: 'next' };
      }

      return {
        ...importantDate,
        status: importantDate.date < todayIso ? 'passed' : 'upcoming',
      };
    });
}

export function toSnapshotImportantDate(
  importantDate: DraftImportantDate
): ImportantDateSnapshotRequest {
  return {
    id: importantDate.id > 0 ? importantDate.id : null,
    date: importantDate.date,
    event: importantDate.event,
    type: importantDate.type,
  };
}

export function removalItemType(pendingRemoval: PendingRemoval) {
  switch (pendingRemoval.type) {
    case 'bill':
      return 'withdrawal';
    case 'annual-withdrawal':
      return 'annual withdrawal';
    case 'asset':
      return 'account';
    case 'debt':
      return 'debt account';
    case 'income-summary':
      return 'income summary item';
    case 'income':
      return 'income event';
    case 'important-date':
      return 'important date';
  }
}

function ordinal(day: number) {
  if (day >= 11 && day <= 13) return `${day}th`;
  switch (day % 10) {
    case 1:
      return `${day}st`;
    case 2:
      return `${day}nd`;
    case 3:
      return `${day}rd`;
    default:
      return `${day}th`;
  }
}
