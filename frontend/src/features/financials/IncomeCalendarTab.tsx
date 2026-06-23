import type { FormEvent } from 'react';

import { formatDate } from './financialsFormatters';
import type { DraftIncomeEvent, IncomeEventFormState } from './financialsTypes';

export function IncomeCalendarTab({
  cancelIncomeEventEdit,
  editingIncomeEventId,
  incomeEventForm,
  incomeEvents,
  requestRemoveIncomeEvent,
  startIncomeEventEdit,
  submitIncomeEvent,
  updateIncomeEventForm,
}: {
  cancelIncomeEventEdit: () => void;
  editingIncomeEventId: number | null;
  incomeEventForm: IncomeEventFormState;
  incomeEvents: DraftIncomeEvent[];
  requestRemoveIncomeEvent: (event: DraftIncomeEvent) => void;
  startIncomeEventEdit: (event: DraftIncomeEvent) => void;
  submitIncomeEvent: (event: FormEvent<HTMLFormElement>) => void;
  updateIncomeEventForm: <K extends keyof IncomeEventFormState>(
    key: K,
    value: IncomeEventFormState[K]
  ) => void;
}) {
  const isEditing = editingIncomeEventId !== null;

  return (
    <>
      <section className="section-header">
        <div>
          <h2>Income Calendar</h2>
          <p>Paydays and one-time income events used for cash flow planning.</p>
        </div>
      </section>
      <section className="expenses-layout">
        <div className="table-wrap">
          <table>
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
              {incomeEvents.map((event) => (
                <tr
                  className={event.status === 'current' ? 'current-income' : undefined}
                  key={event.id}
                >
                  <td>{formatDate(event.date)}</td>
                  <td>{event.label}</td>
                  <td>{event.type}</td>
                  <td>{event.checkNumber ?? '-'}</td>
                  <td>{event.checkNumber === null ? '-' : event.checksInMonth}</td>
                  <td>
                    <span className={`pill ${event.status ?? 'upcoming'}`}>
                      {incomeStatusLabel(event.status)}
                    </span>
                  </td>
                  <td className="actions">
                    <button onClick={() => startIncomeEventEdit(event)} type="button">
                      Edit
                    </button>
                    <button
                      className="ghost"
                      onClick={() => requestRemoveIncomeEvent(event)}
                      type="button"
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

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
