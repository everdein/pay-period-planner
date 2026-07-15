import type { FormEvent } from 'react';

import { EditButton } from './EditButton';
import { EmptyTableRow } from './EmptyTableRow';
import { isRentWithdrawal, RENT_WITHDRAWAL_NAME } from './financialsAnchors';
import { currency, formatDate } from './financialsFormatters';
import type { BillFormState, DraftAnnualWithdrawal, DraftBill } from './financialsTypes';
import { RemoveButton } from './RemoveButton';

export function MonthlyWithdrawalsTab({
  annualPayPeriodTotal,
  annualWithdrawalsInPayPeriod,
  cancelEdit,
  editingId,
  form,
  formTitle,
  payPeriodEnd,
  payPeriodStart,
  requestRemoveBill,
  sortedBills,
  startEdit,
  submitBill,
  totals,
  updateForm,
  updatePayPeriodEnd,
  updatePayPeriodStart,
}: {
  annualPayPeriodTotal: number;
  annualWithdrawalsInPayPeriod: DraftAnnualWithdrawal[];
  cancelEdit: () => void;
  editingId: number | null;
  form: BillFormState;
  formTitle: string;
  payPeriodEnd: string;
  payPeriodStart: string;
  requestRemoveBill: (bill: DraftBill) => void;
  sortedBills: DraftBill[];
  startEdit: (bill: DraftBill) => void;
  submitBill: (event: FormEvent<HTMLFormElement>) => void;
  totals: {
    totalMonthlyExpenses: number;
    paidTotal: number;
    unpaidTotal: number;
    payPeriodTotal: number;
  };
  updateForm: <K extends keyof BillFormState>(key: K, value: BillFormState[K]) => void;
  updatePayPeriodEnd: (value: string) => void;
  updatePayPeriodStart: (value: string) => void;
}) {
  return (
    <>
      <section className="withdrawals-header">
        <div>
          <h2>Monthly Withdrawals</h2>
          <p>Cash outflows for bills, subscriptions, transfers, and savings contributions.</p>
          <p className="helper-text">
            Pay period dates open to the schedule that includes today. Editing them updates the
            schedule anchor when you save.
          </p>
        </div>
        <div className="pay-period">
          <label>
            Pay period start
            <input
              onChange={(event) => updatePayPeriodStart(event.target.value)}
              type="date"
              value={payPeriodStart}
            />
          </label>
          <label>
            Pay period end
            <input
              onChange={(event) => updatePayPeriodEnd(event.target.value)}
              type="date"
              value={payPeriodEnd}
            />
          </label>
        </div>
      </section>

      <section aria-label="Withdrawal summary" className="summary-grid">
        <div>
          <span>Monthly total</span>
          <strong>{currency.format(totals.totalMonthlyExpenses)}</strong>
        </div>
        <div>
          <span>Paid</span>
          <strong>{currency.format(totals.paidTotal)}</strong>
        </div>
        <div>
          <span>Unpaid</span>
          <strong>{currency.format(totals.unpaidTotal)}</strong>
        </div>
        <div>
          <span>Pay period total</span>
          <strong>{currency.format(totals.payPeriodTotal + annualPayPeriodTotal)}</strong>
        </div>
      </section>

      <section className="expenses-layout">
        <div className="stacked-tables">
          <div className="table-wrap">
            <table className="withdrawals-table compact-date-table">
              <colgroup>
                <col className="name-column" />
                <col className="date-column" />
                <col className="amount-column" />
                <col className="account-column" />
                <col className="status-column" />
                <col className="actions-column" />
              </colgroup>
              <caption>
                Monthly withdrawals from {formatDate(payPeriodStart)} to {formatDate(payPeriodEnd)}{' '}
                are highlighted.
              </caption>
              <thead>
                <tr>
                  <th>Withdrawal</th>
                  <th>Due Day</th>
                  <th>Amount</th>
                  <th>Account</th>
                  <th>Paid</th>
                  <th aria-label="Actions" />
                </tr>
              </thead>
              <tbody>
                {sortedBills.length === 0 && (
                  <EmptyTableRow columns={6} message="No monthly withdrawals yet." />
                )}
                {sortedBills.map((bill) => (
                  <tr className={bill.inPayPeriod ? 'in-period' : undefined} key={bill.id}>
                    <td>{bill.bill}</td>
                    <td className="date-cell">{bill.dueLabel}</td>
                    <td className="amount">{currency.format(bill.amount)}</td>
                    <td>{bill.account}</td>
                    <td className="status-cell">
                      <span className={bill.paid ? 'pill paid' : 'pill unpaid'}>
                        {bill.paid ? 'Paid' : 'Open'}
                      </span>
                    </td>
                    <td className="actions">
                      <EditButton label={`Edit ${bill.bill}`} onClick={() => startEdit(bill)} />
                      <RemoveButton
                        disabled={isRentWithdrawal(bill)}
                        label={`Remove ${bill.bill}`}
                        onClick={() => requestRemoveBill(bill)}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="table-total">
              Total: <strong>{currency.format(totals.totalMonthlyExpenses)}</strong>
            </p>
          </div>

          <div className="table-wrap">
            <table className="withdrawals-table">
              <colgroup>
                <col className="name-column" />
                <col className="date-column" />
                <col className="amount-column" />
                <col className="account-column" />
                <col className="status-column" />
              </colgroup>
              <caption>Annual withdrawals due in this pay period.</caption>
              <thead>
                <tr>
                  <th>Withdrawal</th>
                  <th>Date</th>
                  <th>Amount</th>
                  <th>Account</th>
                  <th>Paid</th>
                </tr>
              </thead>
              <tbody>
                {annualWithdrawalsInPayPeriod.length > 0 ? (
                  annualWithdrawalsInPayPeriod.map((withdrawal) => (
                    <tr className="in-period" key={withdrawal.id}>
                      <td>{withdrawal.bill}</td>
                      <td className="date-cell">{formatDate(withdrawal.dueDate)}</td>
                      <td className="amount">{currency.format(withdrawal.amount)}</td>
                      <td>{withdrawal.account}</td>
                      <td className="status-cell">
                        <span className={withdrawal.paid ? 'pill paid' : 'pill unpaid'}>
                          {withdrawal.paid ? 'Paid' : 'Open'}
                        </span>
                      </td>
                    </tr>
                  ))
                ) : (
                  <EmptyTableRow columns={5} message="No annual withdrawals in this pay period." />
                )}
              </tbody>
            </table>
            <p className="table-total">
              Pay period annual total: <strong>{currency.format(annualPayPeriodTotal)}</strong>
            </p>
          </div>
        </div>

        <form className="bill-form" onSubmit={submitBill}>
          <h2>{formTitle}</h2>
          <label>
            Withdrawal
            <input
              disabled={editingId !== null && form.bill === RENT_WITHDRAWAL_NAME}
              onChange={(event) => updateForm('bill', event.target.value)}
              required
              value={form.bill}
            />
          </label>
          <label>
            Due day
            <input
              max={31}
              min={1}
              onChange={(event) => updateForm('dueDay', event.target.value)}
              required
              type="number"
              value={form.dueDay}
            />
          </label>
          <label>
            Amount
            <input
              min={0}
              onChange={(event) => updateForm('amount', event.target.value)}
              required
              step="0.01"
              type="number"
              value={form.amount}
            />
          </label>
          <label>
            Account
            <input
              onChange={(event) => updateForm('account', event.target.value)}
              required
              value={form.account}
            />
          </label>
          <label className="checkbox-row">
            <input
              checked={form.paid}
              onChange={(event) => updateForm('paid', event.target.checked)}
              type="checkbox"
            />
            Paid
          </label>
          {editingId !== null && form.bill === RENT_WITHDRAWAL_NAME && (
            <p className="helper-text">
              Rent is required for projections. You can edit the date, amount, account, and paid
              status, but the name stays fixed.
            </p>
          )}
          <div className="form-actions">
            <button type="submit">{editingId ? 'Update Draft' : 'Add to Draft'}</button>
            {editingId && (
              <button className="ghost" onClick={cancelEdit} type="button">
                Cancel
              </button>
            )}
          </div>
        </form>
      </section>
    </>
  );
}
