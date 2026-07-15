import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import type { ExpenseSnapshot } from '../../api/endpoints/financials';
import { useFinancialsDraftWorkspace } from './useFinancialsDraftWorkspace';

const snapshot: ExpenseSnapshot = {
  annualPayPeriodTotal: 0,
  annualWithdrawals: [],
  assetCategories: [
    {
      accounts: [
        {
          account: 'Rent Reserve',
          amount: 600,
          company: 'Example Bank',
          id: 1,
        },
      ],
      key: 'cash-savings',
      label: 'Cash & Savings',
      total: 600,
    },
  ],
  bills: [
    {
      account: 'Checking',
      amount: 1200,
      bill: 'Rent',
      dueDate: '2026-06-01',
      dueDay: 1,
      dueLabel: '1st',
      id: 1,
      inPayPeriod: false,
      paid: false,
    },
    {
      account: 'Savings',
      amount: 250,
      bill: 'Example Savings Transfer',
      dueDate: '2026-06-15',
      dueDay: 15,
      dueLabel: '15th',
      id: 2,
      inPayPeriod: true,
      paid: false,
    },
  ],
  currentDate: '2026-06-18',
  debtAccounts: [],
  importantDates: [{ date: '2026-12-25', event: 'Christmas', id: 1, type: 'Holiday' }],
  incomeEvents: [
    {
      checkNumber: 12,
      checksInMonth: 1,
      date: '2026-06-12',
      id: 1,
      label: 'Paycheck',
      type: 'Paycheck',
    },
  ],
  incomeSummaryItems: [{ amount: 2000, category: 'Net Income', id: 1, interval: 'Bi-Weekly' }],
  netWorth: 600,
  paidTotal: 0,
  payPeriodEnd: '2026-06-26',
  payPeriodStart: '2026-06-12',
  planningSettings: { payCadence: 'BIWEEKLY', timeZone: 'America/New_York' },
  projectionRoles: {
    primaryPaycheckIncomeSummaryItemId: 1,
    rentBillId: 1,
    rentReserveAssetAccountId: 1,
  },
  payPeriodTotal: 250,
  totalAnnualWithdrawals: 0,
  totalDebt: 0,
  totalMonthlyExpenses: 1450,
  totalTrackedAssets: 600,
  unpaidTotal: 1450,
  version: 7,
};

describe('useFinancialsDraftWorkspace', () => {
  it('loads every domain and builds one full-snapshot save request', async () => {
    const { result } = renderHook(() => useFinancialsDraftWorkspace(snapshot));

    await waitFor(() => expect(result.current.monthlyWithdrawals.sortedBills).toHaveLength(2));

    expect(result.current.overview).toMatchObject({
      annualTotal: 0,
      netWorth: 600,
      primaryPaycheckIncome: 2000,
      totalDebt: 0,
      totalTrackedAssets: 600,
      withdrawalTotal: 1450,
    });
    expect(result.current.buildSaveRequest()).toMatchObject({
      assetCategories: [
        {
          accounts: [expect.objectContaining({ account: 'Rent Reserve', id: 1 })],
          key: 'cash-savings',
        },
      ],
      bills: [
        expect.objectContaining({ bill: 'Rent', id: 1 }),
        expect.objectContaining({ bill: 'Example Savings Transfer', id: 2 }),
      ],
      importantDates: [expect.objectContaining({ event: 'Christmas', id: 1 })],
      incomeEvents: [expect.objectContaining({ checkNumber: 12, id: 1 })],
      payPeriodEnd: '2026-06-26',
      payPeriodStart: '2026-06-12',
      projectionRoles: snapshot.projectionRoles,
      version: 7,
    });
    expect(result.current.isDirty).toBe(false);
  });

  it('protects configured roles and allows removal after reassignment', async () => {
    const { result } = renderHook(() => useFinancialsDraftWorkspace(snapshot));

    await waitFor(() => expect(result.current.monthlyWithdrawals.sortedBills).toHaveLength(2));
    const [rent, transfer] = result.current.monthlyWithdrawals.sortedBills;
    if (!rent || !transfer) {
      throw new Error('Expected the Rent and savings transfer fixtures to be loaded.');
    }

    act(() => result.current.monthlyWithdrawals.requestRemoveBill(rent));
    expect(result.current.removalConfirmation).toBeNull();

    act(() => result.current.projectionSettings.updateProjectionRole('rentBillId', transfer.id));
    act(() => result.current.monthlyWithdrawals.requestRemoveBill(rent));
    expect(result.current.removalConfirmation).toEqual({
      itemName: 'Rent',
      itemType: 'withdrawal',
    });
    act(() => result.current.cancelRemoval());

    act(() => result.current.projectionSettings.updateProjectionRole('rentBillId', rent.id));
    act(() => result.current.monthlyWithdrawals.requestRemoveBill(transfer));
    expect(result.current.removalConfirmation).toEqual({
      itemName: 'Example Savings Transfer',
      itemType: 'withdrawal',
    });

    act(() => result.current.confirmRemoval());
    expect(result.current.monthlyWithdrawals.sortedBills.map(({ bill }) => bill)).toEqual(['Rent']);
    expect(result.current.removalConfirmation).toBeNull();
    expect(result.current.isDirty).toBe(true);

    act(() => result.current.resetDraft());
    await waitFor(() => expect(result.current.monthlyWithdrawals.sortedBills).toHaveLength(2));
    expect(result.current.isDirty).toBe(false);
  });

  it('accepts a save response as the new baseline when the draft has not changed again', async () => {
    const { result, rerender } = renderHook(
      ({ currentSnapshot, savedDraftRevision }) =>
        useFinancialsDraftWorkspace(currentSnapshot, savedDraftRevision),
      {
        initialProps: { currentSnapshot: snapshot, savedDraftRevision: null as number | null },
      }
    );
    await waitFor(() => expect(result.current.monthlyWithdrawals.sortedBills).toHaveLength(2));

    act(() => result.current.monthlyWithdrawals.updatePayPeriodStart('2026-06-13'));
    expect(result.current.draftRevision).toBe(1);

    rerender({
      currentSnapshot: { ...snapshot, payPeriodStart: '2026-06-13', version: 8 },
      savedDraftRevision: 1,
    });

    await waitFor(() => expect(result.current.isDirty).toBe(false));
    expect(result.current.draftRevision).toBe(1);
    expect(result.current.monthlyWithdrawals.payPeriodStart).toBe('2026-06-13');
    expect(result.current.buildSaveRequest()?.version).toBe(8);
  });

  it('preserves edits made after save submission while adopting the committed version', async () => {
    const { result, rerender } = renderHook(
      ({ currentSnapshot, savedDraftRevision }) =>
        useFinancialsDraftWorkspace(currentSnapshot, savedDraftRevision),
      {
        initialProps: { currentSnapshot: snapshot, savedDraftRevision: null as number | null },
      }
    );
    await waitFor(() => expect(result.current.monthlyWithdrawals.sortedBills).toHaveLength(2));

    act(() => result.current.monthlyWithdrawals.updatePayPeriodStart('2026-06-13'));
    const submittedRevision = result.current.draftRevision;
    act(() => result.current.monthlyWithdrawals.updatePayPeriodEnd('2026-06-27'));

    rerender({
      currentSnapshot: {
        ...snapshot,
        payPeriodEnd: '2026-06-26',
        payPeriodStart: '2026-06-13',
        version: 8,
      },
      savedDraftRevision: submittedRevision,
    });

    await waitFor(() => expect(result.current.buildSaveRequest()?.version).toBe(8));
    expect(result.current.draftRevision).toBe(2);
    expect(result.current.isDirty).toBe(true);
    expect(result.current.monthlyWithdrawals.payPeriodStart).toBe('2026-06-13');
    expect(result.current.monthlyWithdrawals.payPeriodEnd).toBe('2026-06-27');
  });
});
