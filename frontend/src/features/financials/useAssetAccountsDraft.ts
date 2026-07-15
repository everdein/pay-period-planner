import { type FormEvent, useEffect, useMemo, useState } from 'react';

import { emptyAssetForm, type FinancialsDraft, toAssetForm } from './financialsDraft';
import type { FinancialsDraftDispatch } from './financialsDraftReducer';
import type { AssetFormState, DraftAssetAccount, DraftAssetCategory } from './financialsTypes';

const emptyAssetCategories: DraftAssetCategory[] = [];

export function useAssetAccountsDraft(
  draft: FinancialsDraft | null,
  dispatch: FinancialsDraftDispatch,
  resetGeneration: number
) {
  const [assetForm, setAssetForm] = useState<AssetFormState>(emptyAssetForm);
  const [editingAsset, setEditingAsset] = useState<{ categoryKey: string; id: number } | null>(
    null
  );
  const draftAssetCategories = draft?.assetCategories ?? emptyAssetCategories;

  useEffect(() => {
    setAssetForm(emptyAssetForm);
    setEditingAsset(null);
  }, [resetGeneration]);

  const totalTrackedAssets = useMemo(
    () => draftAssetCategories.reduce((total, category) => total + category.total, 0),
    [draftAssetCategories]
  );

  function updateAssetForm<K extends keyof AssetFormState>(key: K, value: AssetFormState[K]) {
    setAssetForm((current) => ({ ...current, [key]: value }));
  }

  function submitAsset(categoryKey: string, event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    dispatch({
      categoryKey,
      editingId: editingAsset?.categoryKey === categoryKey ? editingAsset.id : null,
      form: assetForm,
      type: 'save-asset',
    });
    cancelAssetEdit();
  }

  function startAssetEdit(categoryKey: string, account: DraftAssetAccount) {
    setEditingAsset({ categoryKey, id: account.id });
    setAssetForm(toAssetForm(account));
  }

  function cancelAssetEdit() {
    setEditingAsset(null);
    setAssetForm(emptyAssetForm);
  }

  return {
    assetCategories: draftAssetCategories,
    assetForm,
    cancelAssetEdit,
    editingAsset,
    startAssetEdit,
    submitAsset,
    totalTrackedAssets,
    updateAssetForm,
  };
}
