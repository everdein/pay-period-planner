import type { FormEvent } from 'react';

import { EditButton } from './EditButton';
import { FinancialRecordList, FinancialRecordListItem } from './FinancialRecordList';
import { currency, formatDate } from './financialsFormatters';
import type { AnnualWithdrawalFormState, DraftAnnualWithdrawal } from './financialsTypes';
import { RemoveButton } from './RemoveButton';

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
        <FinancialRecordList
          description="Annual withdrawals are checked against the active pay period."
          emptyDescription="Add a yearly renewal, membership, or other recurring charge."
          emptyTitle="No annual withdrawals yet."
          footer={
            <>
              Annual total: <strong>{currency.format(totals.totalAnnualWithdrawals)}</strong>
            </>
          }
          headingId="annual-schedule-heading"
          itemCount={annualWithdrawals.length}
          summary={`${currency.format(totals.totalAnnualWithdrawals)} total`}
          summaryLabel="Annual withdrawal schedule summary"
          title="Annual schedule"
        >
          {annualWithdrawals.map((withdrawal) => (
            <FinancialRecordListItem
              actions={
                <>
                  <EditButton
                    label={`Edit ${withdrawal.bill}`}
                    onClick={() => startAnnualWithdrawalEdit(withdrawal)}
                  />
                  <RemoveButton
                    label={`Remove ${withdrawal.bill}`}
                    onClick={() => requestRemoveAnnualWithdrawal(withdrawal)}
                  />
                </>
              }
              badge={withdrawal.inPayPeriod ? 'Due this pay period' : undefined}
              key={withdrawal.id}
              metadata={[`Due ${formatDate(withdrawal.dueDate)}`, withdrawal.account]}
              primary={withdrawal.bill}
              state={
                <span className={withdrawal.paid ? 'pill paid' : 'pill unpaid'}>
                  {withdrawal.paid ? 'Paid' : 'Open'}
                </span>
              }
              tone={withdrawal.inPayPeriod ? 'positive' : undefined}
              value={<strong>{currency.format(withdrawal.amount)}</strong>}
            />
          ))}
        </FinancialRecordList>

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
