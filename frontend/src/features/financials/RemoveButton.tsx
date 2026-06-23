export function RemoveButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button aria-label={label} className="icon-button danger-icon" onClick={onClick} type="button">
      X
    </button>
  );
}
