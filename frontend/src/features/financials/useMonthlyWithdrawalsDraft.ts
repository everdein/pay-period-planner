import { type FormEvent, useEffect, useMemo, useState } from 'react';

import { emptyForm, type FinancialsDraft, toForm } from './financialsDraft';
import type { FinancialsDraftDispatch } from './financialsDraftReducer';
import type { BillFormState, DraftBill } from './financialsTypes';

type MonthlyWithdrawalsDraftTotals = {
  paidTotal: number;
  payPeriodTotal: number;
  totalMonthlyExpenses: number;
  unpaidTotal: number;
};

const emptyBills: DraftBill[] = [];

export function useMonthlyWithdrawalsDraft(
  draft: FinancialsDraft | null,
  dispatch: FinancialsDraftDispatch,
  resetGeneration: number
) {
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<BillFormState>(emptyForm);
  const draftBills = draft?.bills ?? emptyBills;
  const payPeriodEnd = draft?.payPeriodEnd ?? '';
  const payPeriodStart = draft?.payPeriodStart ?? '';

  useEffect(() => {
    setEditingId(null);
    setForm(emptyForm);
  }, [resetGeneration]);

  const sortedBills = useMemo(
    () => [...draftBills].sort((left, right) => left.dueDay - right.dueDay),
    [draftBills]
  );
  const totals = useMemo<MonthlyWithdrawalsDraftTotals>(() => {
    const totalMonthlyExpenses = draftBills.reduce((total, bill) => total + bill.amount, 0);
    const paidTotal = draftBills
      .filter((bill) => bill.paid)
      .reduce((total, bill) => total + bill.amount, 0);
    const payPeriodTotal = draftBills
      .filter((bill) => bill.inPayPeriod)
      .reduce((total, bill) => total + bill.amount, 0);

    return {
      paidTotal,
      payPeriodTotal,
      totalMonthlyExpenses,
      unpaidTotal: totalMonthlyExpenses - paidTotal,
    };
  }, [draftBills]);
  const selectedBill = sortedBills.find((bill) => bill.id === editingId);
  const formTitle = selectedBill ? `Edit ${selectedBill.bill}` : 'Add Bill';

  function updateForm<K extends keyof BillFormState>(key: K, value: BillFormState[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function submitBill(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    dispatch({ editingId, form, type: 'save-bill' });

    setEditingId(null);
    setForm(emptyForm);
  }

  function startEdit(bill: DraftBill) {
    setEditingId(bill.id);
    setForm(toForm(bill));
  }

  function cancelEdit() {
    setEditingId(null);
    setForm(emptyForm);
  }

  function updatePayPeriodStart(value: string) {
    dispatch({ type: 'update-pay-period-start', value });
  }

  function updatePayPeriodEnd(value: string) {
    dispatch({ type: 'update-pay-period-end', value });
  }

  return {
    cancelEdit,
    draftBills,
    editingId,
    form,
    formTitle,
    payPeriodEnd,
    payPeriodStart,
    sortedBills,
    startEdit,
    submitBill,
    totals,
    updateForm,
    updatePayPeriodEnd,
    updatePayPeriodStart,
  };
}
