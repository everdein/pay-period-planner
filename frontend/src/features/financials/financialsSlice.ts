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
  status?: number;
};

type FinancialsState = {
  snapshot: ExpenseSnapshot | null;
  status: 'idle' | 'loading' | 'succeeded' | 'failed';
  saving: boolean;
  initializing: boolean;
  snapshotMissing: boolean;
  error: FinancialsFailure | null;
};

const initialState: FinancialsState = {
  snapshot: null,
  status: 'idle',
  saving: false,
  initializing: false,
  snapshotMissing: false,
  error: null,
};

export const fetchMonthlyExpenses = createAsyncThunk<
  ExpenseSnapshot,
  void,
  { rejectValue: FinancialsFailure; state: { financials: FinancialsState } }
>(
  'financials/fetchMonthlyExpenses',
  async (_, { rejectWithValue }) => {
    try {
      return await financialsService.getMonthlyExpenses();
    } catch (error) {
      return rejectWithValue(toFinancialsFailure(error, 'load'));
    }
  },
  {
    condition: (_, { getState }) => getState().financials.status !== 'loading',
  }
);

export const saveExpenseSnapshot = createAsyncThunk<
  ExpenseSnapshot,
  ExpenseSnapshotRequest,
  { rejectValue: FinancialsFailure }
>('financials/saveExpenseSnapshot', async (payload, { rejectWithValue }) => {
  try {
    return await financialsService.saveSnapshot(payload);
  } catch (error) {
    return rejectWithValue(toFinancialsFailure(error, 'save'));
  }
});

export const initializeExpenseSnapshot = createAsyncThunk<
  ExpenseSnapshot,
  PayPeriodRequest,
  { rejectValue: FinancialsFailure }
>('financials/initializeExpenseSnapshot', async (payload, { rejectWithValue }) => {
  try {
    return await financialsService.initializeSnapshot(payload);
  } catch (error) {
    return rejectWithValue(toFinancialsFailure(error, 'initialize'));
  }
});

const financialsSlice = createSlice({
  name: 'financials',
  initialState,
  reducers: {
    clearFinancialsError: (state) => {
      state.error = null;
    },
    resetFinancials: () => initialState,
  },
  selectors: {
    selectFinancialsSnapshot: (state) => state.snapshot,
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchMonthlyExpenses.pending, (state) => {
        state.status = 'loading';
        state.snapshot = null;
        state.snapshotMissing = false;
        state.error = null;
      })
      .addCase(fetchMonthlyExpenses.fulfilled, (state, action) => {
        state.status = 'succeeded';
        state.snapshot = action.payload;
        state.snapshotMissing = false;
      })
      .addCase(fetchMonthlyExpenses.rejected, (state, action) => {
        const failure = action.payload ?? fallbackFailure('load');
        state.status = 'failed';
        state.snapshot = null;
        state.error = failure;
        state.snapshotMissing = failure.kind === 'not-found';
      });

    builder
      .addCase(initializeExpenseSnapshot.pending, (state) => {
        state.initializing = true;
        state.error = null;
      })
      .addCase(initializeExpenseSnapshot.fulfilled, (state, action) => {
        state.initializing = false;
        state.snapshotMissing = false;
        state.status = 'succeeded';
        state.snapshot = action.payload;
      })
      .addCase(initializeExpenseSnapshot.rejected, (state, action) => {
        state.initializing = false;
        state.error = action.payload ?? fallbackFailure('initialize');
      });

    builder
      .addCase(saveExpenseSnapshot.pending, (state) => {
        state.saving = true;
        state.error = null;
      })
      .addCase(saveExpenseSnapshot.fulfilled, (state, action) => {
        state.saving = false;
        state.status = 'succeeded';
        state.snapshot = action.payload;
      })
      .addCase(saveExpenseSnapshot.rejected, (state, action) => {
        state.saving = false;
        state.error = action.payload ?? fallbackFailure('save');
      });
  },
});

export const { selectFinancialsSnapshot } = financialsSlice.selectors;
export const { clearFinancialsError, resetFinancials } = financialsSlice.actions;

export default financialsSlice.reducer;

function toFinancialsFailure(
  error: unknown,
  operation: FinancialsFailure['operation']
): FinancialsFailure {
  const message = error instanceof Error ? error.message : fallbackMessage(operation);
  const status = error instanceof ApiError ? error.status : statusFromMessage(message);

  return {
    kind: failureKind(status),
    message,
    operation,
    ...(status ? { status } : {}),
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

function statusFromMessage(message: string) {
  const match = /HTTP (\d{3})/.exec(message);
  return match ? Number(match[1]) : undefined;
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
