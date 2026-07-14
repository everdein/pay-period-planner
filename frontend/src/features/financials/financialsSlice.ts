import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';

import {
  type ExpenseSnapshot,
  type ExpenseSnapshotRequest,
  financialsService,
} from '../../api/endpoints/financials';

type FinancialsState = {
  snapshot: ExpenseSnapshot | null;
  status: 'idle' | 'loading' | 'succeeded' | 'failed';
  saving: boolean;
  error: string | null;
};

const initialState: FinancialsState = {
  snapshot: null,
  status: 'idle',
  saving: false,
  error: null,
};

export const fetchMonthlyExpenses = createAsyncThunk<
  ExpenseSnapshot,
  void,
  { state: { financials: FinancialsState } }
>(
  'financials/fetchMonthlyExpenses',
  async () => {
    return await financialsService.getMonthlyExpenses();
  },
  {
    condition: (_, { getState }) => getState().financials.status !== 'loading',
  }
);

export const saveExpenseSnapshot = createAsyncThunk(
  'financials/saveExpenseSnapshot',
  async (payload: ExpenseSnapshotRequest) => {
    return await financialsService.saveSnapshot(payload);
  }
);

const financialsSlice = createSlice({
  name: 'financials',
  initialState,
  reducers: {
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
        state.error = null;
      })
      .addCase(fetchMonthlyExpenses.fulfilled, (state, action) => {
        state.status = 'succeeded';
        state.snapshot = action.payload;
      })
      .addCase(fetchMonthlyExpenses.rejected, (state, action) => {
        state.status = 'failed';
        state.snapshot = null;
        state.error = action.error.message ?? 'Unable to load monthly expenses';
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
        state.error = action.error.message ?? 'Unable to save monthly expenses';
      });
  },
});

export const { selectFinancialsSnapshot } = financialsSlice.selectors;
export const { resetFinancials } = financialsSlice.actions;

export default financialsSlice.reducer;
