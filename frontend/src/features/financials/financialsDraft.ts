import type {
  AnnualWithdrawal,
  AnnualWithdrawalSnapshotRequest,
  AssetCategory,
  AssetCategorySnapshotRequest,
  DebtAccountSnapshotRequest,
  ExpenseBill,
  ExpenseBillSnapshotRequest,
  ImportantDateSnapshotRequest,
  IncomeEventSnapshotRequest,
  IncomeSummaryItemSnapshotRequest,
} from '../../api/endpoints/financials';
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
} from './financialsTypes';

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
export const emptyAssetForm: AssetFormState = { account: '', company: '', amount: '' };
export const emptyIncomeEventForm: IncomeEventFormState = {
  date: '',
  label: '',
  type: 'Paycheck',
  checkNumber: '',
};
export const emptyIncomeSummaryForm: IncomeSummaryFormState = {
  category: 'Net Income',
  interval: 'Annual',
  amount: '',
};
export const emptyImportantDateForm: ImportantDateFormState = {
  date: '',
  event: '',
  type: 'Holiday',
};

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
  const dueDate = dueDateForPeriod(bill.dueDay, payPeriodStart, payPeriodEnd);
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
    dateLabel: dateLabel(dueDate),
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
  return { ...category, accounts: category.accounts.map((account) => ({ ...account })) };
}

export function toAssetForm(account: DraftAssetAccount): AssetFormState {
  return { account: account.account, company: account.company, amount: String(account.amount) };
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
  return { account: account.account, company: account.company, amount: String(account.amount) };
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
  return { category: item.category, interval: item.interval, amount: String(item.amount) };
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

export function getTodayIso() {
  const today = new Date();
  const offset = today.getTimezoneOffset() * 60_000;
  return new Date(today.getTime() - offset).toISOString().slice(0, 10);
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

function dueDateForPeriod(dueDay: number, payPeriodStart: string, payPeriodEnd: string) {
  const startDate = new Date(`${payPeriodStart}T00:00:00`);
  const endDate = new Date(`${payPeriodEnd}T00:00:00`);
  let dueDate = safeDate(startDate.getFullYear(), startDate.getMonth(), dueDay);
  if (dueDate < startDate && startDate.getMonth() !== endDate.getMonth())
    dueDate = safeDate(endDate.getFullYear(), endDate.getMonth(), dueDay);
  return dueDate.toISOString().slice(0, 10);
}

function annualDueDateForPeriod(
  dayMonth: number,
  day: number,
  payPeriodStart: string,
  payPeriodEnd: string
) {
  const startDate = new Date(`${payPeriodStart}T00:00:00`);
  const endDate = new Date(`${payPeriodEnd}T00:00:00`);
  let dueDate = safeDate(startDate.getFullYear(), dayMonth - 1, day);
  if (dueDate < startDate && startDate.getFullYear() !== endDate.getFullYear())
    dueDate = safeDate(endDate.getFullYear(), dayMonth - 1, day);
  return dueDate.toISOString().slice(0, 10);
}

function safeDate(year: number, monthIndex: number, day: number) {
  const daysInMonth = new Date(year, monthIndex + 1, 0).getDate();
  return new Date(year, monthIndex, Math.min(day, daysInMonth));
}

function dateLabel(value: string) {
  return new Date(`${value}T00:00:00`).toLocaleDateString('en-US', {
    month: '2-digit',
    day: '2-digit',
    year: 'numeric',
  });
}

function parseAnnualDate(value: string) {
  if (value.includes('-')) {
    const [, month = 1, day = 1] = value.split('-').map(Number);
    return { month, day };
  }

  const [month = 1, day = 1] = value.split('/').map(Number);
  return { month, day };
}

function annualInputDate(month: number, day: number) {
  const year = new Date().getFullYear();
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}
