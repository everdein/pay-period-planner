import { act, renderHook } from '@testing-library/react';
import { type FormEvent } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { FinancialsDraft } from './financialsDraft';
import {
  createTestFinancialsDraft,
  useCanonicalDraftTestState,
} from './financialsDraftTestSupport';
import type { DraftAnnualWithdrawal } from './financialsTypes';
import { useAnnualWithdrawalsDraft } from './useAnnualWithdrawalsDraft';

const preventDefault = vi.fn();
const submitEvent = { preventDefault } as unknown as FormEvent<HTMLFormElement>;

function annualWithdrawal(overrides: Partial<DraftAnnualWithdrawal> = {}): DraftAnnualWithdrawal {
  return {
    account: 'Checking',
    amount: 120,
    bill: 'Annual service',
    dateLabel: '06/10/2026',
    day: 10,
    dueDate: '2026-06-10',
    id: 2,
    inPayPeriod: true,
    month: 6,
    paid: true,
    ...overrides,
  };
}

function useAnnualHarness(initialDraft: FinancialsDraft) {
  const { dispatch, state } = useCanonicalDraftTestState(initialDraft);
  return {
    ...useAnnualWithdrawalsDraft(state.draft, dispatch, state.resetGeneration),
    dispatch,
    state,
  };
}

describe('useAnnualWithdrawalsDraft', () => {
  beforeEach(() => {
    preventDefault.mockClear();
  });

  it('selects, sorts, and totals canonical annual withdrawals', () => {
    const draft = createTestFinancialsDraft({
      annualWithdrawals: [
        annualWithdrawal({
          amount: 80,
          day: 5,
          id: 3,
          inPayPeriod: false,
          month: 12,
          paid: false,
        }),
        annualWithdrawal(),
      ],
    });
    const { result } = renderHook(() => useAnnualHarness(draft));

    expect(result.current.annualWithdrawals.map(({ id }) => id)).toEqual([2, 3]);
    expect(result.current.totals).toEqual({
      annualPayPeriodTotal: 120,
      totalAnnualWithdrawals: 200,
    });
    expect(result.current.state.revision).toBe(0);
  });

  it('dispatches a temporary annual withdrawal', () => {
    const { result } = renderHook(() => useAnnualHarness(createTestFinancialsDraft()));

    act(() => result.current.updateAnnualWithdrawalForm('bill', ' Registration '));
    act(() => result.current.updateAnnualWithdrawalForm('date', '2026-06-10'));
    act(() => result.current.updateAnnualWithdrawalForm('amount', '45.50'));
    act(() => result.current.updateAnnualWithdrawalForm('account', ' Checking '));
    act(() => result.current.submitAnnualWithdrawal(submitEvent));

    expect(preventDefault).toHaveBeenCalledOnce();
    expect(result.current.annualWithdrawals).toEqual([
      expect.objectContaining({
        account: 'Checking',
        amount: 45.5,
        bill: 'Registration',
        day: 10,
        dueDate: '2026-06-10',
        id: -1,
        inPayPeriod: true,
        month: 6,
      }),
    ]);
    expect(result.current.editingAnnualWithdrawalId).toBeNull();
    expect(result.current.state.revision).toBe(1);
  });

  it('edits and centrally removes an annual withdrawal', () => {
    const withdrawal = annualWithdrawal();
    const { result } = renderHook(() =>
      useAnnualHarness(createTestFinancialsDraft({ annualWithdrawals: [withdrawal] }))
    );

    act(() => result.current.startAnnualWithdrawalEdit(withdrawal));
    act(() => result.current.updateAnnualWithdrawalForm('amount', '150'));
    act(() => result.current.submitAnnualWithdrawal(submitEvent));
    expect(result.current.annualWithdrawals).toEqual([
      expect.objectContaining({ amount: 150, id: withdrawal.id }),
    ]);

    act(() =>
      result.current.dispatch({
        removal: { id: withdrawal.id, name: withdrawal.bill, type: 'annual-withdrawal' },
        type: 'request-removal',
      })
    );
    act(() => result.current.dispatch({ type: 'confirm-removal' }));

    expect(result.current.annualWithdrawals).toEqual([]);
    expect(result.current.state.revision).toBe(2);
  });

  it('recalculates annual dates when canonical pay-period commands cross a year', () => {
    const draft = createTestFinancialsDraft({
      annualWithdrawals: [annualWithdrawal({ day: 5, inPayPeriod: false, month: 1 })],
      payPeriodEnd: '2026-12-15',
      payPeriodStart: '2026-12-01',
    });
    const { result } = renderHook(() => useAnnualHarness(draft));

    act(() => result.current.dispatch({ type: 'update-pay-period-start', value: '2026-12-20' }));
    act(() => result.current.dispatch({ type: 'update-pay-period-end', value: '2027-01-10' }));

    expect(result.current.annualWithdrawals[0]).toEqual(
      expect.objectContaining({ dueDate: '2027-01-05', inPayPeriod: true })
    );
    expect(result.current.state.revision).toBe(2);
  });
});
