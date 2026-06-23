export function EditButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button aria-label={label} className="icon-button edit-icon" onClick={onClick} type="button">
      &#9998;
    </button>
  );
}
