import type { FormEvent } from 'react';

import { EditButton } from './EditButton';
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
  rentBillId,
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
  rentBillId: number;
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
          <section
            aria-labelledby="monthly-withdrawal-list-heading"
            className="withdrawal-list-section"
          >
            <header className="withdrawal-list-header">
              <div className="withdrawal-list-heading">
                <h3 id="monthly-withdrawal-list-heading">Monthly schedule</h3>
                <p>
                  Withdrawals from {formatDate(payPeriodStart)} to {formatDate(payPeriodEnd)} are
                  highlighted.
                </p>
              </div>
              <div aria-label="Monthly withdrawal list summary" className="withdrawal-list-summary">
                <span>
                  {sortedBills.length} {sortedBills.length === 1 ? 'item' : 'items'}
                </span>
                <strong>{currency.format(totals.totalMonthlyExpenses)} total</strong>
              </div>
            </header>

            {sortedBills.length > 0 ? (
              <ul className="withdrawal-list">
                {sortedBills.map((bill) => (
                  <li
                    className={`withdrawal-list-item${bill.inPayPeriod ? ' in-period' : ''}`}
                    key={bill.id}
                  >
                    <div className="withdrawal-list-primary">
                      <span className="withdrawal-list-name">{bill.bill}</span>
                      {bill.inPayPeriod && (
                        <span className="withdrawal-list-description">Due this pay period</span>
                      )}
                    </div>

                    <strong className="withdrawal-list-amount">
                      {currency.format(bill.amount)}
                    </strong>

                    <div className="withdrawal-list-meta">
                      <span className="withdrawal-list-meta-item">Due {bill.dueLabel}</span>
                      <span className="withdrawal-list-meta-item withdrawal-list-account">
                        {bill.account}
                      </span>
                    </div>

                    <div className="withdrawal-list-controls">
                      <span className={bill.paid ? 'pill paid' : 'pill unpaid'}>
                        {bill.paid ? 'Paid' : 'Open'}
                      </span>
                      <div className="withdrawal-list-actions">
                        <EditButton label={`Edit ${bill.bill}`} onClick={() => startEdit(bill)} />
                        <RemoveButton
                          disabled={bill.id === rentBillId}
                          label={`Remove ${bill.bill}`}
                          onClick={() => requestRemoveBill(bill)}
                        />
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            ) : (
              <div className="withdrawal-list-empty">
                <strong>No monthly withdrawals yet.</strong>
                <p>Add a bill to begin building this household&apos;s monthly schedule.</p>
              </div>
            )}

            <p className="withdrawal-list-total">
              Monthly total: <strong>{currency.format(totals.totalMonthlyExpenses)}</strong>
            </p>
          </section>

          <section
            aria-labelledby="annual-withdrawal-list-heading"
            className="withdrawal-list-section"
          >
            <header className="withdrawal-list-header">
              <div className="withdrawal-list-heading">
                <h3 id="annual-withdrawal-list-heading">Annual withdrawals</h3>
                <p>Annual withdrawals due during this pay period.</p>
              </div>
              <div aria-label="Annual withdrawal list summary" className="withdrawal-list-summary">
                <span>
                  {annualWithdrawalsInPayPeriod.length}{' '}
                  {annualWithdrawalsInPayPeriod.length === 1 ? 'item' : 'items'}
                </span>
                <strong>{currency.format(annualPayPeriodTotal)} total</strong>
              </div>
            </header>

            {annualWithdrawalsInPayPeriod.length > 0 ? (
              <ul className="withdrawal-list">
                {annualWithdrawalsInPayPeriod.map((withdrawal) => (
                  <li className="withdrawal-list-item in-period" key={withdrawal.id}>
                    <div className="withdrawal-list-primary">
                      <span className="withdrawal-list-name">{withdrawal.bill}</span>
                      <span className="withdrawal-list-description">Due this pay period</span>
                    </div>

                    <strong className="withdrawal-list-amount">
                      {currency.format(withdrawal.amount)}
                    </strong>

                    <div className="withdrawal-list-meta">
                      <span className="withdrawal-list-meta-item">
                        {formatDate(withdrawal.dueDate)}
                      </span>
                      <span className="withdrawal-list-meta-item withdrawal-list-account">
                        {withdrawal.account}
                      </span>
                    </div>

                    <div className="withdrawal-list-controls">
                      <span className={withdrawal.paid ? 'pill paid' : 'pill unpaid'}>
                        {withdrawal.paid ? 'Paid' : 'Open'}
                      </span>
                    </div>
                  </li>
                ))}
              </ul>
            ) : (
              <div className="withdrawal-list-empty">
                <strong>No annual withdrawals are due.</strong>
                <p>Nothing annual falls within the current pay-period window.</p>
              </div>
            )}

            <p className="withdrawal-list-total">
              Pay period annual total: <strong>{currency.format(annualPayPeriodTotal)}</strong>
            </p>
          </section>
        </div>

        <form className="bill-form" onSubmit={submitBill}>
          <h2>{formTitle}</h2>
          <label>
            Withdrawal
            <input
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
