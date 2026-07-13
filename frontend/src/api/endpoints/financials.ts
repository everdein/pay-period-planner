/**
 * Financials API types and endpoints (v1)
 * cspell:ignore openxmlformats officedocument spreadsheetml
 *
 * MONEY HANDLING STRATEGY:
 * - Backend: All monetary values are BigDecimal (precise decimal arithmetic)
 * - JSON transport: BigDecimal serializes to JSON numbers (string representation of decimals)
 * - Frontend: Money values are TypeScript `number` (IEEE 754 double precision)
 *
 * This design provides production-grade precision on the backend while keeping
 * frontend forms/display simple. A future phase can migrate frontend to integer
 * cents (BigInt or number * 100) if stricter arithmetic guarantees are needed.
 *
 * For now: display/input treats values as decimal numbers; backend calculations remain authoritative.
 *
 * API VERSIONING:
 * - Explicit /api/v1/ prefix for clear backward compatibility
 * - Endpoint structure: /api/v1/financials (main resource)
 *   - GET /api/v1/financials → snapshot
 *   - PUT /api/v1/financials → save snapshot
 *   - POST /api/v1/financials/bills → create bill
 *   - PUT /api/v1/financials/bills/{id} → update bill
 *   - DELETE /api/v1/financials/bills/{id} → delete bill
 *   - PUT /api/v1/financials/pay-period → update pay period
 */

import { httpDelete, httpGet, httpGetBlob, httpPost, httpPostRaw, httpPut } from '../client';

// Source snapshot backup and restore endpoints.
export const FINANCIALS_EXPORT_PATH = '/api/v1/financials/export';
export const FINANCIALS_EXPORT_CSV_PATH = '/api/v1/financials/export/csv';
export const FINANCIALS_EXPORT_XLSX_PATH = '/api/v1/financials/export/xlsx';
export const FINANCIALS_IMPORT_CSV_PATH = '/api/v1/financials/import/csv';
export const FINANCIALS_IMPORT_XLSX_PATH = '/api/v1/financials/import/xlsx';
export const FINANCIALS_XLSX_CONTENT_TYPE =
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

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

export type AnnualWithdrawalRequest = {
  bill: string;
  month: number;
  day: number;
  amount: number;
  account: string;
  paid: boolean;
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
  version: number;
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

export type AssetAccountRecord = {
  id: number;
  categoryKey: string;
  categoryLabel: string;
  account: string;
  company: string;
  amount: number;
};

export type AssetAccountRequest = {
  categoryKey: string;
  categoryLabel: string;
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

export type DebtAccountRequest = {
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

export type IncomeSummaryItemRequest = {
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

export type IncomeEventRequest = {
  date: string;
  label: string;
  type: string;
  checkNumber: number | null;
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

export type ImportantDateRequest = {
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
  version: number;
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

export type FinancialSnapshotExport = {
  format: 'end-to-end-app.financial-snapshot.v1';
  exportedAt: string;
  snapshot: ExpenseSnapshotRequest;
};

export type FinancialProjectionSummary = {
  payPeriodStart: string;
  payPeriodEnd: string;
  monthlyBillCount: number;
  annualWithdrawalCount: number;
  assetAccountCount: number;
  debtAccountCount: number;
  incomeSummaryItemCount: number;
  incomeEventCount: number;
  importantDateCount: number;
  totalMonthlyExpenses: number;
  totalAnnualWithdrawals: number;
  totalTrackedAssets: number;
  totalDebt: number;
  netWorth: number;
};

export type FinancialAuditEvent = {
  id: number;
  occurredAt: string;
  action: 'CREATE' | 'UPDATE' | 'DELETE' | 'REPLACE';
  resourceType: string;
  resourceId: number | null;
  versionBefore: number;
  versionAfter: number;
  summary: string;
  projectionSummary: FinancialProjectionSummary;
};

export type FinancialAuditHistory = {
  events: FinancialAuditEvent[];
};

export const financialsService = {
  getMonthlyExpenses: () => httpGet<ExpenseSnapshot>('/api/v1/financials'),
  getAuditHistory: (limit = 50) =>
    httpGet<FinancialAuditHistory>(`/api/v1/financials/history?limit=${limit}`),
  downloadSnapshotJson: () => httpGetBlob(FINANCIALS_EXPORT_PATH),
  downloadSnapshotCsv: () => httpGetBlob(FINANCIALS_EXPORT_CSV_PATH),
  downloadSnapshotXlsx: () => httpGetBlob(FINANCIALS_EXPORT_XLSX_PATH),
  importSnapshotCsv: (csv: string) =>
    httpPostRaw<ExpenseSnapshot>(FINANCIALS_IMPORT_CSV_PATH, csv, 'text/csv'),
  importSnapshotXlsx: (workbook: Blob | ArrayBuffer) =>
    httpPostRaw<ExpenseSnapshot>(
      FINANCIALS_IMPORT_XLSX_PATH,
      workbook,
      FINANCIALS_XLSX_CONTENT_TYPE
    ),
  addBill: (payload: ExpenseBillRequest) =>
    httpPost<ExpenseBill, ExpenseBillRequest>('/api/v1/financials/bills', payload),
  updateBill: (id: number, payload: ExpenseBillRequest) =>
    httpPut<ExpenseBill, ExpenseBillRequest>(`/api/v1/financials/bills/${id}`, payload),
  deleteBill: (id: number) => httpDelete(`/api/v1/financials/bills/${id}`),
  addAnnualWithdrawal: (payload: AnnualWithdrawalRequest) =>
    httpPost<AnnualWithdrawal, AnnualWithdrawalRequest>(
      '/api/v1/financials/annual-withdrawals',
      payload
    ),
  updateAnnualWithdrawal: (id: number, payload: AnnualWithdrawalRequest) =>
    httpPut<AnnualWithdrawal, AnnualWithdrawalRequest>(
      `/api/v1/financials/annual-withdrawals/${id}`,
      payload
    ),
  deleteAnnualWithdrawal: (id: number) => httpDelete(`/api/v1/financials/annual-withdrawals/${id}`),
  addAssetAccount: (payload: AssetAccountRequest) =>
    httpPost<AssetAccountRecord, AssetAccountRequest>('/api/v1/financials/asset-accounts', payload),
  updateAssetAccount: (id: number, payload: AssetAccountRequest) =>
    httpPut<AssetAccountRecord, AssetAccountRequest>(
      `/api/v1/financials/asset-accounts/${id}`,
      payload
    ),
  deleteAssetAccount: (id: number) => httpDelete(`/api/v1/financials/asset-accounts/${id}`),
  addDebtAccount: (payload: DebtAccountRequest) =>
    httpPost<DebtAccount, DebtAccountRequest>('/api/v1/financials/debt-accounts', payload),
  updateDebtAccount: (id: number, payload: DebtAccountRequest) =>
    httpPut<DebtAccount, DebtAccountRequest>(`/api/v1/financials/debt-accounts/${id}`, payload),
  deleteDebtAccount: (id: number) => httpDelete(`/api/v1/financials/debt-accounts/${id}`),
  addIncomeSummaryItem: (payload: IncomeSummaryItemRequest) =>
    httpPost<IncomeSummaryItem, IncomeSummaryItemRequest>(
      '/api/v1/financials/income-summary-items',
      payload
    ),
  updateIncomeSummaryItem: (id: number, payload: IncomeSummaryItemRequest) =>
    httpPut<IncomeSummaryItem, IncomeSummaryItemRequest>(
      `/api/v1/financials/income-summary-items/${id}`,
      payload
    ),
  deleteIncomeSummaryItem: (id: number) =>
    httpDelete(`/api/v1/financials/income-summary-items/${id}`),
  addIncomeEvent: (payload: IncomeEventRequest) =>
    httpPost<IncomeEvent, IncomeEventRequest>('/api/v1/financials/income-events', payload),
  updateIncomeEvent: (id: number, payload: IncomeEventRequest) =>
    httpPut<IncomeEvent, IncomeEventRequest>(`/api/v1/financials/income-events/${id}`, payload),
  deleteIncomeEvent: (id: number) => httpDelete(`/api/v1/financials/income-events/${id}`),
  addImportantDate: (payload: ImportantDateRequest) =>
    httpPost<ImportantDate, ImportantDateRequest>('/api/v1/financials/important-dates', payload),
  updateImportantDate: (id: number, payload: ImportantDateRequest) =>
    httpPut<ImportantDate, ImportantDateRequest>(
      `/api/v1/financials/important-dates/${id}`,
      payload
    ),
  deleteImportantDate: (id: number) => httpDelete(`/api/v1/financials/important-dates/${id}`),
  updatePayPeriod: (payload: PayPeriodRequest) =>
    httpPut<ExpenseSnapshot, PayPeriodRequest>('/api/v1/financials/pay-period', payload),
  saveSnapshot: (payload: ExpenseSnapshotRequest) =>
    httpPut<ExpenseSnapshot, ExpenseSnapshotRequest>('/api/v1/financials', payload),
};
