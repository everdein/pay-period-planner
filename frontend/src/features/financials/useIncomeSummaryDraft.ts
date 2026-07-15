import { type FormEvent, useEffect, useMemo, useRef, useState } from 'react';

import type { PayCadence } from '../../api/endpoints/financials';
import {
  buildDerivedIncomeSummaryItems,
  emptyIncomeSummaryForm,
  type FinancialsDraft,
  toIncomeSummaryForm,
} from './financialsDraft';
import type { FinancialsDraftDispatch } from './financialsDraftReducer';
import type { DraftIncomeSummaryItem, IncomeSummaryFormState } from './financialsTypes';

const emptyIncomeSummaryItems: DraftIncomeSummaryItem[] = [];

export function useIncomeSummaryDraft(
  draft: FinancialsDraft | null,
  dispatch: FinancialsDraftDispatch,
  resetGeneration: number,
  totalMonthlyWithdrawals: number,
  primaryPaycheckId: number,
  payCadence: PayCadence = 'BIWEEKLY'
) {
  const [editingIncomeSummaryItemId, setEditingIncomeSummaryItemId] = useState<number | null>(null);
  const [incomeSummaryForm, setIncomeSummaryForm] =
    useState<IncomeSummaryFormState>(emptyIncomeSummaryForm);
  const [restorePrimaryForm, setRestorePrimaryForm] = useState(false);
  const draftIncomeSummaryItems = draft?.incomeSummaryItems ?? emptyIncomeSummaryItems;
  const latestItems = useRef(draftIncomeSummaryItems);
  const latestPrimaryPaycheckId = useRef(primaryPaycheckId);
  latestItems.current = draftIncomeSummaryItems;
  latestPrimaryPaycheckId.current = primaryPaycheckId;

  useEffect(() => {
    setEditingIncomeSummaryItemId(null);
    setIncomeSummaryForm(
      defaultIncomeSummaryForm(latestItems.current, latestPrimaryPaycheckId.current)
    );
    setRestorePrimaryForm(false);
  }, [resetGeneration]);

  useEffect(() => {
    if (restorePrimaryForm) {
      setIncomeSummaryForm(defaultIncomeSummaryForm(draftIncomeSummaryItems, primaryPaycheckId));
      setRestorePrimaryForm(false);
    }
  }, [draftIncomeSummaryItems, primaryPaycheckId, restorePrimaryForm]);

  useEffect(() => {
    if (
      editingIncomeSummaryItemId !== null &&
      !draftIncomeSummaryItems.some(({ id }) => id === editingIncomeSummaryItemId)
    ) {
      setEditingIncomeSummaryItemId(null);
      setIncomeSummaryForm(defaultIncomeSummaryForm(draftIncomeSummaryItems, primaryPaycheckId));
    }
  }, [draftIncomeSummaryItems, editingIncomeSummaryItemId, primaryPaycheckId]);

  const sourceIncomeSummaryItems = useMemo(
    () =>
      [...draftIncomeSummaryItems].sort(
        (left, right) =>
          left.category.localeCompare(right.category) || left.interval.localeCompare(right.interval)
      ),
    [draftIncomeSummaryItems]
  );
  const derivedIncomeSummaryItems = useMemo(
    () =>
      buildDerivedIncomeSummaryItems(
        draftIncomeSummaryItems,
        totalMonthlyWithdrawals,
        primaryPaycheckId,
        payCadence
      ),
    [draftIncomeSummaryItems, payCadence, primaryPaycheckId, totalMonthlyWithdrawals]
  );
  const primaryPaycheckIncome = draftIncomeSummaryItems.find(({ id }) => id === primaryPaycheckId);

  function updateIncomeSummaryForm<K extends keyof IncomeSummaryFormState>(
    key: K,
    value: IncomeSummaryFormState[K]
  ) {
    setIncomeSummaryForm((current) => ({ ...current, [key]: value }));
  }

  function submitIncomeSummaryItem(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    dispatch({
      editingId: editingIncomeSummaryItemId,
      form: incomeSummaryForm,
      type: 'save-income-summary-item',
    });
    setEditingIncomeSummaryItemId(null);
    setRestorePrimaryForm(true);
  }

  function startIncomeSummaryItemEdit(item: DraftIncomeSummaryItem) {
    setEditingIncomeSummaryItemId(item.id);
    setIncomeSummaryForm(toIncomeSummaryForm(item));
  }

  function cancelIncomeSummaryItemEdit() {
    setEditingIncomeSummaryItemId(null);
    setIncomeSummaryForm(defaultIncomeSummaryForm(draftIncomeSummaryItems, primaryPaycheckId));
  }

  return {
    derivedIncomeSummaryItems,
    draftIncomeSummaryItems,
    editingIncomeSummaryItemId,
    incomeSummaryForm,
    primaryPaycheckIncome,
    sourceIncomeSummaryItems,
    startIncomeSummaryItemEdit,
    submitIncomeSummaryItem,
    cancelIncomeSummaryItemEdit,
    updateIncomeSummaryForm,
  };
}

function defaultIncomeSummaryForm(
  items: DraftIncomeSummaryItem[],
  primaryPaycheckId: number
): IncomeSummaryFormState {
  const primaryPaycheck = items.find(({ id }) => id === primaryPaycheckId);
  return primaryPaycheck ? toIncomeSummaryForm(primaryPaycheck) : emptyIncomeSummaryForm;
}
