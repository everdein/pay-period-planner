import type { FormEvent } from 'react';

import { EditButton } from './EditButton';
import { EmptyTableRow } from './EmptyTableRow';
import { currency } from './financialsFormatters';
import type { AssetFormState, DraftAssetAccount, DraftAssetCategory } from './financialsTypes';
import { RemoveButton } from './RemoveButton';
import { ScrollableTableRegion } from './ScrollableTableRegion';

export function AssetTable({
  assetForm,
  cancelAssetEdit,
  category,
  editingAsset,
  requestRemoveAsset,
  rentReserveAssetAccountId,
  startAssetEdit,
  submitAsset,
  updateAssetForm,
}: {
  assetForm: AssetFormState;
  cancelAssetEdit: () => void;
  category: DraftAssetCategory;
  editingAsset: { categoryKey: string; id: number } | null;
  requestRemoveAsset: (categoryKey: string, account: DraftAssetAccount) => void;
  rentReserveAssetAccountId: number;
  startAssetEdit: (categoryKey: string, account: DraftAssetAccount) => void;
  submitAsset: (categoryKey: string, event: FormEvent<HTMLFormElement>) => void;
  updateAssetForm: <K extends keyof AssetFormState>(key: K, value: AssetFormState[K]) => void;
}) {
  const isEditingThisCategory = editingAsset?.categoryKey === category.key;

  return (
    <section className="expenses-layout">
      <ScrollableTableRegion label={`${category.label} table`}>
        <table className="account-table">
          <colgroup>
            <col className="name-column" />
            <col className="company-column" />
            <col className="amount-column" />
            <col className="actions-column" />
          </colgroup>
          <caption>{category.label}</caption>
          <thead>
            <tr>
              <th>Account</th>
              <th>Company</th>
              <th>Amount</th>
              <th aria-label="Actions" />
            </tr>
          </thead>
          <tbody>
            {category.accounts.length === 0 && (
              <EmptyTableRow columns={4} message="No accounts in this category yet." />
            )}
            {category.accounts.map((account) => (
              <tr key={account.id}>
                <td data-label="Account">{account.account}</td>
                <td data-label="Company">{account.company}</td>
                <td className="amount" data-label="Amount">
                  {currency.format(account.amount)}
                </td>
                <td className="actions" data-label="Actions">
                  <EditButton
                    label={`Edit ${account.account}`}
                    onClick={() => startAssetEdit(category.key, account)}
                  />
                  <RemoveButton
                    disabled={account.id === rentReserveAssetAccountId}
                    label={`Remove ${account.account}`}
                    onClick={() => requestRemoveAsset(category.key, account)}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <p className="table-total">
          Total: <strong>{currency.format(category.total)}</strong>
        </p>
      </ScrollableTableRegion>

      <form className="bill-form" onSubmit={(event) => submitAsset(category.key, event)}>
        <h2>{isEditingThisCategory ? 'Edit Account' : 'Add Account'}</h2>
        <label>
          Account
          <input
            onChange={(event) => updateAssetForm('account', event.target.value)}
            required
            value={assetForm.account}
          />
        </label>
        <label>
          Company
          <input
            onChange={(event) => updateAssetForm('company', event.target.value)}
            required
            value={assetForm.company}
          />
        </label>
        <label>
          Amount
          <input
            min={0}
            onChange={(event) => updateAssetForm('amount', event.target.value)}
            required
            step="0.01"
            type="number"
            value={assetForm.amount}
          />
        </label>
        <div className="form-actions">
          <button type="submit">{isEditingThisCategory ? 'Update Draft' : 'Add to Draft'}</button>
          {isEditingThisCategory && (
            <button className="ghost" onClick={cancelAssetEdit} type="button">
              Cancel
            </button>
          )}
        </div>
      </form>
    </section>
  );
}
