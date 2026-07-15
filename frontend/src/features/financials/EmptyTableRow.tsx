export function EmptyTableRow({ columns, message }: { columns: number; message: string }) {
  return (
    <tr>
      <td className="empty-table-cell" colSpan={columns}>
        {message}
      </td>
    </tr>
  );
}
