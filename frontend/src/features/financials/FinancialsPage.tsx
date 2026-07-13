import './FinancialsPage.css';

import { useEffect, useState } from 'react';

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
  onSignOut?: () => void;
};

export default function FinancialsPage({ onSignOut }: FinancialsPageProps) {
  const dispatch = useAppDispatch();
  const { snapshot, status, saving, error } = useAppSelector((state) => state.financials);
  const [activeTab, setActiveTab] = useState<FinancialTab>('overview');
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const workspace = useFinancialsDraftWorkspace(snapshot);

  useEffect(() => {
    dispatch(fetchMonthlyExpenses());
  }, [dispatch]);

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
          {onSignOut && (
            <button className="ghost" onClick={onSignOut} type="button">
              Sign Out
            </button>
          )}
        </div>
      </header>

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
