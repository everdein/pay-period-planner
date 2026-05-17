import { configureStore } from '@reduxjs/toolkit';

import helloReducer from '../features/hello/helloSlice';

export const store = configureStore({
  reducer: {
    hello: helloReducer,
  },
  devTools: import.meta.env.DEV,
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
