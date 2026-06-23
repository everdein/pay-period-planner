import { httpDelete, httpGet, httpPost, httpPut } from '../client';

export type ExpenseBill = {
  id: number;
  bill: string;
  dueDay: number;
  dueLabel: string;
  dueDate: string;
  amount: number;
  account: string;
  paid: boolean;
  inPayPeriod: boolean;
};

export type ExpenseBillRequest = {
  bill: string;
  dueDay: number;
  amount: number;
  account: string;
  paid: boolean;
};

export type PayPeriodRequest = {
  startDate: string;
  endDate: string;
};

export type ExpenseBillSnapshotRequest = {
  id: number | null;
  bill: string;
  dueDay: number;
  amount: number;
  account: string;
  paid: boolean;
};

export type AnnualWithdrawal = {
  id: number;
  bill: string;
  month: number;
  day: number;
  dateLabel: string;
  dueDate: string;
  amount: number;
  account: string;
  paid: boolean;
  inPayPeriod: boolean;
};

export type AnnualWithdrawalSnapshotRequest = {
  id: number | null;
  bill: string;
  month: number;
  day: number;
  amount: number;
  account: string;
  paid: boolean;
};

export type ExpenseSnapshotRequest = {
  payPeriodStart: string;
  payPeriodEnd: string;
  bills: ExpenseBillSnapshotRequest[];
  annualWithdrawals: AnnualWithdrawalSnapshotRequest[];
  assetCategories: AssetCategorySnapshotRequest[];
  debtAccounts: DebtAccountSnapshotRequest[];
  incomeSummaryItems: IncomeSummaryItemSnapshotRequest[];
  incomeEvents: IncomeEventSnapshotRequest[];
  importantDates: ImportantDateSnapshotRequest[];
};

export type AssetAccount = {
  id: number;
  account: string;
  company: string;
  amount: number;
};

export type AssetCategory = {
  key: string;
  label: string;
  total: number;
  accounts: AssetAccount[];
};

export type AssetAccountSnapshotRequest = {
  id: number | null;
  account: string;
  company: string;
  amount: number;
};

export type AssetCategorySnapshotRequest = {
  key: string;
  label: string;
  accounts: AssetAccountSnapshotRequest[];
};

export type DebtAccount = {
  id: number;
  account: string;
  company: string;
  amount: number;
};

export type DebtAccountSnapshotRequest = {
  id: number | null;
  account: string;
  company: string;
  amount: number;
};

export type IncomeSummaryItem = {
  id: number;
  category: string;
  interval: string;
  amount: number;
};

export type IncomeSummaryItemSnapshotRequest = {
  id: number | null;
  category: string;
  interval: string;
  amount: number;
};

export type IncomeEvent = {
  id: number;
  date: string;
  label: string;
  type: string;
  checkNumber: number | null;
  checksInMonth: number;
};

export type IncomeEventSnapshotRequest = {
  id: number | null;
  date: string;
  label: string;
  type: string;
  checkNumber: number | null;
};

export type ImportantDate = {
  id: number;
  date: string;
  event: string;
  type: string;
};

export type ImportantDateSnapshotRequest = {
  id: number | null;
  date: string;
  event: string;
  type: string;
};

export type ExpenseSnapshot = {
  payPeriodStart: string;
  payPeriodEnd: string;
  totalMonthlyExpenses: number;
  paidTotal: number;
  unpaidTotal: number;
  payPeriodTotal: number;
  totalAnnualWithdrawals: number;
  annualPayPeriodTotal: number;
  totalTrackedAssets: number;
  totalDebt: number;
  netWorth: number;
  assetCategories: AssetCategory[];
  debtAccounts: DebtAccount[];
  incomeSummaryItems: IncomeSummaryItem[];
  bills: ExpenseBill[];
  annualWithdrawals: AnnualWithdrawal[];
  incomeEvents: IncomeEvent[];
  importantDates: ImportantDate[];
};

export const financialsService = {
  getMonthlyExpenses: () => httpGet<ExpenseSnapshot>('/api/financials/expenses'),
  addBill: (payload: ExpenseBillRequest) =>
    httpPost<ExpenseBill, ExpenseBillRequest>('/api/financials/expenses', payload),
  updateBill: (id: number, payload: ExpenseBillRequest) =>
    httpPut<ExpenseBill, ExpenseBillRequest>(`/api/financials/expenses/${id}`, payload),
  deleteBill: (id: number) => httpDelete(`/api/financials/expenses/${id}`),
  updatePayPeriod: (payload: PayPeriodRequest) =>
    httpPut<ExpenseSnapshot, PayPeriodRequest>('/api/financials/pay-period', payload),
  saveSnapshot: (payload: ExpenseSnapshotRequest) =>
    httpPut<ExpenseSnapshot, ExpenseSnapshotRequest>('/api/financials/expenses/snapshot', payload),
};
