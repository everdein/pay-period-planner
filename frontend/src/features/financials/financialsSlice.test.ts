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

    const firstRequest = store.dispatch(fetchMonthlyExpenses({ workspaceId: 11 }));
    const duplicateRequest = store.dispatch(fetchMonthlyExpenses({ workspaceId: 11 }));

    expect(mockGetMonthlyExpenses).toHaveBeenCalledTimes(1);
    expect(mockGetMonthlyExpenses).toHaveBeenCalledWith(11);
    expect(store.getState().financials.status).toBe('loading');
    await expect(duplicateRequest).resolves.toMatchObject({
      meta: { condition: true, requestStatus: 'rejected' },
    });

    firstResponse.resolve(snapshot);
    await expect(firstRequest).resolves.toMatchObject({ meta: { requestStatus: 'fulfilled' } });
    expect(store.getState().financials).toMatchObject({ snapshot, status: 'succeeded' });

    await store.dispatch(fetchMonthlyExpenses({ workspaceId: 11 }));

    expect(mockGetMonthlyExpenses).toHaveBeenCalledTimes(2);
  });

  it('clears a loaded snapshot at an account or workspace boundary', () => {
    const loadedState = {
      activeWorkspaceId: 11,
      error: null,
      initializeRequestId: null,
      initializing: false,
      loadRequestId: null,
      savedDraftRevision: null,
      saveRequestId: null,
      saving: false,
      snapshot: { version: 9 } as ExpenseSnapshot,
      snapshotMissing: false,
      status: 'succeeded' as const,
    };

    expect(financialsReducer(loadedState, resetFinancials())).toEqual({
      activeWorkspaceId: null,
      error: null,
      initializeRequestId: null,
      initializing: false,
      loadRequestId: null,
      savedDraftRevision: null,
      saveRequestId: null,
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
      new ApiError(
        'Workspace financial snapshot not found',
        404,
        'request-missing',
        '404 NOT FOUND'
      )
    );
    mockInitializeSnapshot.mockResolvedValue(snapshot);
    const store = createTestStore();

    await store.dispatch(fetchMonthlyExpenses({ workspaceId: 11 }));

    expect(store.getState().financials).toMatchObject({
      error: {
        kind: 'not-found',
        message: 'Workspace financial snapshot not found',
        requestId: 'request-missing',
        status: 404,
        title: '404 NOT FOUND',
      },
      snapshot: null,
      snapshotMissing: true,
      status: 'failed',
    });

    await store.dispatch(
      initializeExpenseSnapshot({
        payPeriod: {
          startDate: '2026-07-10',
          endDate: '2026-07-23',
          planningSettings: { payCadence: 'BIWEEKLY', timeZone: 'UTC' },
        },
        workspaceId: 11,
      })
    );

    expect(mockInitializeSnapshot).toHaveBeenCalledWith(11, {
      endDate: '2026-07-23',
      planningSettings: { payCadence: 'BIWEEKLY', timeZone: 'UTC' },
      startDate: '2026-07-10',
    });
    expect(store.getState().financials).toMatchObject({
      initializing: false,
      snapshot,
      snapshotMissing: false,
      status: 'succeeded',
    });
  });

  it('does not infer API status from presentation text', async () => {
    mockGetMonthlyExpenses.mockRejectedValue(
      new Error('HTTP 404 Not Found: Workspace financial snapshot not found')
    );
    const store = createTestStore();

    await store.dispatch(fetchMonthlyExpenses({ workspaceId: 11 }));

    expect(store.getState().financials).toMatchObject({
      error: {
        kind: 'error',
        message: 'HTTP 404 Not Found: Workspace financial snapshot not found',
        operation: 'load',
      },
      snapshotMissing: false,
      status: 'failed',
    });
    expect(store.getState().financials.error).not.toHaveProperty('requestId');
    expect(store.getState().financials.error).not.toHaveProperty('status');
  });

  it('keeps the loaded snapshot and classifies an optimistic save conflict', async () => {
    const snapshot = { version: 4 } as ExpenseSnapshot;
    mockGetMonthlyExpenses.mockResolvedValue(snapshot);
    mockSaveSnapshot.mockRejectedValue(
      new ApiError('Snapshot version conflict', 409, 'request-conflict', '409 CONFLICT')
    );
    const store = createTestStore();

    await store.dispatch(fetchMonthlyExpenses({ workspaceId: 11 }));
    await store.dispatch(
      saveExpenseSnapshot({
        draftRevision: 3,
        snapshot: { version: 4 } as ExpenseSnapshotRequest,
        workspaceId: 11,
      })
    );

    expect(store.getState().financials).toMatchObject({
      error: {
        kind: 'conflict',
        message: 'Snapshot version conflict',
        operation: 'save',
        requestId: 'request-conflict',
        status: 409,
        title: '409 CONFLICT',
      },
      saving: false,
      snapshot,
    });

    store.dispatch(clearFinancialsError());

    expect(store.getState().financials.error).toBeNull();
    expect(store.getState().financials.snapshot).toBe(snapshot);
  });

  it('loads a newly selected workspace and ignores the prior delayed response', async () => {
    const firstResponse = deferred<ExpenseSnapshot>();
    const secondResponse = deferred<ExpenseSnapshot>();
    const firstSnapshot = { version: 3 } as ExpenseSnapshot;
    const secondSnapshot = { version: 8 } as ExpenseSnapshot;
    mockGetMonthlyExpenses
      .mockReturnValueOnce(firstResponse.promise)
      .mockReturnValueOnce(secondResponse.promise);
    const store = createTestStore();

    const firstRequest = store.dispatch(fetchMonthlyExpenses({ workspaceId: 11 }));
    const secondRequest = store.dispatch(fetchMonthlyExpenses({ workspaceId: 12 }));

    secondResponse.resolve(secondSnapshot);
    await secondRequest;
    expect(store.getState().financials).toMatchObject({
      activeWorkspaceId: 12,
      snapshot: secondSnapshot,
      status: 'succeeded',
    });

    firstResponse.resolve(firstSnapshot);
    await firstRequest;
    expect(store.getState().financials).toMatchObject({
      activeWorkspaceId: 12,
      snapshot: secondSnapshot,
      status: 'succeeded',
    });
  });

  it('ignores a delayed save completion after the active workspace changes', async () => {
    const firstSnapshot = { version: 4 } as ExpenseSnapshot;
    const savedFirstSnapshot = { version: 5 } as ExpenseSnapshot;
    const secondSnapshot = { version: 9 } as ExpenseSnapshot;
    const saveResponse = deferred<ExpenseSnapshot>();
    mockGetMonthlyExpenses
      .mockResolvedValueOnce(firstSnapshot)
      .mockResolvedValueOnce(secondSnapshot);
    mockSaveSnapshot.mockReturnValue(saveResponse.promise);
    const store = createTestStore();

    await store.dispatch(fetchMonthlyExpenses({ workspaceId: 11 }));
    const saveRequest = store.dispatch(
      saveExpenseSnapshot({
        draftRevision: 2,
        snapshot: { version: 4 } as ExpenseSnapshotRequest,
        workspaceId: 11,
      })
    );
    await store.dispatch(fetchMonthlyExpenses({ workspaceId: 12 }));

    saveResponse.resolve(savedFirstSnapshot);
    await saveRequest;

    expect(mockSaveSnapshot).toHaveBeenCalledWith(11, { version: 4 });
    expect(store.getState().financials).toMatchObject({
      activeWorkspaceId: 12,
      savedDraftRevision: null,
      snapshot: secondSnapshot,
      status: 'succeeded',
    });
  });
});
