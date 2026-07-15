import type { FormEvent } from 'react';

import { EditButton } from './EditButton';
import { EmptyTableRow } from './EmptyTableRow';
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
        <div className="table-wrap">
          <table className="account-table">
            <colgroup>
              <col className="name-column" />
              <col className="company-column" />
              <col className="amount-column" />
              <col className="actions-column" />
            </colgroup>
            <caption>Debt balances</caption>
            <thead>
              <tr>
                <th>Account</th>
                <th>Company / Lender</th>
                <th>Balance</th>
                <th aria-label="Actions" />
              </tr>
            </thead>
            <tbody>
              {debtAccounts.length === 0 && (
                <EmptyTableRow columns={4} message="No debt accounts yet." />
              )}
              {debtAccounts.map((account) => (
                <tr key={account.id}>
                  <td>{account.account}</td>
                  <td>{account.company}</td>
                  <td className="amount">{currency.format(account.amount)}</td>
                  <td className="actions">
                    <EditButton
                      label={`Edit ${account.account}`}
                      onClick={() => startDebtEdit(account)}
                    />
                    <RemoveButton
                      label={`Remove ${account.account}`}
                      onClick={() => requestRemoveDebt(account)}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="table-total">
            Total debt: <strong>{currency.format(totalDebt)}</strong>
          </p>
        </div>

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
