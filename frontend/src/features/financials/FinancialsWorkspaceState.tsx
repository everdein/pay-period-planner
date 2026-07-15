import type { ReactNode } from 'react';

import type { PayPeriodRequest } from '../../api/endpoints/financials';
import type { FinancialsFailure } from './financialsSlice';
import { WorkspaceOnboarding } from './WorkspaceOnboarding';

export function FinancialsWorkspaceState({
  children,
  error,
  hasSnapshot,
  initializing,
  loading,
  onInitialize,
  onRetry,
  snapshotMissing,
  workspaceName,
}: {
  children: ReactNode;
  error: FinancialsFailure | null;
  hasSnapshot: boolean;
  initializing: boolean;
  loading: boolean;
  onInitialize: (payPeriod: PayPeriodRequest) => Promise<void>;
  onRetry: () => void;
  snapshotMissing: boolean;
  workspaceName: string;
}) {
  if (snapshotMissing) {
    return (
      <WorkspaceOnboarding
        error={error?.kind === 'not-found' ? null : error}
        initializing={initializing}
        onInitialize={onInitialize}
        workspaceName={workspaceName}
      />
    );
  }

  if (loading && !hasSnapshot) {
    return (
      <section className="workspace-load-state" aria-live="polite">
        <span aria-hidden="true" className="loading-indicator" />
        <div>
          <p className="eyebrow">{workspaceName}</p>
          <h2>Loading workspace</h2>
        </div>
      </section>
    );
  }

  if (!hasSnapshot) {
    return (
      <section className="workspace-error-state" aria-labelledby="workspace-error-heading">
        <p className="eyebrow">{workspaceName}</p>
        <h2 id="workspace-error-heading">Financials unavailable</h2>
        {error && (
          <p className="error" role="alert">
            {error.message}
            {error.requestId && <small>Request reference: {error.requestId}</small>}
          </p>
        )}
        <button onClick={onRetry} type="button">
          Try Again
        </button>
      </section>
    );
  }

  return children;
}
