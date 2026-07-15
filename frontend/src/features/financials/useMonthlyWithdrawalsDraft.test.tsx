import { act, renderHook } from '@testing-library/react';
import { type FormEvent } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { FinancialsDraft } from './financialsDraft';
import {
  createTestFinancialsDraft,
  useCanonicalDraftTestState,
} from './financialsDraftTestSupport';
import type { DraftBill } from './financialsTypes';
import { useMonthlyWithdrawalsDraft } from './useMonthlyWithdrawalsDraft';

const preventDefault = vi.fn();
const submitEvent = { preventDefault } as unknown as FormEvent<HTMLFormElement>;

function bill(overrides: Partial<DraftBill> = {}): DraftBill {
  return {
    account: 'Checking',
    amount: 100,
    bill: 'Utilities',
    dueDate: '2026-06-10',
    dueDay: 10,
    dueLabel: '10th',
    id: 2,
    inPayPeriod: true,
    paid: true,
    ...overrides,
  };
}

function useMonthlyHarness(initialDraft: FinancialsDraft) {
  const { dispatch, state } = useCanonicalDraftTestState(initialDraft);
  return {
    ...useMonthlyWithdrawalsDraft(state.draft, dispatch, state.resetGeneration),
    dispatch,
    state,
  };
}

describe('useMonthlyWithdrawalsDraft', () => {
  beforeEach(() => {
    preventDefault.mockClear();
  });

  it('selects, sorts, and totals canonical monthly bills without changing revision', () => {
    const draft = createTestFinancialsDraft({
      bills: [bill({ amount: 50, dueDay: 20, id: 3, inPayPeriod: false, paid: false }), bill()],
    });
    const { result } = renderHook(() => useMonthlyHarness(draft));

    expect(result.current.sortedBills.map(({ id }) => id)).toEqual([2, 3]);
    expect(result.current.totals).toEqual({
      paidTotal: 100,
      payPeriodTotal: 100,
      totalMonthlyExpenses: 150,
      unpaidTotal: 50,
    });
    expect(result.current.state.revision).toBe(0);
  });

  it('dispatches a temporary bill into the canonical draft', () => {
    const { result } = renderHook(() => useMonthlyHarness(createTestFinancialsDraft()));

    act(() => result.current.updateForm('bill', ' Internet '));
    act(() => result.current.updateForm('dueDay', '5'));
    act(() => result.current.updateForm('amount', '75.50'));
    act(() => result.current.updateForm('account', ' Checking '));
    act(() => result.current.submitBill(submitEvent));

    expect(preventDefault).toHaveBeenCalledOnce();
    expect(result.current.sortedBills).toEqual([
      expect.objectContaining({
        account: 'Checking',
        amount: 75.5,
        bill: 'Internet',
        dueDate: '2026-06-05',
        id: -1,
        inPayPeriod: true,
      }),
    ]);
    expect(result.current.formTitle).toBe('Add Bill');
    expect(result.current.state.revision).toBe(1);
  });

  it('allows the selected rent bill label to change while protecting its ID', () => {
    const rent = bill({ bill: 'Rent', id: 1 });
    const utilities = bill();
    const { result } = renderHook(() =>
      useMonthlyHarness(createTestFinancialsDraft({ bills: [rent, utilities] }))
    );

    act(() => result.current.startEdit(rent));
    act(() => result.current.updateForm('bill', 'Renamed'));
    act(() => result.current.updateForm('amount', '125'));
    act(() => result.current.submitBill(submitEvent));
    act(() =>
      result.current.dispatch({
        removal: { id: rent.id, name: rent.bill, type: 'bill' },
        type: 'request-removal',
      })
    );
    expect(result.current.state.pendingRemoval).toBeNull();

    act(() =>
      result.current.dispatch({
        removal: { id: utilities.id, name: utilities.bill, type: 'bill' },
        type: 'request-removal',
      })
    );
    act(() => result.current.dispatch({ type: 'confirm-removal' }));

    expect(result.current.sortedBills).toEqual([
      expect.objectContaining({ amount: 125, bill: 'Renamed', id: rent.id }),
    ]);
    expect(result.current.state.revision).toBe(2);
  });

  it('recalculates bill dates through canonical pay-period commands', () => {
    const { result } = renderHook(() =>
      useMonthlyHarness(
        createTestFinancialsDraft({ bills: [bill({ dueDay: 20, inPayPeriod: false })] })
      )
    );

    act(() => result.current.updatePayPeriodStart('2026-06-16'));
    act(() => result.current.updatePayPeriodEnd('2026-06-30'));

    expect(result.current.sortedBills[0]).toEqual(
      expect.objectContaining({ dueDate: '2026-06-20', inPayPeriod: true })
    );
    expect(result.current.state.revision).toBe(2);
  });
});
