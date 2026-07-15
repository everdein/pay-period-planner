import { type FormEvent, useEffect, useMemo, useState } from 'react';

import {
  emptyImportantDateForm,
  type FinancialsDraft,
  getNextImportantDate,
  toImportantDateForm,
  withImportantDateStatuses,
} from './financialsDraft';
import type { FinancialsDraftDispatch } from './financialsDraftReducer';
import type { DraftImportantDate, ImportantDateFormState } from './financialsTypes';

const emptyImportantDates: DraftImportantDate[] = [];

export function useImportantDatesDraft(
  draft: FinancialsDraft | null,
  dispatch: FinancialsDraftDispatch,
  resetGeneration: number,
  todayIso: string
) {
  const [editingImportantDateId, setEditingImportantDateId] = useState<number | null>(null);
  const [importantDateForm, setImportantDateForm] =
    useState<ImportantDateFormState>(emptyImportantDateForm);
  const draftImportantDates = draft?.importantDates ?? emptyImportantDates;

  useEffect(() => {
    setEditingImportantDateId(null);
    setImportantDateForm(emptyImportantDateForm);
  }, [resetGeneration]);

  const importantDates = useMemo(
    () => withImportantDateStatuses(draftImportantDates, todayIso),
    [draftImportantDates, todayIso]
  );
  const nextImportantDate = useMemo(
    () => getNextImportantDate(importantDates, todayIso),
    [importantDates, todayIso]
  );

  function updateImportantDateForm<K extends keyof ImportantDateFormState>(
    key: K,
    value: ImportantDateFormState[K]
  ) {
    setImportantDateForm((current) => ({ ...current, [key]: value }));
  }

  function submitImportantDate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    dispatch({
      editingId: editingImportantDateId,
      form: importantDateForm,
      type: 'save-important-date',
    });

    cancelImportantDateEdit();
  }

  function startImportantDateEdit(importantDate: DraftImportantDate) {
    setEditingImportantDateId(importantDate.id);
    setImportantDateForm(toImportantDateForm(importantDate));
  }

  function cancelImportantDateEdit() {
    setEditingImportantDateId(null);
    setImportantDateForm(emptyImportantDateForm);
  }

  return {
    cancelImportantDateEdit,
    draftImportantDates,
    editingImportantDateId,
    importantDateForm,
    importantDates,
    nextImportantDate,
    startImportantDateEdit,
    submitImportantDate,
    updateImportantDateForm,
  };
}
