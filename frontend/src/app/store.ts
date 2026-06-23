import { configureStore } from '@reduxjs/toolkit';

import financialsReducer from '../features/financials/financialsSlice';

export const store = configureStore({
  reducer: {
    financials: financialsReducer,
  },
  devTools: import.meta.env.DEV,
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
