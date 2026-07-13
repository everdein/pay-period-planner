import { describe, expect, it } from 'vitest';

import {
  annualDueDateForPeriod,
  monthlyDueDateForPeriod,
  nextPayPeriod,
} from './financialsDatePolicy';
import {
  buildDerivedIncomeSummaryItems,
  buildExpenseSnapshotRequest,
  createFinancialsDraft,
  generateRecurringPaydays,
  isNumberedIncomeEventInYear,
  toSnapshotIncomeSummaryItems,
} from './financialsDraft';
import { buildProjectionPeriod, buildProjectionSummary } from './financialsProjection';
import type {
  DraftAnnualWithdrawal,
  DraftAssetCategory,
  DraftBill,
  DraftIncomeEvent,
} from './financialsTypes';

function bill(overrides: Partial<DraftBill>): DraftBill {
  return {
    account: 'Check',
    amount: 0,
    bill: 'Bill',
    dueDate: '2026-06-12',
    dueDay: 12,
    dueLabel: '12th',
    id: 1,
    inPayPeriod: true,
    paid: false,
    ...overrides,
  };
}

function annualWithdrawal(overrides: Partial<DraftAnnualWithdrawal>): DraftAnnualWithdrawal {
  return {
    account: 'Check',
    amount: 0,
    bill: 'Annual Bill',
    dateLabel: '06/30/2026',
    day: 30,
    dueDate: '2026-06-30',
    id: 1,
    inPayPeriod: true,
    month: 6,
    paid: false,
    ...overrides,
  };
}

function cashSavings(amount: number): DraftAssetCategory {
  return {
    accounts: [
      {
        account: 'Rent Reserve',
        amount,
        company: 'Credit Union',
        id: 1,
      },
    ],
    key: 'cash-savings',
    label: 'Cash & Savings',
    total: amount,
  };
}

describe('financialsProjection', () => {
  it('uses rent savings for rent due and sets aside the next rent contribution', () => {
    const result = buildProjectionPeriod(
      'Current Pay Period',
      '2026-06-26',
      '2026-07-09',
      [bill({ amount: 2600, bill: 'Rent', dueDay: 1, id: 1 })],
      [],
      3396.25,
      bill({ amount: 2600, bill: 'Rent', dueDay: 1, id: 1 }),
      1300
    );

    expect(result.period.rentCoveredBySavings).toBe(1300);
    expect(result.period.rentContribution).toBe(1300);
    expect(result.period.projectedBeforeDebt).toBe(2096.25);
    expect(result.endingRentSavings).toBe(1300);
  });

  it('includes annual withdrawals due in the next pay period', () => {
    const projection = buildProjectionSummary({
      annualWithdrawals: [
        annualWithdrawal({
          amount: 99,
          bill: 'Arc Studio',
          day: 30,
          dueDate: '2026-12-30',
          month: 12,
        }),
      ],
      annualWithdrawalsInPayPeriod: [],
      cashSavings: cashSavings(1300),
      paycheckIncome: 3396.25,
      payPeriodEnd: '2026-12-29',
      payPeriodStart: '2026-12-16',
      sortedBills: [
        bill({
          amount: 2600,
          bill: 'Rent',
          dueDay: 1,
          id: 1,
          inPayPeriod: false,
        }),
      ],
      totalDebt: 0,
    });

    expect(projection.periods[1]?.payPeriodStart).toBe('2026-12-30');
    expect(projection.periods[1]?.annualWithdrawalsDue).toBe(99);
    expect(projection.nextPayPeriodCashAfterBills).toBe(1997.25);
  });

  it('applies next pay period cash to debt before HYSA transfers', () => {
    const projection = buildProjectionSummary({
      annualWithdrawals: [],
      annualWithdrawalsInPayPeriod: [],
      cashSavings: cashSavings(1300),
      paycheckIncome: 3396.25,
      payPeriodEnd: '2026-06-25',
      payPeriodStart: '2026-06-12',
      sortedBills: [
        bill({
          amount: 2600,
          bill: 'Rent',
          dueDay: 1,
          id: 1,
          inPayPeriod: false,
        }),
        bill({ amount: 148, bill: 'T-Mobile', dueDay: 28, id: 2, inPayPeriod: false }),
      ],
      totalDebt: 2130.03,
    });

    expect(projection.nextPayPeriodCashAfterBills).toBe(1948.25);
    expect(projection.nextPayPeriodDebtPayment).toBe(1948.25);
    expect(projection.nextPayPeriodDebtRemaining).toBeCloseTo(181.78);
    expect(projection.nextPayPeriodHysaTransfer).toBe(0);
  });

  it('shows possible HYSA transfer only after debt is covered', () => {
    const projection = buildProjectionSummary({
      annualWithdrawals: [],
      annualWithdrawalsInPayPeriod: [],
      cashSavings: cashSavings(1300),
      paycheckIncome: 3396.25,
      payPeriodEnd: '2026-06-25',
      payPeriodStart: '2026-06-12',
      sortedBills: [
        bill({
          amount: 2600,
          bill: 'Rent',
          dueDay: 1,
          id: 1,
          inPayPeriod: false,
        }),
      ],
      totalDebt: 500,
    });

    expect(projection.nextPayPeriodDebtPayment).toBe(500);
    expect(projection.nextPayPeriodDebtRemaining).toBe(0);
    expect(projection.nextPayPeriodHysaTransfer).toBe(1596.25);
  });

  it('rolls the next pay period across month and year boundaries', () => {
    expect(nextPayPeriod('2026-12-18', '2026-12-31')).toEqual({
      end: '2027-01-14',
      start: '2027-01-01',
    });
  });

  it('chooses end month for due dates when a pay period crosses months', () => {
    expect(monthlyDueDateForPeriod(1, '2026-06-26', '2026-07-09')).toBe('2026-07-01');
  });

  it('clamps monthly due dates to the last day of month', () => {
    expect(monthlyDueDateForPeriod(31, '2026-02-01', '2026-02-15')).toBe('2026-02-28');
  });

  it('chooses end year for annual due dates when a pay period crosses years', () => {
    expect(annualDueDateForPeriod(1, 1, '2026-12-30', '2027-01-12')).toBe('2027-01-01');
  });

  it('derives income summary rows from bi-weekly net income and monthly withdrawals', () => {
    const items = buildDerivedIncomeSummaryItems(
      [
        {
          amount: 3396.25,
          category: 'Net Income',
          id: 7,
          interval: 'Bi-Weekly',
        },
      ],
      4890.92
    );

    expect(items).toContainEqual({
      amount: 88302.5,
      category: 'Net Income',
      id: -100003,
      interval: 'Annual',
    });
    expect(items).toContainEqual({
      amount: 6792.5,
      category: 'Net Income',
      id: -100004,
      interval: 'Month',
    });
    expect(items).toContainEqual({
      amount: 3396.25,
      category: 'Net Income',
      id: 7,
      interval: 'Bi-Weekly',
    });
    expect(
      items.find((item) => item.category === 'Disposable Income' && item.interval === 'Month')
        ?.amount
    ).toBeCloseTo(1901.58);
    expect(
      items.find((item) => item.category === 'Disposable Income' && item.interval === 'Weekly')
        ?.amount
    ).toBeCloseTo(475.4);
  });

  it('preserves source income summary rows when building a save payload', () => {
    const items = toSnapshotIncomeSummaryItems([
      {
        amount: 3396.25,
        category: 'Net Income',
        id: 7,
        interval: 'Bi-Weekly',
      },
      {
        amount: 125,
        category: 'Side Income',
        id: 8,
        interval: 'Month',
      },
    ]);

    expect(items).toContainEqual({
      amount: 3396.25,
      category: 'Net Income',
      id: 7,
      interval: 'Bi-Weekly',
    });
    expect(items).toContainEqual({
      amount: 125,
      category: 'Side Income',
      id: 8,
      interval: 'Month',
    });
  });

  it('generates bi-weekly paydays for the selected year', () => {
    const paydays = generateRecurringPaydays(
      {
        firstPayDate: '2026-01-09',
        label: 'Paycheck',
        replaceExistingYear: true,
        startingCheckNumber: '1',
        type: 'Paycheck',
        year: '2026',
      },
      -1
    );

    expect(paydays).toHaveLength(26);
    expect(paydays[0]).toMatchObject({
      checkNumber: 1,
      checksInMonth: 2,
      date: '2026-01-09',
      id: -1,
      label: 'Paycheck',
      type: 'Paycheck',
    });
    expect(paydays[1]).toMatchObject({
      checkNumber: 2,
      checksInMonth: 2,
      date: '2026-01-23',
      id: -2,
    });
    expect(paydays.at(-1)).toMatchObject({
      checkNumber: 26,
      checksInMonth: 2,
      date: '2026-12-25',
      id: -26,
    });
  });

  it('identifies numbered income events in a year without matching one-time income', () => {
    const paycheck: DraftIncomeEvent = {
      checkNumber: 1,
      checksInMonth: 0,
      date: '2026-01-09',
      id: 1,
      label: 'Paycheck',
      type: 'Paycheck',
    };
    const taxReturn: DraftIncomeEvent = {
      checkNumber: null,
      checksInMonth: 0,
      date: '2026-02-11',
      id: 2,
      label: 'Tax Return',
      type: 'Tax Return',
    };

    expect(isNumberedIncomeEventInYear(paycheck, '2026')).toBe(true);
    expect(isNumberedIncomeEventInYear(taxReturn, '2026')).toBe(false);
    expect(isNumberedIncomeEventInYear(paycheck, '2027')).toBe(false);
  });

  it('builds a snapshot save payload from draft state', () => {
    const payload = buildExpenseSnapshotRequest({
      annualWithdrawals: [],
      assetCategories: [],
      bills: [bill({ amount: 25, bill: 'Internet', id: -1 })],
      debtAccounts: [],
      incomeEvents: [],
      incomeSummaryItems: [
        {
          amount: 125,
          category: 'Side Income',
          id: 8,
          interval: 'Month',
        },
      ],
      importantDates: [],
      payPeriodEnd: '2026-06-26',
      payPeriodStart: '2026-06-12',
      version: 7,
    });

    expect(payload).toMatchObject({
      version: 7,
      payPeriodEnd: '2026-06-26',
      payPeriodStart: '2026-06-12',
      bills: [{ amount: 25, bill: 'Internet', id: null }],
      incomeSummaryItems: expect.arrayContaining([
        {
          amount: 125,
          category: 'Side Income',
          id: 8,
          interval: 'Month',
        },
      ]),
    });
  });

  it('creates draft state from a loaded snapshot', () => {
    const draft = createFinancialsDraft({
      version: 1,
      annualPayPeriodTotal: 0,
      annualWithdrawals: [],
      assetCategories: [
        {
          key: 'cash-savings',
          label: 'Cash & Savings',
          total: 1300,
          accounts: [
            {
              account: 'Rent Reserve',
              amount: 1300,
              company: 'Credit Union',
              id: -100001,
            },
          ],
        },
      ],
      bills: [
        {
          account: 'Check',
          amount: 0,
          bill: 'Rent',
          dueDate: '2026-06-12',
          dueDay: 1,
          dueLabel: '1st',
          id: -100000,
          inPayPeriod: false,
          paid: false,
        },
      ],
      debtAccounts: [],
      importantDates: [],
      incomeEvents: [
        {
          checkNumber: 1,
          checksInMonth: 0,
          date: '2026-06-12',
          id: 1,
          label: 'Paycheck',
          type: 'Paycheck',
        },
        {
          checkNumber: 2,
          checksInMonth: 0,
          date: '2026-06-26',
          id: 2,
          label: 'Paycheck',
          type: 'Paycheck',
        },
      ],
      incomeSummaryItems: [
        {
          amount: 0,
          category: 'Net Income',
          id: -100002,
          interval: 'Bi-Weekly',
        },
      ],
      netWorth: 0,
      paidTotal: 0,
      payPeriodEnd: '2026-06-26',
      payPeriodStart: '2026-06-12',
      payPeriodTotal: 0,
      totalAnnualWithdrawals: 0,
      totalDebt: 0,
      totalMonthlyExpenses: 0,
      totalTrackedAssets: 0,
      unpaidTotal: 0,
    });

    expect(draft.payPeriodStart).toBe('2026-06-12');
    expect(draft.draftBills).toContainEqual(expect.objectContaining({ bill: 'Rent', id: -100000 }));
    expect(draft.draftAssetCategories[0]?.accounts).toContainEqual(
      expect.objectContaining({ account: 'Rent Reserve', id: -100001 })
    );
    expect(draft.draftIncomeSummaryItems).toContainEqual({
      amount: 0,
      category: 'Net Income',
      id: -100002,
      interval: 'Bi-Weekly',
    });
    expect(draft.incomeSummaryForm).toMatchObject({
      amount: '0',
      category: 'Net Income',
      interval: 'Bi-Weekly',
    });
    expect(draft.draftIncomeEvents).toEqual([
      expect.objectContaining({ checksInMonth: 2, id: 1 }),
      expect.objectContaining({ checksInMonth: 2, id: 2 }),
    ]);
  });
});
