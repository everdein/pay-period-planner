import './FinancialsPage.css';

import { useEffect, useState } from 'react';

import type { AccountSession } from '../../api/auth';
import { financialsService } from '../../api/endpoints/financials';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { ConfirmRemoveModal } from './ConfirmRemoveModal';
import { FinancialsNavigation } from './FinancialsNavigation';
import { fetchMonthlyExpenses, saveExpenseSnapshot } from './financialsSlice';
import { FinancialsTabContent } from './FinancialsTabContent';
import type { FinancialTab } from './financialsTypes';
import { SaveControls } from './SaveControls';
import { useFinancialsDraftWorkspace } from './useFinancialsDraftWorkspace';

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
  const { snapshot, status, saving, error } = useAppSelector((state) => state.financials);
  const [activeTab, setActiveTab] = useState<FinancialTab>('overview');
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const workspace = useFinancialsDraftWorkspace(snapshot);
  const activeWorkspace = account.workspaces.find(({ id }) => id === activeWorkspaceId);
  const snapshotMissing = status === 'failed' && error?.includes('HTTP 404');

  useEffect(() => {
    dispatch(fetchMonthlyExpenses());
  }, [activeWorkspaceId, dispatch]);

  async function saveDraft() {
    const request = workspace.buildSaveRequest();
    if (request) {
      await dispatch(saveExpenseSnapshot(request));
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

      {sessionError && <p className="error">{sessionError}</p>}
      {snapshotMissing ? (
        <section className="empty-state" aria-labelledby="empty-workspace-heading">
          <p className="eyebrow">{activeWorkspace?.name ?? 'Personal'}</p>
          <h2 id="empty-workspace-heading">No financial snapshot yet</h2>
          <p>An existing backup can be migrated into this workspace before editing begins.</p>
        </section>
      ) : (
        <div className="financials-layout">
          <FinancialsNavigation activeTab={activeTab} onChange={setActiveTab} />

          <section className="financials-content">
            {workspace.isDirty && <p className="status">You have unsaved changes.</p>}
            {status === 'loading' && <p className="status">Loading financials...</p>}
            {error && <p className="error">{error}</p>}
            {exportError && <p className="error">{exportError}</p>}
            {snapshot && <FinancialsTabContent activeTab={activeTab} workspace={workspace} />}
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
