import type { FormEvent } from 'react';

import { EditButton } from './EditButton';
import { FinancialRecordList, FinancialRecordListItem } from './FinancialRecordList';
import { payCadenceLabel } from './financialsDatePolicy';
import { formatDate } from './financialsFormatters';
import type {
  DraftIncomeEvent,
  IncomeEventFormState,
  RecurringPaydayFormState,
} from './financialsTypes';
import { RemoveButton } from './RemoveButton';

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
        <FinancialRecordList
          description="Checks per month are calculated from entries with a check number."
          emptyDescription="Generate paydays or add a one-time income event to begin."
          emptyTitle="No income calendar entries yet."
          headingId="income-schedule-heading"
          itemCount={incomeEvents.length}
          summaryLabel="Income schedule summary"
          title="Income schedule"
        >
          {incomeEvents.map((event) => (
            <FinancialRecordListItem
              actions={
                <>
                  <EditButton
                    label={`Edit ${event.label}`}
                    onClick={() => startIncomeEventEdit(event)}
                  />
                  <RemoveButton
                    label={`Remove ${event.label}`}
                    onClick={() => requestRemoveIncomeEvent(event)}
                  />
                </>
              }
              badge={event.status === 'current' ? 'Current pay period' : undefined}
              key={event.id}
              metadata={
                event.checkNumber === null
                  ? [event.type, 'One-time event']
                  : [
                      event.type,
                      `Check #${event.checkNumber}`,
                      `${event.checksInMonth} ${event.checksInMonth === 1 ? 'check' : 'checks'} this month`,
                    ]
              }
              primary={event.label}
              state={
                <span className={`pill ${event.status ?? 'upcoming'}`}>
                  {incomeStatusLabel(event.status)}
                </span>
              }
              tone={event.status === 'current' ? 'positive' : undefined}
              value={<strong>{formatDate(event.date)}</strong>}
            />
          ))}
        </FinancialRecordList>

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
