import { type FormEvent, useEffect, useMemo, useState } from 'react';

import {
  emptyAnnualWithdrawalForm,
  type FinancialsDraft,
  toAnnualWithdrawalForm,
} from './financialsDraft';
import type { FinancialsDraftDispatch } from './financialsDraftReducer';
import type { AnnualWithdrawalFormState, DraftAnnualWithdrawal } from './financialsTypes';

type AnnualWithdrawalsDraftTotals = {
  annualPayPeriodTotal: number;
  totalAnnualWithdrawals: number;
};

const emptyAnnualWithdrawals: DraftAnnualWithdrawal[] = [];

export function useAnnualWithdrawalsDraft(
  draft: FinancialsDraft | null,
  dispatch: FinancialsDraftDispatch,
  resetGeneration: number
) {
  const [annualWithdrawalForm, setAnnualWithdrawalForm] =
    useState<AnnualWithdrawalFormState>(emptyAnnualWithdrawalForm);
  const [editingAnnualWithdrawalId, setEditingAnnualWithdrawalId] = useState<number | null>(null);
  const draftAnnualWithdrawals = draft?.annualWithdrawals ?? emptyAnnualWithdrawals;

  useEffect(() => {
    setAnnualWithdrawalForm(emptyAnnualWithdrawalForm);
    setEditingAnnualWithdrawalId(null);
  }, [resetGeneration]);

  const annualWithdrawals = useMemo(
    () =>
      [...draftAnnualWithdrawals].sort(
        (left, right) => left.month - right.month || left.day - right.day
      ),
    [draftAnnualWithdrawals]
  );
  const annualWithdrawalsInPayPeriod = useMemo(
    () => annualWithdrawals.filter((withdrawal) => withdrawal.inPayPeriod),
    [annualWithdrawals]
  );
  const totals = useMemo<AnnualWithdrawalsDraftTotals>(() => {
    const totalAnnualWithdrawals = draftAnnualWithdrawals.reduce(
      (total, withdrawal) => total + withdrawal.amount,
      0
    );
    const annualPayPeriodTotal = annualWithdrawalsInPayPeriod.reduce(
      (total, withdrawal) => total + withdrawal.amount,
      0
    );

    return { annualPayPeriodTotal, totalAnnualWithdrawals };
  }, [annualWithdrawalsInPayPeriod, draftAnnualWithdrawals]);

  function updateAnnualWithdrawalForm<K extends keyof AnnualWithdrawalFormState>(
    key: K,
    value: AnnualWithdrawalFormState[K]
  ) {
    setAnnualWithdrawalForm((current) => ({ ...current, [key]: value }));
  }

  function submitAnnualWithdrawal(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    dispatch({
      editingId: editingAnnualWithdrawalId,
      form: annualWithdrawalForm,
      type: 'save-annual-withdrawal',
    });

    cancelAnnualWithdrawalEdit();
  }

  function startAnnualWithdrawalEdit(withdrawal: DraftAnnualWithdrawal) {
    setEditingAnnualWithdrawalId(withdrawal.id);
    setAnnualWithdrawalForm(toAnnualWithdrawalForm(withdrawal));
  }

  function cancelAnnualWithdrawalEdit() {
    setEditingAnnualWithdrawalId(null);
    setAnnualWithdrawalForm(emptyAnnualWithdrawalForm);
  }

  return {
    annualWithdrawalForm,
    annualWithdrawals,
    annualWithdrawalsInPayPeriod,
    cancelAnnualWithdrawalEdit,
    editingAnnualWithdrawalId,
    startAnnualWithdrawalEdit,
    submitAnnualWithdrawal,
    totals,
    updateAnnualWithdrawalForm,
  };
}
