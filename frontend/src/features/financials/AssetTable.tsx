import type { FormEvent } from 'react';

import { currency } from './financialsFormatters';
import type { AssetFormState, DraftAssetAccount, DraftAssetCategory } from './financialsTypes';

export function AssetTable({
  assetForm,
  cancelAssetEdit,
  category,
  editingAsset,
  requestRemoveAsset,
  startAssetEdit,
  submitAsset,
  updateAssetForm,
}: {
  assetForm: AssetFormState;
  cancelAssetEdit: () => void;
  category: DraftAssetCategory;
  editingAsset: { categoryKey: string; id: number } | null;
  requestRemoveAsset: (categoryKey: string, account: DraftAssetAccount) => void;
  startAssetEdit: (categoryKey: string, account: DraftAssetAccount) => void;
  submitAsset: (categoryKey: string, event: FormEvent<HTMLFormElement>) => void;
  updateAssetForm: <K extends keyof AssetFormState>(key: K, value: AssetFormState[K]) => void;
}) {
  const isEditingThisCategory = editingAsset?.categoryKey === category.key;

  return (
    <section className="expenses-layout">
      <div className="table-wrap">
        <table>
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
            {category.accounts.map((account) => (
              <tr key={account.id}>
                <td>{account.account}</td>
                <td>{account.company}</td>
                <td className="amount">{currency.format(account.amount)}</td>
                <td className="actions">
                  <button onClick={() => startAssetEdit(category.key, account)} type="button">
                    Edit
                  </button>
                  <button
                    className="ghost"
                    onClick={() => requestRemoveAsset(category.key, account)}
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
              <td className="amount">{currency.format(category.total)}</td>
              <td />
            </tr>
          </tfoot>
        </table>
      </div>

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
