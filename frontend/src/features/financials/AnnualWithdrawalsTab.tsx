import type { FormEvent } from 'react';

import { currency, formatDate } from './financialsFormatters';
import type { AnnualWithdrawalFormState, DraftAnnualWithdrawal } from './financialsTypes';

export function AnnualWithdrawalsTab({
  annualWithdrawalForm,
  annualWithdrawals,
  cancelAnnualWithdrawalEdit,
  editingAnnualWithdrawalId,
  requestRemoveAnnualWithdrawal,
  startAnnualWithdrawalEdit,
  submitAnnualWithdrawal,
  totals,
  updateAnnualWithdrawalForm,
}: {
  annualWithdrawalForm: AnnualWithdrawalFormState;
  annualWithdrawals: DraftAnnualWithdrawal[];
  cancelAnnualWithdrawalEdit: () => void;
  editingAnnualWithdrawalId: number | null;
  requestRemoveAnnualWithdrawal: (withdrawal: DraftAnnualWithdrawal) => void;
  startAnnualWithdrawalEdit: (withdrawal: DraftAnnualWithdrawal) => void;
  submitAnnualWithdrawal: (event: FormEvent<HTMLFormElement>) => void;
  totals: {
    annualPayPeriodTotal: number;
    totalAnnualWithdrawals: number;
  };
  updateAnnualWithdrawalForm: <K extends keyof AnnualWithdrawalFormState>(
    key: K,
    value: AnnualWithdrawalFormState[K]
  ) => void;
}) {
  const isEditing = editingAnnualWithdrawalId !== null;

  return (
    <>
      <section className="section-header">
        <div>
          <h2>Annual Withdrawals</h2>
          <p>Yearly renewals, memberships, insurance, and other non-monthly outflows.</p>
        </div>
      </section>

      <section aria-label="Annual withdrawal summary" className="summary-grid">
        <div>
          <span>Annual total</span>
          <strong>{currency.format(totals.totalAnnualWithdrawals)}</strong>
        </div>
        <div>
          <span>Monthly reserve</span>
          <strong>{currency.format(totals.totalAnnualWithdrawals / 12)}</strong>
        </div>
        <div>
          <span>In pay period</span>
          <strong>{currency.format(totals.annualPayPeriodTotal)}</strong>
        </div>
      </section>

      <section className="expenses-layout">
        <div className="table-wrap">
          <table>
            <caption>Annual withdrawals are checked against the active pay period.</caption>
            <thead>
              <tr>
                <th>Withdrawal</th>
                <th>Date</th>
                <th>Amount</th>
                <th>Account</th>
                <th>Paid</th>
                <th>Pay Period</th>
                <th aria-label="Actions" />
              </tr>
            </thead>
            <tbody>
              {annualWithdrawals.map((withdrawal) => (
                <tr
                  className={withdrawal.inPayPeriod ? 'in-period' : undefined}
                  key={withdrawal.id}
                >
                  <td>{withdrawal.bill}</td>
                  <td>{formatDate(withdrawal.dueDate)}</td>
                  <td className="amount">{currency.format(withdrawal.amount)}</td>
                  <td>{withdrawal.account}</td>
                  <td>
                    <span className={withdrawal.paid ? 'pill paid' : 'pill unpaid'}>
                      {withdrawal.paid ? 'Paid' : 'Open'}
                    </span>
                  </td>
                  <td>{withdrawal.inPayPeriod ? formatDate(withdrawal.dueDate) : '-'}</td>
                  <td className="actions">
                    <button onClick={() => startAnnualWithdrawalEdit(withdrawal)} type="button">
                      Edit
                    </button>
                    <button
                      className="ghost"
                      onClick={() => requestRemoveAnnualWithdrawal(withdrawal)}
                      type="button"
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={2}>Total</td>
                <td className="amount">{currency.format(totals.totalAnnualWithdrawals)}</td>
                <td colSpan={4} />
              </tr>
            </tfoot>
          </table>
        </div>

        <form className="bill-form" onSubmit={submitAnnualWithdrawal}>
          <h2>{isEditing ? 'Edit Annual Withdrawal' : 'Add Annual Withdrawal'}</h2>
          <label>
            Withdrawal
            <input
              onChange={(event) => updateAnnualWithdrawalForm('bill', event.target.value)}
              required
              value={annualWithdrawalForm.bill}
            />
          </label>
          <label>
            Date
            <input
              onChange={(event) => updateAnnualWithdrawalForm('date', event.target.value)}
              required
              type="date"
              value={annualWithdrawalForm.date}
            />
          </label>
          <label>
            Amount
            <input
              min={0}
              onChange={(event) => updateAnnualWithdrawalForm('amount', event.target.value)}
              required
              step="0.01"
              type="number"
              value={annualWithdrawalForm.amount}
            />
          </label>
          <label>
            Account
            <input
              onChange={(event) => updateAnnualWithdrawalForm('account', event.target.value)}
              required
              value={annualWithdrawalForm.account}
            />
          </label>
          <label className="checkbox-row">
            <input
              checked={annualWithdrawalForm.paid}
              onChange={(event) => updateAnnualWithdrawalForm('paid', event.target.checked)}
              type="checkbox"
            />
            Paid
          </label>
          <div className="form-actions">
            <button type="submit">{isEditing ? 'Update Draft' : 'Add to Draft'}</button>
            {isEditing && (
              <button className="ghost" onClick={cancelAnnualWithdrawalEdit} type="button">
                Cancel
              </button>
            )}
          </div>
        </form>
      </section>
    </>
  );
}
