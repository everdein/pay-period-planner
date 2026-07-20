import type { FormEvent } from 'react';

import { EditButton } from './EditButton';
import { FinancialRecordList, FinancialRecordListItem } from './FinancialRecordList';
import { currency } from './financialsFormatters';
import type { AssetFormState, DraftDebtAccount } from './financialsTypes';
import { RemoveButton } from './RemoveButton';

export function DebtTab({
  cancelDebtEdit,
  debtAccounts,
  debtForm,
  editingDebtId,
  requestRemoveDebt,
  startDebtEdit,
  submitDebt,
  totalDebt,
  updateDebtForm,
}: {
  cancelDebtEdit: () => void;
  debtAccounts: DraftDebtAccount[];
  debtForm: AssetFormState;
  editingDebtId: number | null;
  requestRemoveDebt: (account: DraftDebtAccount) => void;
  startDebtEdit: (account: DraftDebtAccount) => void;
  submitDebt: (event: FormEvent<HTMLFormElement>) => void;
  totalDebt: number;
  updateDebtForm: <K extends keyof AssetFormState>(key: K, value: AssetFormState[K]) => void;
}) {
  const isEditing = editingDebtId !== null;

  return (
    <>
      <section className="section-header">
        <div>
          <h2>Debt</h2>
          <p>Outstanding credit cards, credit lines, and financed balances.</p>
        </div>
      </section>

      <section aria-label="Debt summary" className="summary-grid">
        <div>
          <span>Total debt</span>
          <strong>{currency.format(totalDebt)}</strong>
        </div>
      </section>

      <section className="expenses-layout">
        <FinancialRecordList
          description="Outstanding balances grouped by account and lender."
          emptyDescription="Add a debt account to begin tracking outstanding balances."
          emptyTitle="No debt accounts yet."
          footer={
            <>
              Total debt: <strong>{currency.format(totalDebt)}</strong>
            </>
          }
          headingId="debt-balance-list-heading"
          itemCount={debtAccounts.length}
          summary={`${currency.format(totalDebt)} total`}
          summaryLabel="Debt balance summary"
          title="Debt balances"
        >
          {debtAccounts.map((account) => (
            <FinancialRecordListItem
              actions={
                <>
                  <EditButton
                    label={`Edit ${account.account}`}
                    onClick={() => startDebtEdit(account)}
                  />
                  <RemoveButton
                    label={`Remove ${account.account}`}
                    onClick={() => requestRemoveDebt(account)}
                  />
                </>
              }
              key={account.id}
              metadata={[account.company]}
              primary={account.account}
              value={<strong>{currency.format(account.amount)}</strong>}
            />
          ))}
        </FinancialRecordList>

        <form className="bill-form" onSubmit={submitDebt}>
          <h2>{isEditing ? 'Edit Debt Account' : 'Add Debt Account'}</h2>
          <label>
            Account
            <input
              onChange={(event) => updateDebtForm('account', event.target.value)}
              required
              value={debtForm.account}
            />
          </label>
          <label>
            Company / Lender
            <input
              onChange={(event) => updateDebtForm('company', event.target.value)}
              required
              value={debtForm.company}
            />
          </label>
          <label>
            Balance
            <input
              min={0}
              onChange={(event) => updateDebtForm('amount', event.target.value)}
              required
              step="0.01"
              type="number"
              value={debtForm.amount}
            />
          </label>
          <div className="form-actions">
            <button type="submit">{isEditing ? 'Update Draft' : 'Add to Draft'}</button>
            {isEditing && (
              <button className="ghost" onClick={cancelDebtEdit} type="button">
                Cancel
              </button>
            )}
          </div>
        </form>
      </section>
    </>
  );
}
