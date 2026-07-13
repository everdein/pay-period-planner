export function SaveControls({
  exporting,
  isDirty,
  onExport,
  onReset,
  onSave,
  saving,
}: {
  exporting: boolean;
  isDirty: boolean;
  onExport: () => void;
  onReset: () => void;
  onSave: () => void;
  saving: boolean;
}) {
  return (
    <div className="save-actions">
      <button disabled={saving || !isDirty} onClick={onSave} type="button">
        Save Changes
      </button>
      <button className="ghost" disabled={!isDirty} onClick={onReset} type="button">
        Reset
      </button>
      <button
        aria-label="Export saved financial snapshot backup"
        className="ghost"
        disabled={exporting}
        onClick={onExport}
        type="button"
      >
        {exporting ? 'Exporting...' : 'Export Backup'}
      </button>
    </div>
  );
}
