import './observability.css';

import { Component, type ReactNode } from 'react';

import { createErrorReferenceId, reportFrontendError } from './errorReporter';

type AppErrorBoundaryProps = {
  children: ReactNode;
};

type AppErrorBoundaryState = {
  hasError: boolean;
  referenceId: string | null;
};

export class AppErrorBoundary extends Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  state: AppErrorBoundaryState = { hasError: false, referenceId: null };

  static getDerivedStateFromError(): AppErrorBoundaryState {
    return { hasError: true, referenceId: createErrorReferenceId() };
  }

  componentDidCatch(error: Error) {
    reportFrontendError('render-error', error, this.state.referenceId ?? undefined);
  }

  render() {
    if (this.state.hasError) {
      return (
        <main className="error-boundary-shell">
          <section className="error-boundary-card" aria-labelledby="error-heading" role="alert">
            <p className="error-boundary-eyebrow">Application error</p>
            <h1 id="error-heading">Pay Period Planner could not continue</h1>
            <p>
              Reload the application and try again. If the problem continues, share the reference
              below without sharing financial data or credentials.
            </p>
            <p className="error-boundary-reference">
              Reference: <code>{this.state.referenceId}</code>
            </p>
            <button type="button" onClick={() => globalThis.location.reload()}>
              Reload Application
            </button>
          </section>
        </main>
      );
    }

    return this.props.children;
  }
}
