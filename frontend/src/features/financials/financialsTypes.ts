import type {
  AnnualWithdrawal,
  AssetAccount,
  DebtAccount,
  ImportantDate,
  IncomeEvent,
  IncomeSummaryItem,
} from '../../api/endpoints/financials';

export type BillFormState = {
  bill: string;
  dueDay: string;
  amount: string;
  account: string;
  paid: boolean;
};

export type DraftBill = {
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

export type AnnualWithdrawalFormState = {
  bill: string;
  date: string;
  amount: string;
  account: string;
  paid: boolean;
};

export type DraftAnnualWithdrawal = AnnualWithdrawal;

export type AssetFormState = {
  account: string;
  company: string;
  amount: string;
};

export type IncomeEventFormState = {
  date: string;
  label: string;
  type: string;
  checkNumber: string;
};

export type ImportantDateFormState = {
  date: string;
  event: string;
  type: string;
};

export type IncomeSummaryFormState = {
  category: string;
  interval: string;
  amount: string;
};

export type DraftAssetAccount = AssetAccount;
export type DraftDebtAccount = DebtAccount;
export type DraftIncomeSummaryItem = IncomeSummaryItem;
export type IncomeEventStatus = 'received' | 'current' | 'upcoming';
export type ImportantDateStatus = 'passed' | 'next' | 'upcoming';

export type DraftIncomeEvent = IncomeEvent & {
  status?: IncomeEventStatus;
};
export type DraftImportantDate = ImportantDate & {
  status?: ImportantDateStatus;
};

export type ProjectionLine = {
  amount: number;
  label: string;
};

export type ProjectionPeriod = {
  annualWithdrawalsDue: number;
  monthlyWithdrawalsDue: number;
  paycheckIncome: number;
  payPeriodEnd: string;
  payPeriodStart: string;
  projectedBeforeDebt: number;
  rentCoveredBySavings: number;
  rentBillAmount: number;
  rentContribution: number;
  rentRemainingNeed: number;
  rentSavingsBalance: number;
  title: string;
  withdrawalLines: ProjectionLine[];
};

export type ProjectionSummary = {
  currentDebt: number;
  debtCoveredByProjectedCash: number;
  debtCoveragePercent: number;
  nextPayPeriodCashAfterBills: number;
  nextPayPeriodDebtPayment: number;
  nextPayPeriodDebtRemaining: number;
  nextPayPeriodHysaTransfer: number;
  projectedAfterDebt: number;
  projectedBeforeDebt: number;
  remainingDebtAfterProjectedCash: number;
  periods: ProjectionPeriod[];
};

export type DraftAssetCategory = {
  key: string;
  label: string;
  total: number;
  accounts: DraftAssetAccount[];
};

export type FinancialTab =
  | 'overview'
  | 'projection'
  | 'monthly-withdrawals'
  | 'annual-withdrawals'
  | 'income-summary'
  | 'income-calendar'
  | 'retirement'
  | 'investments'
  | 'cash-savings'
  | 'insurance-benefits'
  | 'debt'
  | 'important-dates';

export type PendingRemoval =
  | {
      id: number;
      name: string;
      type: 'bill';
    }
  | {
      id: number;
      name: string;
      type: 'annual-withdrawal';
    }
  | {
      categoryKey: string;
      id: number;
      name: string;
      type: 'asset';
    }
  | {
      id: number;
      name: string;
      type: 'debt';
    }
  | {
      id: number;
      name: string;
      type: 'income-summary';
    }
  | {
      id: number;
      name: string;
      type: 'income';
    }
  | {
      id: number;
      name: string;
      type: 'important-date';
    };
