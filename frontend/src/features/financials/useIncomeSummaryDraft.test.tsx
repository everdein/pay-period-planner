import { act, renderHook } from '@testing-library/react';
import { type FormEvent } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { FinancialsDraft } from './financialsDraft';
import {
  createTestFinancialsDraft,
  useCanonicalDraftTestState,
} from './financialsDraftTestSupport';
import type { DraftIncomeSummaryItem } from './financialsTypes';
import { useIncomeSummaryDraft } from './useIncomeSummaryDraft';

const preventDefault = vi.fn();
const submitEvent = { preventDefault } as unknown as FormEvent<HTMLFormElement>;

function item(overrides: Partial<DraftIncomeSummaryItem> = {}): DraftIncomeSummaryItem {
  return {
    amount: 1000,
    category: 'Net Income',
    id: 1,
    interval: 'Bi-Weekly',
    ...overrides,
  };
}

function useIncomeSummaryHarness(initialDraft: FinancialsDraft, withdrawals: number) {
  const { dispatch, state } = useCanonicalDraftTestState(initialDraft);
  return {
    ...useIncomeSummaryDraft(
      state.draft,
      dispatch,
      state.resetGeneration,
      withdrawals,
      state.draft?.projectionRoles.primaryPaycheckIncomeSummaryItemId ?? 0
    ),
    dispatch,
    state,
  };
}

function derivedAmount(
  result: {
    readonly current: ReturnType<typeof useIncomeSummaryHarness>;
  },
  category: string,
  interval: string
) {
  return result.current.derivedIncomeSummaryItems.find(
    (summary) => summary.category === category && summary.interval === interval
  )?.amount;
}

describe('useIncomeSummaryDraft', () => {
  beforeEach(() => {
    preventDefault.mockClear();
  });

  it('selects sources and recalculates derived income when withdrawals change', () => {
    const draft = createTestFinancialsDraft({
      incomeSummaryItems: [
        item({ amount: 100, category: 'Side Income', id: 2, interval: 'Month' }),
        item(),
      ],
    });
    const { result, rerender } = renderHook(
      ({ withdrawals }) => useIncomeSummaryHarness(draft, withdrawals),
      { initialProps: { withdrawals: 600 } }
    );

    expect(result.current.sourceIncomeSummaryItems.map(({ category }) => category)).toEqual([
      'Net Income',
      'Side Income',
    ]);
    expect(result.current.primaryPaycheckIncome?.amount).toBe(1000);
    expect(derivedAmount(result, 'Net Income', 'Month')).toBeCloseTo(2166.67);
    expect(derivedAmount(result, 'Disposable Income', 'Month')).toBeCloseTo(1566.67);

    rerender({ withdrawals: 800 });

    expect(derivedAmount(result, 'Disposable Income', 'Month')).toBeCloseTo(1366.67);
    expect(result.current.state.revision).toBe(0);
  });

  it('dispatches a temporary source and restores the primary paycheck form', () => {
    const draft = createTestFinancialsDraft({ incomeSummaryItems: [item()] });
    const { result } = renderHook(() => useIncomeSummaryHarness(draft, 0));

    act(() => result.current.updateIncomeSummaryForm('category', ' Bonus '));
    act(() => result.current.updateIncomeSummaryForm('interval', ' Month '));
    act(() => result.current.updateIncomeSummaryForm('amount', '250'));
    act(() => result.current.submitIncomeSummaryItem(submitEvent));

    expect(preventDefault).toHaveBeenCalledOnce();
    expect(result.current.sourceIncomeSummaryItems).toContainEqual({
      amount: 250,
      category: 'Bonus',
      id: -1,
      interval: 'Month',
    });
    expect(result.current.incomeSummaryForm).toEqual({
      amount: '1000',
      category: 'Net Income',
      interval: 'Bi-Weekly',
    });
    expect(result.current.state.revision).toBe(1);
  });

  it('updates a matching source instead of creating a duplicate', () => {
    const draft = createTestFinancialsDraft({
      incomeSummaryItems: [
        item(),
        item({ amount: 100, category: 'Side Income', id: 2, interval: 'Month' }),
      ],
    });
    const { result } = renderHook(() => useIncomeSummaryHarness(draft, 0));

    act(() => result.current.updateIncomeSummaryForm('category', ' side income '));
    act(() => result.current.updateIncomeSummaryForm('interval', ' month '));
    act(() => result.current.updateIncomeSummaryForm('amount', '200'));
    act(() => result.current.submitIncomeSummaryItem(submitEvent));

    expect(result.current.sourceIncomeSummaryItems).toHaveLength(2);
    expect(result.current.sourceIncomeSummaryItems).toContainEqual({
      amount: 200,
      category: 'side income',
      id: 2,
      interval: 'month',
    });
    expect(result.current.state.revision).toBe(1);
  });

  it('allows the selected primary paycheck labels to be edited without changing its role', () => {
    const primaryPaycheck = item();
    const draft = createTestFinancialsDraft({ incomeSummaryItems: [primaryPaycheck] });
    const { result } = renderHook(() => useIncomeSummaryHarness(draft, 0));

    act(() => result.current.startIncomeSummaryItemEdit(primaryPaycheck));
    act(() => result.current.updateIncomeSummaryForm('category', 'Changed'));
    act(() => result.current.updateIncomeSummaryForm('interval', 'Annual'));
    act(() => result.current.updateIncomeSummaryForm('amount', '1200'));
    act(() => result.current.submitIncomeSummaryItem(submitEvent));

    expect(result.current.sourceIncomeSummaryItems).toEqual([
      { amount: 1200, category: 'Changed', id: 1, interval: 'Annual' },
    ]);
    expect(result.current.primaryPaycheckIncome?.id).toBe(1);
    expect(result.current.state.revision).toBe(1);
  });

  it('protects the primary paycheck and confirms an ordinary source removal', () => {
    const primaryPaycheck = item();
    const sideIncome = item({ amount: 100, category: 'Side Income', id: 2, interval: 'Month' });
    const draft = createTestFinancialsDraft({
      incomeSummaryItems: [primaryPaycheck, sideIncome],
    });
    const { result } = renderHook(() => useIncomeSummaryHarness(draft, 0));

    act(() => result.current.startIncomeSummaryItemEdit(sideIncome));
    act(() =>
      result.current.dispatch({
        removal: {
          id: primaryPaycheck.id,
          name: primaryPaycheck.category,
          type: 'income-summary',
        },
        type: 'request-removal',
      })
    );
    expect(result.current.state.pendingRemoval).toBeNull();

    act(() =>
      result.current.dispatch({
        removal: { id: sideIncome.id, name: sideIncome.category, type: 'income-summary' },
        type: 'request-removal',
      })
    );
    act(() => result.current.dispatch({ type: 'confirm-removal' }));

    expect(result.current.sourceIncomeSummaryItems).toEqual([primaryPaycheck]);
    expect(result.current.editingIncomeSummaryItemId).toBeNull();
    expect(result.current.state.revision).toBe(1);
  });
});
