import type { FormEvent } from 'react';

import { EditButton } from './EditButton';
import { FinancialRecordList, FinancialRecordListItem } from './FinancialRecordList';
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
        <div className="record-list-stack">
          <FinancialRecordList
            description={
              <>
                Withdrawals from {formatDate(payPeriodStart)} to {formatDate(payPeriodEnd)} are
                highlighted.
              </>
            }
            emptyDescription="Add a bill to begin building this household's monthly schedule."
            emptyTitle="No monthly withdrawals yet."
            footer={
              <>
                Monthly total: <strong>{currency.format(totals.totalMonthlyExpenses)}</strong>
              </>
            }
            headingId="monthly-withdrawal-list-heading"
            itemCount={sortedBills.length}
            summary={`${currency.format(totals.totalMonthlyExpenses)} total`}
            summaryLabel="Monthly withdrawal list summary"
            title="Monthly schedule"
          >
            {sortedBills.map((bill) => (
              <FinancialRecordListItem
                actions={
                  <>
                    <EditButton label={`Edit ${bill.bill}`} onClick={() => startEdit(bill)} />
                    <RemoveButton
                      disabled={bill.id === rentBillId}
                      label={`Remove ${bill.bill}`}
                      onClick={() => requestRemoveBill(bill)}
                    />
                  </>
                }
                badge={bill.inPayPeriod ? 'Due this pay period' : undefined}
                key={bill.id}
                metadata={[`Due ${bill.dueLabel}`, bill.account]}
                primary={bill.bill}
                state={
                  <span className={bill.paid ? 'pill paid' : 'pill unpaid'}>
                    {bill.paid ? 'Paid' : 'Open'}
                  </span>
                }
                tone={bill.inPayPeriod ? 'positive' : undefined}
                value={<strong>{currency.format(bill.amount)}</strong>}
              />
            ))}
          </FinancialRecordList>

          <FinancialRecordList
            description="Annual withdrawals due during this pay period."
            emptyDescription="Nothing annual falls within the current pay-period window."
            emptyTitle="No annual withdrawals are due."
            footer={
              <>
                Pay period annual total: <strong>{currency.format(annualPayPeriodTotal)}</strong>
              </>
            }
            headingId="annual-withdrawal-list-heading"
            itemCount={annualWithdrawalsInPayPeriod.length}
            summary={`${currency.format(annualPayPeriodTotal)} total`}
            summaryLabel="Annual withdrawal list summary"
            title="Annual withdrawals"
          >
            {annualWithdrawalsInPayPeriod.map((withdrawal) => (
              <FinancialRecordListItem
                badge="Due this pay period"
                key={withdrawal.id}
                metadata={[formatDate(withdrawal.dueDate), withdrawal.account]}
                primary={withdrawal.bill}
                state={
                  <span className={withdrawal.paid ? 'pill paid' : 'pill unpaid'}>
                    {withdrawal.paid ? 'Paid' : 'Open'}
                  </span>
                }
                tone="positive"
                value={<strong>{currency.format(withdrawal.amount)}</strong>}
              />
            ))}
          </FinancialRecordList>
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
