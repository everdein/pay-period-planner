import { type FormEvent, useEffect, useMemo, useState } from 'react';

import type { PayCadence } from '../../api/endpoints/financials';
import {
  defaultRecurringPaydayForm,
  emptyIncomeEventForm,
  type FinancialsDraft,
  getCurrentPaycheck,
  toIncomeEventForm,
  withIncomeEventStatuses,
} from './financialsDraft';
import type { FinancialsDraftDispatch } from './financialsDraftReducer';
import type {
  DraftIncomeEvent,
  IncomeEventFormState,
  RecurringPaydayFormState,
} from './financialsTypes';

const emptyIncomeEvents: DraftIncomeEvent[] = [];

export function useIncomeCalendarDraft(
  draft: FinancialsDraft | null,
  dispatch: FinancialsDraftDispatch,
  resetGeneration: number,
  todayIso: string,
  payCadence: PayCadence = 'BIWEEKLY'
) {
  const [editingIncomeEventId, setEditingIncomeEventId] = useState<number | null>(null);
  const [incomeEventForm, setIncomeEventForm] =
    useState<IncomeEventFormState>(emptyIncomeEventForm);
  const [recurringPaydayForm, setRecurringPaydayForm] = useState<RecurringPaydayFormState>(() =>
    defaultRecurringPaydayForm(todayIso, payCadence)
  );
  const draftIncomeEvents = draft?.incomeEvents ?? emptyIncomeEvents;

  useEffect(() => {
    setEditingIncomeEventId(null);
    setIncomeEventForm(emptyIncomeEventForm);
    setRecurringPaydayForm(defaultRecurringPaydayForm(todayIso, payCadence));
  }, [payCadence, resetGeneration, todayIso]);

  const incomeEvents = useMemo(
    () => withIncomeEventStatuses(draftIncomeEvents, todayIso),
    [draftIncomeEvents, todayIso]
  );
  const currentPaycheck = useMemo(
    () => getCurrentPaycheck(incomeEvents, todayIso),
    [incomeEvents, todayIso]
  );

  function updateIncomeEventForm<K extends keyof IncomeEventFormState>(
    key: K,
    value: IncomeEventFormState[K]
  ) {
    setIncomeEventForm((current) => ({ ...current, [key]: value }));
  }

  function updateRecurringPaydayForm<K extends keyof RecurringPaydayFormState>(
    key: K,
    value: RecurringPaydayFormState[K]
  ) {
    setRecurringPaydayForm((current) => {
      const next = { ...current, [key]: value };
      return key === 'year' ? { ...next, firstPayDate: '', secondPayDate: '' } : next;
    });
  }

  function submitIncomeEvent(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    dispatch({ editingId: editingIncomeEventId, form: incomeEventForm, type: 'save-income-event' });

    cancelIncomeEventEdit();
  }

  function submitRecurringPaydays(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    dispatch({ form: recurringPaydayForm, type: 'generate-recurring-paydays' });
  }

  function startIncomeEventEdit(event: DraftIncomeEvent) {
    setEditingIncomeEventId(event.id);
    setIncomeEventForm(toIncomeEventForm(event));
  }

  function cancelIncomeEventEdit() {
    setEditingIncomeEventId(null);
    setIncomeEventForm(emptyIncomeEventForm);
  }

  return {
    cancelIncomeEventEdit,
    currentPaycheck,
    draftIncomeEvents,
    editingIncomeEventId,
    incomeEventForm,
    incomeEvents,
    recurringPaydayForm,
    startIncomeEventEdit,
    submitIncomeEvent,
    submitRecurringPaydays,
    updateIncomeEventForm,
    updateRecurringPaydayForm,
  };
}
