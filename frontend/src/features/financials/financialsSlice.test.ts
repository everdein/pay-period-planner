import { configureStore } from '@reduxjs/toolkit';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { ExpenseSnapshot } from '../../api/endpoints/financials';

const mockGetMonthlyExpenses = vi.hoisted(() => vi.fn());

vi.mock('../../api/endpoints/financials', () => ({
  financialsService: {
    getMonthlyExpenses: mockGetMonthlyExpenses,
    saveSnapshot: vi.fn(),
  },
}));

import financialsReducer, { fetchMonthlyExpenses, resetFinancials } from './financialsSlice';

function createTestStore() {
  return configureStore({ reducer: { financials: financialsReducer } });
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
}

describe('financialsSlice', () => {
  beforeEach(() => {
    mockGetMonthlyExpenses.mockReset();
  });

  it('deduplicates concurrent snapshot loads and allows a later refresh', async () => {
    const firstResponse = deferred<ExpenseSnapshot>();
    const snapshot = { version: 1 } as ExpenseSnapshot;
    mockGetMonthlyExpenses
      .mockReturnValueOnce(firstResponse.promise)
      .mockResolvedValueOnce(snapshot);
    const store = createTestStore();

    const firstRequest = store.dispatch(fetchMonthlyExpenses());
    const duplicateRequest = store.dispatch(fetchMonthlyExpenses());

    expect(mockGetMonthlyExpenses).toHaveBeenCalledTimes(1);
    expect(store.getState().financials.status).toBe('loading');
    await expect(duplicateRequest).resolves.toMatchObject({
      meta: { condition: true, requestStatus: 'rejected' },
    });

    firstResponse.resolve(snapshot);
    await expect(firstRequest).resolves.toMatchObject({ meta: { requestStatus: 'fulfilled' } });
    expect(store.getState().financials).toMatchObject({ snapshot, status: 'succeeded' });

    await store.dispatch(fetchMonthlyExpenses());

    expect(mockGetMonthlyExpenses).toHaveBeenCalledTimes(2);
  });

  it('clears a loaded snapshot at an account or workspace boundary', () => {
    const loadedState = {
      error: null,
      saving: false,
      snapshot: { version: 9 } as ExpenseSnapshot,
      status: 'succeeded' as const,
    };

    expect(financialsReducer(loadedState, resetFinancials())).toEqual({
      error: null,
      saving: false,
      snapshot: null,
      status: 'idle',
    });
  });
});
