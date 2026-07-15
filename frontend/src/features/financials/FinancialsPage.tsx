import './FinancialsPage.css';

import { useEffect, useState } from 'react';

import type { AccountSession } from '../../api/auth';
import { financialsService } from '../../api/endpoints/financials';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { ConfirmRemoveModal } from './ConfirmRemoveModal';
import { FinancialsNavigation } from './FinancialsNavigation';
import {
  clearFinancialsError,
  fetchMonthlyExpenses,
  initializeExpenseSnapshot,
  saveExpenseSnapshot,
} from './financialsSlice';
import { FinancialsTabContent } from './FinancialsTabContent';
import type { FinancialTab } from './financialsTypes';
import { SaveControls } from './SaveControls';
import { useFinancialsDraftWorkspace } from './useFinancialsDraftWorkspace';
import { WorkflowNotice } from './WorkflowNotice';
import { WorkspaceOnboarding } from './WorkspaceOnboarding';

type FinancialsPageProps = {
  account: AccountSession;
  activeWorkspaceId: number;
  onSignOut: () => Promise<void>;
  onWorkspaceChange: (workspaceId: number) => void;
  sessionError: string | null;
  signingOut: boolean;
};

export default function FinancialsPage({
  account,
  activeWorkspaceId,
  onSignOut,
  onWorkspaceChange,
  sessionError,
  signingOut,
}: FinancialsPageProps) {
  const dispatch = useAppDispatch();
  const { error, initializing, saving, snapshot, snapshotMissing, status } = useAppSelector(
    (state) => state.financials
  );
  const [activeTab, setActiveTab] = useState<FinancialTab>('overview');
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const [onboardingComplete, setOnboardingComplete] = useState(false);
  const [saveNotice, setSaveNotice] = useState<string | null>(null);
  const workspace = useFinancialsDraftWorkspace(snapshot);
  const activeWorkspace = account.workspaces.find(({ id }) => id === activeWorkspaceId);

  useEffect(() => {
    setOnboardingComplete(false);
    setSaveNotice(null);
    dispatch(fetchMonthlyExpenses());
  }, [activeWorkspaceId, dispatch]);

  async function saveDraft() {
    const request = workspace.buildSaveRequest();
    if (request) {
      setSaveNotice(null);
      const result = await dispatch(saveExpenseSnapshot(request));
      if (saveExpenseSnapshot.fulfilled.match(result)) {
        setOnboardingComplete(false);
        setSaveNotice(`Changes saved. Snapshot version ${result.payload.version}.`);
      }
    }
  }

  async function initializeWorkspace(payPeriod: { endDate: string; startDate: string }) {
    const result = await dispatch(initializeExpenseSnapshot(payPeriod));
    if (initializeExpenseSnapshot.fulfilled.match(result)) {
      setOnboardingComplete(true);
      setActiveTab('income-summary');
    }
  }

  async function exportBackup() {
    if (!snapshot) {
      return;
    }

    setExporting(true);
    setExportError(null);

    try {
      const blob = await financialsService.downloadSnapshotJson();
      downloadBlob(blob, `financial-snapshot-v${snapshot.version}.json`);
    } catch (unknownError) {
      setExportError(
        unknownError instanceof Error ? unknownError.message : 'Unable to export financial backup'
      );
    } finally {
      setExporting(false);
    }
  }

  function reloadLatestSnapshot() {
    setSaveNotice(null);
    dispatch(clearFinancialsError());
    void dispatch(fetchMonthlyExpenses());
  }

  return (
    <main className="expenses-shell">
      <header className="app-header">
        <div>
          <p className="eyebrow">Personal finance</p>
          <h1>Financials</h1>
        </div>
        <div className="header-actions">
          {snapshot && (
            <SaveControls
              exporting={exporting}
              isDirty={workspace.isDirty}
              onExport={() => void exportBackup()}
              onReset={workspace.resetDraft}
              onSave={() => void saveDraft()}
              saving={saving}
            />
          )}
          <div className="account-context">
            <div className="account-identity">
              <strong>{account.displayName}</strong>
              <span>{account.email}</span>
            </div>
            {account.workspaces.length > 1 ? (
              <label className="workspace-picker">
                Workspace
                <select
                  onChange={(event) => onWorkspaceChange(Number(event.target.value))}
                  value={activeWorkspaceId}
                >
                  {account.workspaces.map((availableWorkspace) => (
                    <option key={availableWorkspace.id} value={availableWorkspace.id}>
                      {availableWorkspace.name}
                    </option>
                  ))}
                </select>
              </label>
            ) : (
              <div className="workspace-name">
                <span>Workspace</span>
                <strong>{activeWorkspace?.name ?? 'Personal'}</strong>
              </div>
            )}
            <button
              className="ghost"
              disabled={signingOut}
              onClick={() => void onSignOut()}
              type="button"
            >
              {signingOut ? 'Signing Out...' : 'Sign Out'}
            </button>
          </div>
        </div>
      </header>

      {sessionError && (
        <p className="error" role="alert">
          {sessionError}
        </p>
      )}
      {snapshotMissing ? (
        <WorkspaceOnboarding
          error={error?.kind === 'not-found' ? null : (error?.message ?? null)}
          initializing={initializing}
          onInitialize={initializeWorkspace}
          workspaceName={activeWorkspace?.name ?? 'Personal'}
        />
      ) : status === 'loading' && !snapshot ? (
        <section className="workspace-load-state" aria-live="polite">
          <span aria-hidden="true" className="loading-indicator" />
          <div>
            <p className="eyebrow">{activeWorkspace?.name ?? 'Personal'}</p>
            <h2>Loading workspace</h2>
          </div>
        </section>
      ) : !snapshot ? (
        <section className="workspace-error-state" aria-labelledby="workspace-error-heading">
          <p className="eyebrow">{activeWorkspace?.name ?? 'Personal'}</p>
          <h2 id="workspace-error-heading">Financials unavailable</h2>
          {error && <p className="error">{error.message}</p>}
          <button onClick={() => void dispatch(fetchMonthlyExpenses())} type="button">
            Try Again
          </button>
        </section>
      ) : (
        <div className="financials-layout">
          <FinancialsNavigation activeTab={activeTab} onChange={setActiveTab} />

          <section className="financials-content">
            {onboardingComplete && (
              <section className="workspace-ready" aria-live="polite">
                <div>
                  <strong>Workspace ready</strong>
                  <span>Add income or a monthly withdrawal to begin.</span>
                </div>
                <div className="workspace-ready-actions">
                  <button
                    className="ghost"
                    onClick={() => setActiveTab('income-summary')}
                    type="button"
                  >
                    Add Income
                  </button>
                  <button
                    className="ghost"
                    onClick={() => setActiveTab('monthly-withdrawals')}
                    type="button"
                  >
                    Add Monthly Withdrawal
                  </button>
                </div>
              </section>
            )}
            {workspace.isDirty && !error && (
              <p className="status" role="status">
                You have unsaved changes.
              </p>
            )}
            {saveNotice && !workspace.isDirty && (
              <WorkflowNotice title={saveNotice} tone="success" />
            )}
            {error?.operation === 'save' && error.kind === 'conflict' && (
              <WorkflowNotice
                actions={
                  <button className="danger" onClick={reloadLatestSnapshot} type="button">
                    Discard Draft and Reload
                  </button>
                }
                detail={error.message}
                message="Your draft is still visible. Reloading will replace it with the latest saved snapshot."
                title="A newer snapshot is available"
                tone="conflict"
              />
            )}
            {error?.operation === 'save' && error.kind !== 'conflict' && (
              <WorkflowNotice
                actions={
                  <>
                    <button onClick={() => void saveDraft()} type="button">
                      Try Save Again
                    </button>
                    <button
                      className="ghost"
                      onClick={() => dispatch(clearFinancialsError())}
                      type="button"
                    >
                      Dismiss
                    </button>
                  </>
                }
                message={error.message}
                title="Changes not saved"
                tone="error"
              />
            )}
            {exportError && (
              <WorkflowNotice
                actions={
                  <>
                    <button onClick={() => void exportBackup()} type="button">
                      Try Export Again
                    </button>
                    <button className="ghost" onClick={() => setExportError(null)} type="button">
                      Dismiss
                    </button>
                  </>
                }
                message={exportError}
                title="Backup not exported"
                tone="error"
              />
            )}
            {snapshot && (
              <FinancialsTabContent
                activeTab={activeTab}
                onNavigate={setActiveTab}
                workspace={workspace}
              />
            )}
          </section>
        </div>
      )}

      {workspace.removalConfirmation && (
        <ConfirmRemoveModal
          itemName={workspace.removalConfirmation.itemName}
          itemType={workspace.removalConfirmation.itemType}
          onCancel={workspace.cancelRemoval}
          onConfirm={workspace.confirmRemoval}
        />
      )}
    </main>
  );
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
