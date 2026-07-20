import type { FormEvent } from 'react';

import { EditButton } from './EditButton';
import { FinancialRecordList, FinancialRecordListItem } from './FinancialRecordList';
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
        <FinancialRecordList
          description="Calendar dates used for yearly planning."
          emptyDescription="Add a holiday, birthday, anniversary, or company day off."
          emptyTitle="No important dates yet."
          headingId="important-date-list-heading"
          itemCount={importantDates.length}
          summaryLabel="Important date summary"
          title="Planning calendar"
        >
          {importantDates.map((importantDate) => (
            <FinancialRecordListItem
              actions={
                <>
                  <EditButton
                    label={`Edit ${importantDate.event}`}
                    onClick={() => startImportantDateEdit(importantDate)}
                  />
                  <RemoveButton
                    label={`Remove ${importantDate.event}`}
                    onClick={() => requestRemoveImportantDate(importantDate)}
                  />
                </>
              }
              badge={importantDate.status === 'next' ? 'Next important date' : undefined}
              key={importantDate.id}
              metadata={[importantDate.type]}
              primary={importantDate.event}
              state={
                <span className={`pill ${importantDate.status ?? 'upcoming'}`}>
                  {importantDateStatusLabel(importantDate.status)}
                </span>
              }
              tone={importantDate.status === 'next' ? 'caution' : undefined}
              value={<strong>{formatDate(importantDate.date)}</strong>}
            />
          ))}
        </FinancialRecordList>

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
