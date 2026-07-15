import { act, renderHook } from '@testing-library/react';
import { type FormEvent } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { FinancialsDraft } from './financialsDraft';
import {
  createTestFinancialsDraft,
  useCanonicalDraftTestState,
} from './financialsDraftTestSupport';
import type { DraftDebtAccount } from './financialsTypes';
import { useDebtAccountsDraft } from './useDebtAccountsDraft';

const preventDefault = vi.fn();
const submitEvent = { preventDefault } as unknown as FormEvent<HTMLFormElement>;

function account(overrides: Partial<DraftDebtAccount> = {}): DraftDebtAccount {
  return {
    account: 'Credit line',
    amount: 200,
    company: 'Example lender',
    id: 2,
    ...overrides,
  };
}

function useDebtHarness(initialDraft: FinancialsDraft) {
  const { dispatch, state } = useCanonicalDraftTestState(initialDraft);
  return {
    ...useDebtAccountsDraft(state.draft, dispatch, state.resetGeneration),
    dispatch,
    state,
  };
}

describe('useDebtAccountsDraft', () => {
  beforeEach(() => {
    preventDefault.mockClear();
  });

  it('selects, sorts, and totals canonical debt accounts', () => {
    const draft = createTestFinancialsDraft({
      debtAccounts: [account({ account: 'Loan', amount: 100, id: 1 }), account()],
    });
    const { result } = renderHook(() => useDebtHarness(draft));

    expect(result.current.debtAccounts.map(({ account: name }) => name)).toEqual([
      'Credit line',
      'Loan',
    ]);
    expect(result.current.totalDebt).toBe(300);
    expect(result.current.state.revision).toBe(0);
  });

  it('dispatches a temporary debt account', () => {
    const { result } = renderHook(() => useDebtHarness(createTestFinancialsDraft()));

    act(() => result.current.updateDebtForm('account', ' Personal loan '));
    act(() => result.current.updateDebtForm('company', ' Example lender '));
    act(() => result.current.updateDebtForm('amount', '75.50'));
    act(() => result.current.submitDebt(submitEvent));

    expect(preventDefault).toHaveBeenCalledOnce();
    expect(result.current.debtAccounts).toEqual([
      {
        account: 'Personal loan',
        amount: 75.5,
        company: 'Example lender',
        id: -1,
      },
    ]);
    expect(result.current.editingDebtId).toBeNull();
    expect(result.current.totalDebt).toBe(75.5);
    expect(result.current.state.revision).toBe(1);
  });

  it('edits a debt account and recalculates the total', () => {
    const debtAccount = account();
    const { result } = renderHook(() =>
      useDebtHarness(createTestFinancialsDraft({ debtAccounts: [debtAccount] }))
    );

    act(() => result.current.startDebtEdit(debtAccount));
    act(() => result.current.updateDebtForm('amount', '125'));
    act(() => result.current.submitDebt(submitEvent));

    expect(result.current.debtAccounts).toEqual([
      expect.objectContaining({ amount: 125, id: debtAccount.id }),
    ]);
    expect(result.current.totalDebt).toBe(125);
    expect(result.current.state.revision).toBe(1);
  });

  it('clears an active editor on reset and centrally confirms removal', () => {
    const debtAccount = account();
    const { result } = renderHook(() =>
      useDebtHarness(createTestFinancialsDraft({ debtAccounts: [debtAccount] }))
    );

    act(() => result.current.startDebtEdit(debtAccount));
    act(() => result.current.dispatch({ type: 'reset' }));
    expect(result.current.editingDebtId).toBeNull();

    act(() =>
      result.current.dispatch({
        removal: { id: debtAccount.id, name: debtAccount.account, type: 'debt' },
        type: 'request-removal',
      })
    );
    act(() => result.current.dispatch({ type: 'confirm-removal' }));

    expect(result.current.debtAccounts).toEqual([]);
    expect(result.current.totalDebt).toBe(0);
    expect(result.current.state.revision).toBe(1);
  });
});
