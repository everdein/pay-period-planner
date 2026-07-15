import type { FinancialsFailure } from './financialsSlice';
import type { FinancialTab } from './financialsTypes';
import { WorkflowNotice } from './WorkflowNotice';

export type FinancialsRequestFailure = {
  message: string;
  requestId?: string;
  status?: number;
  title?: string;
};

export function FinancialsWorkflowFeedback({
  error,
  exportError,
  isDirty,
  onboardingComplete,
  onDismissExportError,
  onDismissSaveError,
  onNavigate,
  onReloadLatestSnapshot,
  onRetryExport,
  onRetrySave,
  saveNotice,
}: {
  error: FinancialsFailure | null;
  exportError: FinancialsRequestFailure | null;
  isDirty: boolean;
  onboardingComplete: boolean;
  onDismissExportError: () => void;
  onDismissSaveError: () => void;
  onNavigate: (tab: FinancialTab) => void;
  onReloadLatestSnapshot: () => void;
  onRetryExport: () => Promise<void>;
  onRetrySave: () => Promise<void>;
  saveNotice: string | null;
}) {
  return (
    <>
      {onboardingComplete && (
        <section className="workspace-ready" aria-live="polite">
          <div>
            <strong>Workspace ready</strong>
            <span>Add income or a monthly withdrawal to begin.</span>
          </div>
          <div className="workspace-ready-actions">
            <button className="ghost" onClick={() => onNavigate('income-summary')} type="button">
              Add Income
            </button>
            <button
              className="ghost"
              onClick={() => onNavigate('monthly-withdrawals')}
              type="button"
            >
              Add Monthly Withdrawal
            </button>
          </div>
        </section>
      )}
      {isDirty && !error && (
        <p className="status" role="status">
          You have unsaved changes.
        </p>
      )}
      {saveNotice && !isDirty && <WorkflowNotice title={saveNotice} tone="success" />}
      {error?.operation === 'save' && error.kind === 'conflict' && (
        <WorkflowNotice
          actions={
            <button className="danger" onClick={onReloadLatestSnapshot} type="button">
              Discard Draft and Reload
            </button>
          }
          detail={error.message}
          message="Your draft is still visible. Reloading will replace it with the latest saved snapshot."
          requestId={error.requestId}
          title="A newer snapshot is available"
          tone="conflict"
        />
      )}
      {error?.operation === 'save' && error.kind !== 'conflict' && (
        <WorkflowNotice
          actions={
            <>
              <button onClick={() => void onRetrySave()} type="button">
                Try Save Again
              </button>
              <button className="ghost" onClick={onDismissSaveError} type="button">
                Dismiss
              </button>
            </>
          }
          message={error.message}
          requestId={error.requestId}
          title="Changes not saved"
          tone="error"
        />
      )}
      {exportError && (
        <WorkflowNotice
          actions={
            <>
              <button onClick={() => void onRetryExport()} type="button">
                Try Export Again
              </button>
              <button className="ghost" onClick={onDismissExportError} type="button">
                Dismiss
              </button>
            </>
          }
          message={exportError.message}
          requestId={exportError.requestId}
          title="Backup not exported"
          tone="error"
        />
      )}
    </>
  );
}
