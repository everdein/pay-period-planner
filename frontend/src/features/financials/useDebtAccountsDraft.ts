import { type FormEvent, useEffect, useMemo, useState } from 'react';

import { emptyAssetForm, type FinancialsDraft, toDebtForm } from './financialsDraft';
import type { FinancialsDraftDispatch } from './financialsDraftReducer';
import type { AssetFormState, DraftDebtAccount } from './financialsTypes';

const emptyDebtAccounts: DraftDebtAccount[] = [];

export function useDebtAccountsDraft(
  draft: FinancialsDraft | null,
  dispatch: FinancialsDraftDispatch,
  resetGeneration: number
) {
  const [debtForm, setDebtForm] = useState<AssetFormState>(emptyAssetForm);
  const [editingDebtId, setEditingDebtId] = useState<number | null>(null);
  const draftDebtAccounts = draft?.debtAccounts ?? emptyDebtAccounts;

  useEffect(() => {
    setDebtForm(emptyAssetForm);
    setEditingDebtId(null);
  }, [resetGeneration]);

  const debtAccounts = useMemo(
    () => [...draftDebtAccounts].sort((left, right) => left.account.localeCompare(right.account)),
    [draftDebtAccounts]
  );
  const totalDebt = useMemo(
    () => draftDebtAccounts.reduce((total, account) => total + account.amount, 0),
    [draftDebtAccounts]
  );

  function updateDebtForm<K extends keyof AssetFormState>(key: K, value: AssetFormState[K]) {
    setDebtForm((current) => ({ ...current, [key]: value }));
  }

  function submitDebt(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    dispatch({ editingId: editingDebtId, form: debtForm, type: 'save-debt' });

    cancelDebtEdit();
  }

  function startDebtEdit(account: DraftDebtAccount) {
    setEditingDebtId(account.id);
    setDebtForm(toDebtForm(account));
  }

  function cancelDebtEdit() {
    setEditingDebtId(null);
    setDebtForm(emptyAssetForm);
  }

  return {
    cancelDebtEdit,
    debtAccounts,
    debtForm,
    editingDebtId,
    startDebtEdit,
    submitDebt,
    totalDebt,
    updateDebtForm,
  };
}
