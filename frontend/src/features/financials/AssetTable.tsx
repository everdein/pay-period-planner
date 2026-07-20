import type { FormEvent } from 'react';

import { EditButton } from './EditButton';
import { FinancialRecordList, FinancialRecordListItem } from './FinancialRecordList';
import { currency } from './financialsFormatters';
import type { AssetFormState, DraftAssetAccount, DraftAssetCategory } from './financialsTypes';
import { RemoveButton } from './RemoveButton';

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
      <FinancialRecordList
        description={`Accounts tracked in ${category.label.toLowerCase()}.`}
        emptyDescription="Add an account to begin tracking this balance category."
        emptyTitle="No accounts in this category yet."
        footer={
          <>
            Total: <strong>{currency.format(category.total)}</strong>
          </>
        }
        headingId={`asset-list-${category.key}`}
        itemCount={category.accounts.length}
        summary={`${currency.format(category.total)} total`}
        summaryLabel={`${category.label} account summary`}
        title={category.label}
      >
        {category.accounts.map((account) => (
          <FinancialRecordListItem
            actions={
              <>
                <EditButton
                  label={`Edit ${account.account}`}
                  onClick={() => startAssetEdit(category.key, account)}
                />
                <RemoveButton
                  disabled={account.id === rentReserveAssetAccountId}
                  label={`Remove ${account.account}`}
                  onClick={() => requestRemoveAsset(category.key, account)}
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
