import './FinancialsPage.css';

import { useEffect, useRef, useState } from 'react';

import type { AccountSession } from '../../api/auth';
import { ApiError } from '../../api/client';
import { financialsService, type PayPeriodRequest } from '../../api/endpoints/financials';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { ThemeToggle } from '../../ThemeToggle';
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
import {
  type FinancialsRequestFailure,
  FinancialsWorkflowFeedback,
} from './FinancialsWorkflowFeedback';
import { FinancialsWorkspaceState } from './FinancialsWorkspaceState';
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
  const { error, initializing, savedDraftRevision, saving, snapshot, snapshotMissing, status } =
    useAppSelector((state) => state.financials);
  const [activeTab, setActiveTab] = useState<FinancialTab>('overview');
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<FinancialsRequestFailure | null>(null);
  const [onboardingComplete, setOnboardingComplete] = useState(false);
  const [saveNotice, setSaveNotice] = useState<string | null>(null);
  const activeWorkspaceIdRef = useRef(activeWorkspaceId);
  activeWorkspaceIdRef.current = activeWorkspaceId;
  const workspace = useFinancialsDraftWorkspace(snapshot, savedDraftRevision);
  const activeWorkspace = account.workspaces.find(({ id }) => id === activeWorkspaceId);

  useEffect(() => {
    setOnboardingComplete(false);
    setExportError(null);
    setExporting(false);
    setSaveNotice(null);
    dispatch(fetchMonthlyExpenses({ workspaceId: activeWorkspaceId }));
  }, [activeWorkspaceId, dispatch]);

  async function saveDraft() {
    const request = workspace.buildSaveRequest();
    if (request) {
      setSaveNotice(null);
      const result = await dispatch(
        saveExpenseSnapshot({
          draftRevision: workspace.draftRevision,
          snapshot: request,
          workspaceId: activeWorkspaceId,
        })
      );
      if (
        saveExpenseSnapshot.fulfilled.match(result) &&
        activeWorkspaceIdRef.current === result.meta.arg.workspaceId
      ) {
        setOnboardingComplete(false);
        setSaveNotice(`Changes saved. Snapshot version ${result.payload.version}.`);
      }
    }
  }

  async function initializeWorkspace(payPeriod: PayPeriodRequest) {
    const result = await dispatch(
      initializeExpenseSnapshot({ payPeriod, workspaceId: activeWorkspaceId })
    );
    if (
      initializeExpenseSnapshot.fulfilled.match(result) &&
      activeWorkspaceIdRef.current === result.meta.arg.workspaceId
    ) {
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
      const blob = await financialsService.downloadSnapshotJson(activeWorkspaceId);
      downloadBlob(blob, `financial-snapshot-v${snapshot.version}.json`);
    } catch (unknownError) {
      if (activeWorkspaceIdRef.current === activeWorkspaceId) {
        setExportError(toRequestFailure(unknownError, 'Unable to export financial backup'));
      }
    } finally {
      if (activeWorkspaceIdRef.current === activeWorkspaceId) {
        setExporting(false);
      }
    }
  }

  function reloadLatestSnapshot() {
    setSaveNotice(null);
    dispatch(clearFinancialsError());
    void dispatch(fetchMonthlyExpenses({ workspaceId: activeWorkspaceId }));
  }

  return (
    <main className={`expenses-shell${snapshot ? ' workspace-shell' : ''}`}>
      <header className="app-header">
        <div>
          <p className="eyebrow">Household planning</p>
          <h1>Pay Period Planner</h1>
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
          <ThemeToggle />
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
      <FinancialsWorkspaceState
        error={error}
        hasSnapshot={snapshot !== null}
        initializing={initializing}
        loading={status === 'loading'}
        onInitialize={initializeWorkspace}
        onRetry={() => void dispatch(fetchMonthlyExpenses({ workspaceId: activeWorkspaceId }))}
        snapshotMissing={snapshotMissing}
        workspaceName={activeWorkspace?.name ?? 'Personal'}
      >
        <div className="financials-layout">
          <FinancialsNavigation activeTab={activeTab} onChange={setActiveTab} />

          <section className="financials-content">
            <FinancialsWorkflowFeedback
              error={error}
              exportError={exportError}
              isDirty={workspace.isDirty}
              onboardingComplete={onboardingComplete}
              onDismissExportError={() => setExportError(null)}
              onDismissSaveError={() => dispatch(clearFinancialsError())}
              onNavigate={setActiveTab}
              onReloadLatestSnapshot={reloadLatestSnapshot}
              onRetryExport={exportBackup}
              onRetrySave={saveDraft}
              saveNotice={saveNotice}
            />
            <FinancialsTabContent
              activeTab={activeTab}
              onNavigate={setActiveTab}
              workspace={workspace}
            />
          </section>
        </div>
      </FinancialsWorkspaceState>

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

function toRequestFailure(error: unknown, fallbackMessage: string): FinancialsRequestFailure {
  if (error instanceof ApiError) {
    return {
      message: error.detail,
      requestId: error.requestId,
      status: error.status,
      ...(error.title ? { title: error.title } : {}),
    };
  }

  return { message: error instanceof Error ? error.message : fallbackMessage };
}
