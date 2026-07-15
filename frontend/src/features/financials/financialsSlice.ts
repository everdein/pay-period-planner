import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';

import { ApiError } from '../../api/client';
import {
  type ExpenseSnapshot,
  type ExpenseSnapshotRequest,
  financialsService,
  type PayPeriodRequest,
} from '../../api/endpoints/financials';

export type FinancialsFailure = {
  kind: 'conflict' | 'error' | 'not-found' | 'unauthorized' | 'validation';
  message: string;
  operation: 'initialize' | 'load' | 'save';
  requestId?: string;
  status?: number;
  title?: string;
};

type FinancialsState = {
  activeWorkspaceId: number | null;
  snapshot: ExpenseSnapshot | null;
  status: 'idle' | 'loading' | 'succeeded' | 'failed';
  saving: boolean;
  initializing: boolean;
  snapshotMissing: boolean;
  error: FinancialsFailure | null;
  initializeRequestId: string | null;
  loadRequestId: string | null;
  saveRequestId: string | null;
  savedDraftRevision: number | null;
};

const initialState: FinancialsState = {
  activeWorkspaceId: null,
  snapshot: null,
  status: 'idle',
  saving: false,
  initializing: false,
  snapshotMissing: false,
  error: null,
  initializeRequestId: null,
  loadRequestId: null,
  saveRequestId: null,
  savedDraftRevision: null,
};

type WorkspaceRequest = {
  workspaceId: number;
};

type InitializeSnapshotRequest = WorkspaceRequest & {
  payPeriod: PayPeriodRequest;
};

type SaveSnapshotRequest = WorkspaceRequest & {
  draftRevision: number;
  snapshot: ExpenseSnapshotRequest;
};

export const fetchMonthlyExpenses = createAsyncThunk<
  ExpenseSnapshot,
  WorkspaceRequest,
  { rejectValue: FinancialsFailure; state: { financials: FinancialsState } }
>(
  'financials/fetchMonthlyExpenses',
  async ({ workspaceId }, { rejectWithValue }) => {
    try {
      return await financialsService.getMonthlyExpenses(workspaceId);
    } catch (error) {
      return rejectWithValue(toFinancialsFailure(error, 'load'));
    }
  },
  {
    condition: ({ workspaceId }, { getState }) => {
      const state = getState().financials;
      return state.status !== 'loading' || state.activeWorkspaceId !== workspaceId;
    },
  }
);

export const saveExpenseSnapshot = createAsyncThunk<
  ExpenseSnapshot,
  SaveSnapshotRequest,
  { rejectValue: FinancialsFailure; state: { financials: FinancialsState } }
>(
  'financials/saveExpenseSnapshot',
  async ({ snapshot, workspaceId }, { rejectWithValue }) => {
    try {
      return await financialsService.saveSnapshot(workspaceId, snapshot);
    } catch (error) {
      return rejectWithValue(toFinancialsFailure(error, 'save'));
    }
  },
  {
    condition: ({ workspaceId }, { getState }) => {
      const state = getState().financials;
      return state.activeWorkspaceId === workspaceId && state.snapshot !== null && !state.saving;
    },
  }
);

export const initializeExpenseSnapshot = createAsyncThunk<
  ExpenseSnapshot,
  InitializeSnapshotRequest,
  { rejectValue: FinancialsFailure; state: { financials: FinancialsState } }
>(
  'financials/initializeExpenseSnapshot',
  async ({ payPeriod, workspaceId }, { rejectWithValue }) => {
    try {
      return await financialsService.initializeSnapshot(workspaceId, payPeriod);
    } catch (error) {
      return rejectWithValue(toFinancialsFailure(error, 'initialize'));
    }
  },
  {
    condition: ({ workspaceId }, { getState }) => {
      const state = getState().financials;
      return state.activeWorkspaceId === workspaceId && !state.initializing;
    },
  }
);

const financialsSlice = createSlice({
  name: 'financials',
  initialState,
  reducers: {
    clearFinancialsError: (state) => {
      state.error = null;
    },
    resetFinancials: () => ({ ...initialState }),
  },
  selectors: {
    selectFinancialsSnapshot: (state) => state.snapshot,
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchMonthlyExpenses.pending, (state, action) => {
        const workspaceChanged = state.activeWorkspaceId !== action.meta.arg.workspaceId;
        state.activeWorkspaceId = action.meta.arg.workspaceId;
        state.loadRequestId = action.meta.requestId;
        state.status = 'loading';
        state.snapshot = null;
        state.snapshotMissing = false;
        state.error = null;
        state.savedDraftRevision = null;
        if (workspaceChanged) {
          state.initializing = false;
          state.initializeRequestId = null;
          state.saving = false;
          state.saveRequestId = null;
        }
      })
      .addCase(fetchMonthlyExpenses.fulfilled, (state, action) => {
        if (!isCurrentRequest(state, action.meta.arg.workspaceId, action.meta.requestId, 'load')) {
          return;
        }
        state.loadRequestId = null;
        state.status = 'succeeded';
        state.snapshot = action.payload;
        state.snapshotMissing = false;
      })
      .addCase(fetchMonthlyExpenses.rejected, (state, action) => {
        if (!isCurrentRequest(state, action.meta.arg.workspaceId, action.meta.requestId, 'load')) {
          return;
        }
        const failure = action.payload ?? fallbackFailure('load');
        state.loadRequestId = null;
        state.status = 'failed';
        state.snapshot = null;
        state.error = failure;
        state.snapshotMissing = failure.kind === 'not-found';
      });

    builder
      .addCase(initializeExpenseSnapshot.pending, (state, action) => {
        state.initializeRequestId = action.meta.requestId;
        state.initializing = true;
        state.error = null;
      })
      .addCase(initializeExpenseSnapshot.fulfilled, (state, action) => {
        if (
          !isCurrentRequest(state, action.meta.arg.workspaceId, action.meta.requestId, 'initialize')
        ) {
          return;
        }
        state.initializeRequestId = null;
        state.initializing = false;
        state.snapshotMissing = false;
        state.status = 'succeeded';
        state.snapshot = action.payload;
        state.savedDraftRevision = null;
      })
      .addCase(initializeExpenseSnapshot.rejected, (state, action) => {
        if (
          !isCurrentRequest(state, action.meta.arg.workspaceId, action.meta.requestId, 'initialize')
        ) {
          return;
        }
        state.initializeRequestId = null;
        state.initializing = false;
        state.error = action.payload ?? fallbackFailure('initialize');
      });

    builder
      .addCase(saveExpenseSnapshot.pending, (state, action) => {
        state.saveRequestId = action.meta.requestId;
        state.saving = true;
        state.error = null;
      })
      .addCase(saveExpenseSnapshot.fulfilled, (state, action) => {
        if (!isCurrentRequest(state, action.meta.arg.workspaceId, action.meta.requestId, 'save')) {
          return;
        }
        state.saveRequestId = null;
        state.saving = false;
        state.status = 'succeeded';
        state.snapshot = action.payload;
        state.savedDraftRevision = action.meta.arg.draftRevision;
      })
      .addCase(saveExpenseSnapshot.rejected, (state, action) => {
        if (!isCurrentRequest(state, action.meta.arg.workspaceId, action.meta.requestId, 'save')) {
          return;
        }
        state.saveRequestId = null;
        state.saving = false;
        state.error = action.payload ?? fallbackFailure('save');
      });
  },
});

export const { selectFinancialsSnapshot } = financialsSlice.selectors;
export const { clearFinancialsError, resetFinancials } = financialsSlice.actions;

export default financialsSlice.reducer;

function isCurrentRequest(
  state: FinancialsState,
  workspaceId: number,
  requestId: string,
  operation: 'initialize' | 'load' | 'save'
) {
  const activeRequestId =
    operation === 'load'
      ? state.loadRequestId
      : operation === 'save'
        ? state.saveRequestId
        : state.initializeRequestId;

  return state.activeWorkspaceId === workspaceId && activeRequestId === requestId;
}

function toFinancialsFailure(
  error: unknown,
  operation: FinancialsFailure['operation']
): FinancialsFailure {
  if (error instanceof ApiError) {
    return {
      kind: failureKind(error.status),
      message: error.detail,
      operation,
      requestId: error.requestId,
      status: error.status,
      ...(error.title ? { title: error.title } : {}),
    };
  }

  return {
    kind: 'error',
    message: error instanceof Error ? error.message : fallbackMessage(operation),
    operation,
  };
}

function fallbackFailure(operation: FinancialsFailure['operation']): FinancialsFailure {
  return { kind: 'error', message: fallbackMessage(operation), operation };
}

function fallbackMessage(operation: FinancialsFailure['operation']) {
  switch (operation) {
    case 'initialize':
      return 'Unable to create the financial snapshot';
    case 'load':
      return 'Unable to load financials';
    default:
      return 'Unable to save changes';
  }
}

function failureKind(status?: number): FinancialsFailure['kind'] {
  if (status === 404) {
    return 'not-found';
  }
  if (status === 409) {
    return 'conflict';
  }
  if (status === 400 || status === 422) {
    return 'validation';
  }
  if (status === 401 || status === 403) {
    return 'unauthorized';
  }
  return 'error';
}
