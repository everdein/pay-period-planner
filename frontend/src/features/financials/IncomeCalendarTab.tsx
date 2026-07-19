import type { FormEvent } from 'react';

import { EditButton } from './EditButton';
import { EmptyTableRow } from './EmptyTableRow';
import { payCadenceLabel } from './financialsDatePolicy';
import { formatDate } from './financialsFormatters';
import type {
  DraftIncomeEvent,
  IncomeEventFormState,
  RecurringPaydayFormState,
} from './financialsTypes';
import { RemoveButton } from './RemoveButton';
import { ScrollableTableRegion } from './ScrollableTableRegion';

export function IncomeCalendarTab({
  cancelIncomeEventEdit,
  editingIncomeEventId,
  incomeEventForm,
  incomeEvents,
  recurringPaydayForm,
  requestRemoveIncomeEvent,
  startIncomeEventEdit,
  submitRecurringPaydays,
  submitIncomeEvent,
  updateIncomeEventForm,
  updateRecurringPaydayForm,
}: {
  cancelIncomeEventEdit: () => void;
  editingIncomeEventId: number | null;
  incomeEventForm: IncomeEventFormState;
  incomeEvents: DraftIncomeEvent[];
  recurringPaydayForm: RecurringPaydayFormState;
  requestRemoveIncomeEvent: (event: DraftIncomeEvent) => void;
  startIncomeEventEdit: (event: DraftIncomeEvent) => void;
  submitRecurringPaydays: (event: FormEvent<HTMLFormElement>) => void;
  submitIncomeEvent: (event: FormEvent<HTMLFormElement>) => void;
  updateIncomeEventForm: <K extends keyof IncomeEventFormState>(
    key: K,
    value: IncomeEventFormState[K]
  ) => void;
  updateRecurringPaydayForm: <K extends keyof RecurringPaydayFormState>(
    key: K,
    value: RecurringPaydayFormState[K]
  ) => void;
}) {
  const isEditing = editingIncomeEventId !== null;
  const yearStart = `${recurringPaydayForm.year}-01-01`;
  const yearEnd = `${recurringPaydayForm.year}-12-31`;

  return (
    <>
      <section className="section-header">
        <div>
          <h2>Income Calendar</h2>
          <p>Paydays and one-time income events used for cash flow planning.</p>
        </div>
      </section>
      <section className="expenses-layout">
        <ScrollableTableRegion label="Income calendar">
          <table className="calendar-table">
            <colgroup>
              <col className="date-column" />
              <col className="name-column" />
              <col className="type-column" />
              <col className="count-column" />
              <col className="count-column" />
              <col className="status-column" />
              <col className="actions-column" />
            </colgroup>
            <caption>Checks per month are calculated from rows with a check number.</caption>
            <thead>
              <tr>
                <th>Pay Date</th>
                <th>Event</th>
                <th>Type</th>
                <th>Check #</th>
                <th>Checks/Month</th>
                <th>Status</th>
                <th aria-label="Actions" />
              </tr>
            </thead>
            <tbody>
              {incomeEvents.length === 0 && (
                <EmptyTableRow columns={7} message="No income calendar entries yet." />
              )}
              {incomeEvents.map((event) => (
                <tr
                  className={event.status === 'current' ? 'current-income' : undefined}
                  key={event.id}
                >
                  <td className="date-cell" data-label="Pay Date">
                    {formatDate(event.date)}
                  </td>
                  <td data-label="Event">{event.label}</td>
                  <td data-label="Type">{event.type}</td>
                  <td className="count-cell" data-label="Check #">
                    {event.checkNumber ?? '-'}
                  </td>
                  <td className="count-cell" data-label="Checks / Month">
                    {event.checkNumber === null ? '-' : event.checksInMonth}
                  </td>
                  <td className="status-cell" data-label="Status">
                    <span className={`pill ${event.status ?? 'upcoming'}`}>
                      {incomeStatusLabel(event.status)}
                    </span>
                  </td>
                  <td className="actions" data-label="Actions">
                    <EditButton
                      label={`Edit ${event.label}`}
                      onClick={() => startIncomeEventEdit(event)}
                    />
                    <RemoveButton
                      label={`Remove ${event.label}`}
                      onClick={() => requestRemoveIncomeEvent(event)}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </ScrollableTableRegion>

        <div className="side-forms">
          <form className="bill-form" onSubmit={submitRecurringPaydays}>
            <h2>Generate Paydays</h2>
            <p className="helper-text">{payCadenceLabel(recurringPaydayForm.payCadence)}</p>
            <label>
              Year
              <input
                max={2100}
                min={2000}
                onChange={(event) => updateRecurringPaydayForm('year', event.target.value)}
                required
                type="number"
                value={recurringPaydayForm.year}
              />
            </label>
            {recurringPaydayForm.payCadence === 'SEMIMONTHLY' && (
              <label>
                Second payday
                <input
                  max={yearEnd}
                  min={yearStart}
                  onChange={(event) =>
                    updateRecurringPaydayForm('secondPayDate', event.target.value)
                  }
                  required
                  type="date"
                  value={recurringPaydayForm.secondPayDate}
                />
              </label>
            )}
            <label>
              First payday
              <input
                max={yearEnd}
                min={yearStart}
                onChange={(event) => updateRecurringPaydayForm('firstPayDate', event.target.value)}
                required
                type="date"
                value={recurringPaydayForm.firstPayDate}
              />
            </label>
            <label>
              Starting check #
              <input
                min={1}
                onChange={(event) =>
                  updateRecurringPaydayForm('startingCheckNumber', event.target.value)
                }
                required
                type="number"
                value={recurringPaydayForm.startingCheckNumber}
              />
            </label>
            <label>
              Event
              <input
                onChange={(event) => updateRecurringPaydayForm('label', event.target.value)}
                required
                value={recurringPaydayForm.label}
              />
            </label>
            <label>
              Type
              <select
                onChange={(event) => updateRecurringPaydayForm('type', event.target.value)}
                value={recurringPaydayForm.type}
              >
                <option>Paycheck</option>
                <option>Tax Return</option>
                <option>Merit Increase</option>
                <option>Bonus</option>
                <option>Other</option>
              </select>
            </label>
            <label className="checkbox-row">
              <input
                checked={recurringPaydayForm.replaceExistingYear}
                onChange={(event) =>
                  updateRecurringPaydayForm('replaceExistingYear', event.target.checked)
                }
                type="checkbox"
              />
              Replace existing numbered income rows for this year
            </label>
            <button type="submit">Generate Paydays</button>
          </form>

          <form className="bill-form" onSubmit={submitIncomeEvent}>
            <h2>{isEditing ? 'Edit Income Event' : 'Add Income Event'}</h2>
            <label>
              Date
              <input
                onChange={(event) => updateIncomeEventForm('date', event.target.value)}
                required
                type="date"
                value={incomeEventForm.date}
              />
            </label>
            <label>
              Event
              <input
                onChange={(event) => updateIncomeEventForm('label', event.target.value)}
                required
                value={incomeEventForm.label}
              />
            </label>
            <label>
              Type
              <select
                onChange={(event) => updateIncomeEventForm('type', event.target.value)}
                value={incomeEventForm.type}
              >
                <option>Paycheck</option>
                <option>Tax Return</option>
                <option>Merit Increase</option>
                <option>Bonus</option>
                <option>Other</option>
              </select>
            </label>
            <label>
              Check #
              <input
                min={1}
                onChange={(event) => updateIncomeEventForm('checkNumber', event.target.value)}
                type="number"
                value={incomeEventForm.checkNumber}
              />
            </label>
            <div className="form-actions">
              <button type="submit">{isEditing ? 'Update Draft' : 'Add to Draft'}</button>
              {isEditing && (
                <button className="ghost" onClick={cancelIncomeEventEdit} type="button">
                  Cancel
                </button>
              )}
            </div>
          </form>
        </div>
      </section>
    </>
  );
}

function incomeStatusLabel(status: DraftIncomeEvent['status']) {
  switch (status) {
    case 'current':
      return 'Current';
    case 'received':
      return 'Received';
    default:
      return 'Upcoming';
  }
}
