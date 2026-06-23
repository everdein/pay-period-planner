export function ConfirmRemoveModal({
  itemName,
  itemType,
  onCancel,
  onConfirm,
}: {
  itemName: string;
  itemType: string;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div
      aria-labelledby="remove-modal-title"
      aria-modal="true"
      className="modal-backdrop"
      role="dialog"
    >
      <div className="modal">
        <h2 id="remove-modal-title">Remove {itemType}?</h2>
        <p>
          This will remove <strong>{itemName}</strong> from your draft. You can still use Reset to
          return to the last saved snapshot before saving changes.
        </p>
        <div className="modal-actions">
          <button className="danger" onClick={onConfirm} type="button">
            Remove
          </button>
          <button className="ghost" onClick={onCancel} type="button">
            Cancel
          </button>
        </div>
      </div>
    </div>
  );
}
