import type { FinancialTab } from './financialsTypes';

export type FinancialNavigationSection = {
  items: Array<[FinancialTab, string]>;
  label: string;
};

export const navigationSections: FinancialNavigationSection[] = [
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
    label: 'Balances',
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
