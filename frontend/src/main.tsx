import './theme.css';

import React from 'react';
import ReactDOM from 'react-dom/client';
import { Provider } from 'react-redux';

import App from './App';
import { store } from './app/store';
import { AppErrorBoundary } from './observability/AppErrorBoundary';
import { installGlobalErrorReporting } from './observability/errorReporter';
import { initializeTheme } from './theme';

initializeTheme();
installGlobalErrorReporting();

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AppErrorBoundary>
      <Provider store={store}>
        <App />
      </Provider>
    </AppErrorBoundary>
  </React.StrictMode>
);
