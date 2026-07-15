import { configureStore } from '@reduxjs/toolkit';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../api/client';
import type { ExpenseSnapshot, ExpenseSnapshotRequest } from '../../api/endpoints/financials';

const mockGetMonthlyExpenses = vi.hoisted(() => vi.fn());
const mockInitializeSnapshot = vi.hoisted(() => vi.fn());
const mockSaveSnapshot = vi.hoisted(() => vi.fn());

vi.mock('../../api/endpoints/financials', () => ({
  financialsService: {
    getMonthlyExpenses: mockGetMonthlyExpenses,
    initializeSnapshot: mockInitializeSnapshot,
    saveSnapshot: mockSaveSnapshot,
  },
}));

import financialsReducer, {
  clearFinancialsError,
  fetchMonthlyExpenses,
  initializeExpenseSnapshot,
  resetFinancials,
  saveExpenseSnapshot,
} from './financialsSlice';

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
    mockInitializeSnapshot.mockReset();
    mockSaveSnapshot.mockReset();
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
      initializing: false,
      saving: false,
      snapshot: { version: 9 } as ExpenseSnapshot,
      snapshotMissing: false,
      status: 'succeeded' as const,
    };

    expect(financialsReducer(loadedState, resetFinancials())).toEqual({
      error: null,
      initializing: false,
      saving: false,
      snapshot: null,
      snapshotMissing: false,
      status: 'idle',
    });
  });

  it('distinguishes a missing snapshot and initializes the workspace', async () => {
    const snapshot = {
      version: 1,
      payPeriodStart: '2026-07-10',
      payPeriodEnd: '2026-07-23',
    } as ExpenseSnapshot;
    mockGetMonthlyExpenses.mockRejectedValue(
      new Error('HTTP 404 Not Found: Workspace financial snapshot not found')
    );
    mockInitializeSnapshot.mockResolvedValue(snapshot);
    const store = createTestStore();

    await store.dispatch(fetchMonthlyExpenses());

    expect(store.getState().financials).toMatchObject({
      snapshot: null,
      snapshotMissing: true,
      status: 'failed',
    });

    await store.dispatch(
      initializeExpenseSnapshot({ startDate: '2026-07-10', endDate: '2026-07-23' })
    );

    expect(mockInitializeSnapshot).toHaveBeenCalledWith({
      startDate: '2026-07-10',
      endDate: '2026-07-23',
    });
    expect(store.getState().financials).toMatchObject({
      initializing: false,
      snapshot,
      snapshotMissing: false,
      status: 'succeeded',
    });
  });

  it('keeps the loaded snapshot and classifies an optimistic save conflict', async () => {
    const snapshot = { version: 4 } as ExpenseSnapshot;
    mockGetMonthlyExpenses.mockResolvedValue(snapshot);
    mockSaveSnapshot.mockRejectedValue(
      new ApiError('Snapshot version conflict', 409, 'request-conflict')
    );
    const store = createTestStore();

    await store.dispatch(fetchMonthlyExpenses());
    await store.dispatch(saveExpenseSnapshot({ version: 4 } as ExpenseSnapshotRequest));

    expect(store.getState().financials).toMatchObject({
      error: {
        kind: 'conflict',
        operation: 'save',
        status: 409,
      },
      saving: false,
      snapshot,
    });

    store.dispatch(clearFinancialsError());

    expect(store.getState().financials.error).toBeNull();
    expect(store.getState().financials.snapshot).toBe(snapshot);
  });
});
