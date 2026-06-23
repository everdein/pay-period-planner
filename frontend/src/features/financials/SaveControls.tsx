export function SaveControls({
  isDirty,
  onReset,
  onSave,
  saving,
}: {
  isDirty: boolean;
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
    </div>
  );
}
