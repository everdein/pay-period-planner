import { createSlice } from '@reduxjs/toolkit';

import type { HelloResponse } from '../../api/endpoints/hello';
import { fetchHello, sendHello } from '../common/commonSlice';

type HelloState = {
  data: HelloResponse | null;
  status: 'idle' | 'loading' | 'succeeded' | 'failed';
  error: string | null;
};

const initialState: HelloState = {
  data: null,
  status: 'idle',
  error: null,
};

const helloSlice = createSlice({
  name: 'hello',
  initialState,
  reducers: {
    // Define any common reducers here if needed
  },
  selectors: {
    selectHelloData: (state: HelloState) => state.data,
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchHello.pending, (state) => {
        state.status = 'loading';
        state.error = null;
      })
      .addCase(fetchHello.fulfilled, (state, action) => {
        state.status = 'succeeded';
        state.data = action.payload;
      })
      .addCase(fetchHello.rejected, (state, action) => {
        state.status = 'failed';
        state.error = action.error.message ?? 'Unknown error';
      });
    builder
      .addCase(sendHello.pending, (state) => {
        state.status = 'loading';
        state.error = null;
      })
      .addCase(sendHello.fulfilled, (state, action) => {
        state.status = 'succeeded';
        state.data = action.payload;
      })
      .addCase(sendHello.rejected, (state, action) => {
        state.status = 'failed';
        state.error = action.error.message ?? 'Unknown error';
      });
  },
});

// export const { } = helloSlice.actions;

export const { selectHelloData } = helloSlice.selectors;

export default helloSlice.reducer;
