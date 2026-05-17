import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';

import { helloService } from '../../api/endpoints/hello';

export const fetchHello = createAsyncThunk('api/getHello', async () => {
  return await helloService.getHello();
});

export const sendHello = createAsyncThunk('api/postHello', async (payload: { message: string }) => {
  return await helloService.postHello(payload);
});

const initialState = {
  // Define any common state properties here if needed
};

const commonSlice = createSlice({
  name: 'common',
  initialState,
  reducers: {
    // Define any common reducers here if needed
  },
  // selectors: {
  // Define any common selectors here if needed
  // },
  // extraReducers: (builder) => {
  // Handle any common async actions here if needed
  // },
});

// export const { } = commonSlice.actions;

// export const { } = commonSlice.selectors;

export default commonSlice.reducer;
