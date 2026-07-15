import { act, renderHook } from '@testing-library/react';
import { type FormEvent } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { FinancialsDraft } from './financialsDraft';
import {
  createTestFinancialsDraft,
  useCanonicalDraftTestState,
} from './financialsDraftTestSupport';
import type { DraftAssetAccount, DraftAssetCategory } from './financialsTypes';
import { useAssetAccountsDraft } from './useAssetAccountsDraft';

const preventDefault = vi.fn();
const submitEvent = { preventDefault } as unknown as FormEvent<HTMLFormElement>;

function account(overrides: Partial<DraftAssetAccount> = {}): DraftAssetAccount {
  return {
    account: 'Retirement account',
    amount: 100,
    company: 'Example provider',
    id: 1,
    ...overrides,
  };
}

function category(overrides: Partial<DraftAssetCategory> = {}): DraftAssetCategory {
  return {
    accounts: [account()],
    key: 'retirement',
    label: 'Retirement',
    total: 100,
    ...overrides,
  };
}

function useAssetHarness(initialDraft: FinancialsDraft) {
  const { dispatch, state } = useCanonicalDraftTestState(initialDraft);
  return {
    ...useAssetAccountsDraft(state.draft, dispatch, state.resetGeneration),
    dispatch,
    state,
  };
}

describe('useAssetAccountsDraft', () => {
  beforeEach(() => {
    preventDefault.mockClear();
  });

  it('selects canonical asset categories and totals without changing revision', () => {
    const draft = createTestFinancialsDraft({
      assetCategories: [
        category(),
        category({ accounts: [], key: 'investments', label: 'Investments', total: 50 }),
      ],
    });
    const { result } = renderHook(() => useAssetHarness(draft));

    expect(result.current.assetCategories.map(({ key }) => key)).toEqual([
      'retirement',
      'investments',
    ]);
    expect(result.current.totalTrackedAssets).toBe(150);
    expect(result.current.state.revision).toBe(0);
  });

  it('dispatches a temporary asset and recalculates the category total', () => {
    const { result } = renderHook(() =>
      useAssetHarness(createTestFinancialsDraft({ assetCategories: [category()] }))
    );

    act(() => result.current.updateAssetForm('account', ' Brokerage '));
    act(() => result.current.updateAssetForm('company', ' Example investments '));
    act(() => result.current.updateAssetForm('amount', '50.25'));
    act(() => result.current.submitAsset('retirement', submitEvent));

    expect(preventDefault).toHaveBeenCalledOnce();
    expect(result.current.assetCategories[0]).toEqual(
      expect.objectContaining({
        accounts: [
          expect.objectContaining({ id: 1 }),
          {
            account: 'Brokerage',
            amount: 50.25,
            company: 'Example investments',
            id: -1,
          },
        ],
        total: 150.25,
      })
    );
    expect(result.current.editingAsset).toBeNull();
    expect(result.current.totalTrackedAssets).toBe(150.25);
    expect(result.current.state.revision).toBe(1);
  });

  it('allows the selected rent reserve label to be edited', () => {
    const rentReserve = account({ account: 'Rent Reserve' });
    const draft = createTestFinancialsDraft({
      assetCategories: [
        category({ accounts: [rentReserve], key: 'cash-savings', label: 'Cash & Savings' }),
      ],
    });
    const { result } = renderHook(() => useAssetHarness(draft));

    act(() => result.current.startAssetEdit('cash-savings', rentReserve));
    act(() => result.current.updateAssetForm('account', 'Renamed reserve'));
    act(() => result.current.updateAssetForm('company', 'Updated provider'));
    act(() => result.current.updateAssetForm('amount', '125'));
    act(() => result.current.submitAsset('cash-savings', submitEvent));

    expect(result.current.assetCategories[0]).toEqual(
      expect.objectContaining({
        accounts: [
          {
            account: 'Renamed reserve',
            amount: 125,
            company: 'Updated provider',
            id: rentReserve.id,
          },
        ],
        total: 125,
      })
    );
    expect(result.current.state.revision).toBe(1);
  });

  it('protects Rent Reserve while confirming an ordinary asset removal', () => {
    const rentReserve = account({ account: 'Rent Reserve' });
    const savings = account({ account: 'Emergency savings', amount: 50, id: 2 });
    const draft = createTestFinancialsDraft({
      assetCategories: [
        category({
          accounts: [rentReserve, savings],
          key: 'cash-savings',
          label: 'Cash & Savings',
          total: 150,
        }),
      ],
    });
    const { result } = renderHook(() => useAssetHarness(draft));

    act(() =>
      result.current.dispatch({
        removal: {
          categoryKey: 'cash-savings',
          id: rentReserve.id,
          name: rentReserve.account,
          type: 'asset',
        },
        type: 'request-removal',
      })
    );
    expect(result.current.state.pendingRemoval).toBeNull();

    act(() =>
      result.current.dispatch({
        removal: {
          categoryKey: 'cash-savings',
          id: savings.id,
          name: savings.account,
          type: 'asset',
        },
        type: 'request-removal',
      })
    );
    act(() => result.current.dispatch({ type: 'confirm-removal' }));

    expect(result.current.assetCategories[0]).toEqual(
      expect.objectContaining({ accounts: [rentReserve], total: 100 })
    );
    expect(result.current.state.revision).toBe(1);
  });
});
