import type { FormEvent } from 'react';

import { EditButton } from './EditButton';
import { EmptyTableRow } from './EmptyTableRow';
import { formatDate } from './financialsFormatters';
import type { DraftImportantDate, ImportantDateFormState } from './financialsTypes';
import { RemoveButton } from './RemoveButton';

export function ImportantDatesTab({
  cancelImportantDateEdit,
  editingImportantDateId,
  importantDateForm,
  importantDates,
  requestRemoveImportantDate,
  startImportantDateEdit,
  submitImportantDate,
  updateImportantDateForm,
}: {
  cancelImportantDateEdit: () => void;
  editingImportantDateId: number | null;
  importantDateForm: ImportantDateFormState;
  importantDates: DraftImportantDate[];
  requestRemoveImportantDate: (importantDate: DraftImportantDate) => void;
  startImportantDateEdit: (importantDate: DraftImportantDate) => void;
  submitImportantDate: (event: FormEvent<HTMLFormElement>) => void;
  updateImportantDateForm: <K extends keyof ImportantDateFormState>(
    key: K,
    value: ImportantDateFormState[K]
  ) => void;
}) {
  const isEditing = editingImportantDateId !== null;

  return (
    <>
      <section className="section-header">
        <div>
          <h2>Important Dates</h2>
          <p>Holidays, birthdays, anniversaries, and company days off.</p>
        </div>
      </section>
      <section className="expenses-layout">
        <div className="table-wrap">
          <table className="dates-table">
            <colgroup>
              <col className="name-column" />
              <col className="date-column" />
              <col className="type-column" />
              <col className="status-column" />
              <col className="actions-column" />
            </colgroup>
            <caption>Calendar dates used for yearly planning.</caption>
            <thead>
              <tr>
                <th>Event</th>
                <th>Date</th>
                <th>Type</th>
                <th>Status</th>
                <th aria-label="Actions" />
              </tr>
            </thead>
            <tbody>
              {importantDates.length === 0 && (
                <EmptyTableRow columns={5} message="No important dates yet." />
              )}
              {importantDates.map((importantDate) => (
                <tr
                  className={importantDate.status === 'next' ? 'next-important-date' : undefined}
                  key={importantDate.id}
                >
                  <td>{importantDate.event}</td>
                  <td className="date-cell">{formatDate(importantDate.date)}</td>
                  <td>{importantDate.type}</td>
                  <td className="status-cell">
                    <span className={`pill ${importantDate.status ?? 'upcoming'}`}>
                      {importantDateStatusLabel(importantDate.status)}
                    </span>
                  </td>
                  <td className="actions">
                    <EditButton
                      label={`Edit ${importantDate.event}`}
                      onClick={() => startImportantDateEdit(importantDate)}
                    />
                    <RemoveButton
                      label={`Remove ${importantDate.event}`}
                      onClick={() => requestRemoveImportantDate(importantDate)}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <form className="bill-form" onSubmit={submitImportantDate}>
          <h2>{isEditing ? 'Edit Important Date' : 'Add Important Date'}</h2>
          <label>
            Event
            <input
              onChange={(event) => updateImportantDateForm('event', event.target.value)}
              required
              value={importantDateForm.event}
            />
          </label>
          <label>
            Date
            <input
              onChange={(event) => updateImportantDateForm('date', event.target.value)}
              required
              type="date"
              value={importantDateForm.date}
            />
          </label>
          <label>
            Type
            <select
              onChange={(event) => updateImportantDateForm('type', event.target.value)}
              value={importantDateForm.type}
            >
              <option>Holiday</option>
              <option>Birthday</option>
              <option>Anniversary</option>
              <option>Company Day Off</option>
              <option>Personal</option>
            </select>
          </label>
          <div className="form-actions">
            <button type="submit">{isEditing ? 'Update Draft' : 'Add to Draft'}</button>
            {isEditing && (
              <button className="ghost" onClick={cancelImportantDateEdit} type="button">
                Cancel
              </button>
            )}
          </div>
        </form>
      </section>
    </>
  );
}

function importantDateStatusLabel(status: DraftImportantDate['status']) {
  switch (status) {
    case 'next':
      return 'Next';
    case 'passed':
      return 'Passed';
    default:
      return 'Upcoming';
  }
}
