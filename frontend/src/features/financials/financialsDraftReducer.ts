import type { Dispatch } from 'react';

import type {
  ExpenseSnapshot,
  ExpenseSnapshotRequest,
  FinancialPlanningSettings,
  FinancialProjectionRoles,
} from '../../api/endpoints/financials';
import {
  buildExpenseSnapshotRequest,
  createFinancialsDraft,
  type FinancialsDraft,
  formToAnnualWithdrawal,
  formToAssetAccount,
  formToDebtAccount,
  formToDraftBill,
  formToImportantDate,
  formToIncomeEvent,
  formToIncomeSummaryItem,
  generateRecurringPaydays,
  isNumberedIncomeEventInYear,
  recalculateAssetCategory,
  removalItemType,
  toDraftAnnualWithdrawal,
  toDraftBill,
} from './financialsDraft';
import type {
  AnnualWithdrawalFormState,
  AssetFormState,
  BillFormState,
  ImportantDateFormState,
  IncomeEventFormState,
  IncomeSummaryFormState,
  PendingRemoval,
  RecurringPaydayFormState,
} from './financialsTypes';

export type FinancialsDraftWorkspaceState = {
  baseline: FinancialsDraft | null;
  baselineRevision: number;
  draft: FinancialsDraft | null;
  nextTemporaryId: number;
  pendingRemoval: PendingRemoval | null;
  resetGeneration: number;
  revision: number;
};

export type FinancialsDraftAction =
  | { savedRevision: number | null; snapshot: ExpenseSnapshot; type: 'synchronize' }
  | { type: 'reset' }
  | { type: 'cancel-removal' }
  | { type: 'confirm-removal' }
  | { removal: PendingRemoval; type: 'request-removal' }
  | { type: 'update-pay-period-start'; value: string }
  | { type: 'update-pay-period-end'; value: string }
  | {
      setting: keyof FinancialPlanningSettings;
      type: 'update-planning-setting';
      value: string;
    }
  | {
      recordId: number;
      role: keyof FinancialProjectionRoles;
      type: 'update-projection-role';
    }
  | { editingId: number | null; form: BillFormState; type: 'save-bill' }
  | {
      editingId: number | null;
      form: AnnualWithdrawalFormState;
      type: 'save-annual-withdrawal';
    }
  | {
      categoryKey: string;
      editingId: number | null;
      form: AssetFormState;
      type: 'save-asset';
    }
  | { editingId: number | null; form: AssetFormState; type: 'save-debt' }
  | {
      editingId: number | null;
      form: IncomeSummaryFormState;
      type: 'save-income-summary-item';
    }
  | { editingId: number | null; form: IncomeEventFormState; type: 'save-income-event' }
  | { form: RecurringPaydayFormState; type: 'generate-recurring-paydays' }
  | { editingId: number | null; form: ImportantDateFormState; type: 'save-important-date' };

export type FinancialsDraftDispatch = Dispatch<FinancialsDraftAction>;

export const initialFinancialsDraftWorkspaceState: FinancialsDraftWorkspaceState = {
  baseline: null,
  baselineRevision: 0,
  draft: null,
  nextTemporaryId: -1,
  pendingRemoval: null,
  resetGeneration: 0,
  revision: 0,
};

export function createFinancialsDraftWorkspaceState(
  draft: FinancialsDraft | null = null
): FinancialsDraftWorkspaceState {
  return { ...initialFinancialsDraftWorkspaceState, baseline: draft, draft };
}

export function financialsDraftReducer(
  state: FinancialsDraftWorkspaceState,
  action: FinancialsDraftAction
): FinancialsDraftWorkspaceState {
  switch (action.type) {
    case 'synchronize':
      return synchronizeDraft(state, action.snapshot, action.savedRevision);
    case 'reset':
      return resetDraft(state);
    case 'cancel-removal':
      return state.pendingRemoval ? { ...state, pendingRemoval: null } : state;
    case 'request-removal':
      return requestRemoval(state, action.removal);
    case 'confirm-removal':
      return confirmRemoval(state);
    case 'update-pay-period-start':
      return updatePayPeriod(state, action.value, 'start');
    case 'update-pay-period-end':
      return updatePayPeriod(state, action.value, 'end');
    case 'update-planning-setting':
      return updatePlanningSetting(state, action.setting, action.value);
    case 'update-projection-role':
      return updateProjectionRole(state, action.role, action.recordId);
    case 'save-bill':
      return saveBill(state, action.editingId, action.form);
    case 'save-annual-withdrawal':
      return saveAnnualWithdrawal(state, action.editingId, action.form);
    case 'save-asset':
      return saveAsset(state, action.categoryKey, action.editingId, action.form);
    case 'save-debt':
      return saveDebt(state, action.editingId, action.form);
    case 'save-income-summary-item':
      return saveIncomeSummaryItem(state, action.editingId, action.form);
    case 'save-income-event':
      return saveIncomeEvent(state, action.editingId, action.form);
    case 'generate-recurring-paydays':
      return generatePaydays(state, action.form);
    case 'save-important-date':
      return saveImportantDate(state, action.editingId, action.form);
  }
}

export function selectCommittedBaseline(state: FinancialsDraftWorkspaceState) {
  return state.baseline;
}

export function selectFinancialsDraft(state: FinancialsDraftWorkspaceState) {
  return state.draft;
}

export function selectDraftRevision(state: FinancialsDraftWorkspaceState) {
  return state.revision;
}

export function selectDraftSnapshotVersion(state: FinancialsDraftWorkspaceState) {
  return state.draft?.version ?? null;
}

export function selectFinancialsDraftIsDirty(state: FinancialsDraftWorkspaceState) {
  return state.draft !== null && state.draft !== state.baseline;
}

export function selectFinancialsSaveRequest(
  state: FinancialsDraftWorkspaceState
): ExpenseSnapshotRequest | null {
  return state.draft ? buildExpenseSnapshotRequest(state.draft) : null;
}

export function selectRemovalConfirmation(state: FinancialsDraftWorkspaceState) {
  return state.pendingRemoval
    ? {
        itemName: state.pendingRemoval.name,
        itemType: removalItemType(state.pendingRemoval),
      }
    : null;
}

function synchronizeDraft(
  state: FinancialsDraftWorkspaceState,
  snapshot: ExpenseSnapshot,
  savedRevision: number | null
) {
  const incoming = createFinancialsDraft(snapshot);

  if (savedRevision !== null && state.draft && state.revision > savedRevision) {
    return {
      ...state,
      baseline: incoming,
      baselineRevision: savedRevision,
      draft: { ...state.draft, version: incoming.version },
    };
  }

  const revision = savedRevision ?? 0;
  return {
    baseline: incoming,
    baselineRevision: revision,
    draft: incoming,
    nextTemporaryId: -1,
    pendingRemoval: null,
    resetGeneration: state.resetGeneration + 1,
    revision,
  };
}

function resetDraft(state: FinancialsDraftWorkspaceState) {
  if (!state.baseline) {
    return state;
  }

  return {
    ...state,
    draft: state.baseline,
    nextTemporaryId: -1,
    pendingRemoval: null,
    resetGeneration: state.resetGeneration + 1,
  };
}

function updatePayPeriod(
  state: FinancialsDraftWorkspaceState,
  value: string,
  boundary: 'end' | 'start'
) {
  return updateDraft(state, (draft) => {
    const payPeriodStart = boundary === 'start' ? value : draft.payPeriodStart;
    const payPeriodEnd = boundary === 'end' ? value : draft.payPeriodEnd;
    if (payPeriodStart === draft.payPeriodStart && payPeriodEnd === draft.payPeriodEnd) {
      return draft;
    }

    return {
      ...draft,
      annualWithdrawals: draft.annualWithdrawals.map((withdrawal) =>
        toDraftAnnualWithdrawal(withdrawal, payPeriodStart, payPeriodEnd)
      ),
      bills: draft.bills.map((bill) => toDraftBill(bill, payPeriodStart, payPeriodEnd)),
      payPeriodEnd,
      payPeriodStart,
    };
  });
}

function updatePlanningSetting(
  state: FinancialsDraftWorkspaceState,
  setting: keyof FinancialPlanningSettings,
  value: string
) {
  return updateDraft(state, (draft) => {
    if (draft.planningSettings[setting] === value) {
      return draft;
    }

    return {
      ...draft,
      planningSettings: { ...draft.planningSettings, [setting]: value },
    } as FinancialsDraft;
  });
}

function saveBill(
  state: FinancialsDraftWorkspaceState,
  editingId: number | null,
  form: BillFormState
) {
  if (!state.draft) {
    return state;
  }

  const existing = editingId === null ? null : state.draft.bills.find(({ id }) => id === editingId);
  if (editingId !== null && !existing) {
    return state;
  }

  const id = existing?.id ?? state.nextTemporaryId;
  const savedBill = formToDraftBill(id, form, state.draft.payPeriodStart, state.draft.payPeriodEnd);
  const bills = existing
    ? state.draft.bills.map((bill) => (bill.id === existing.id ? savedBill : bill))
    : [...state.draft.bills, savedBill];

  return commitDraft(
    state,
    { ...state.draft, bills },
    existing ? state.nextTemporaryId : state.nextTemporaryId - 1
  );
}

function saveAnnualWithdrawal(
  state: FinancialsDraftWorkspaceState,
  editingId: number | null,
  form: AnnualWithdrawalFormState
) {
  if (!state.draft) {
    return state;
  }

  const existing =
    editingId === null ? null : state.draft.annualWithdrawals.find(({ id }) => id === editingId);
  if (editingId !== null && !existing) {
    return state;
  }

  const id = existing?.id ?? state.nextTemporaryId;
  const savedWithdrawal = formToAnnualWithdrawal(
    id,
    form,
    state.draft.payPeriodStart,
    state.draft.payPeriodEnd
  );
  const annualWithdrawals = existing
    ? state.draft.annualWithdrawals.map((withdrawal) =>
        withdrawal.id === existing.id ? savedWithdrawal : withdrawal
      )
    : [...state.draft.annualWithdrawals, savedWithdrawal];

  return commitDraft(
    state,
    { ...state.draft, annualWithdrawals },
    existing ? state.nextTemporaryId : state.nextTemporaryId - 1
  );
}

function saveAsset(
  state: FinancialsDraftWorkspaceState,
  categoryKey: string,
  editingId: number | null,
  form: AssetFormState
) {
  if (!state.draft) {
    return state;
  }

  const category = state.draft.assetCategories.find(({ key }) => key === categoryKey);
  const existing =
    editingId === null ? null : (category?.accounts.find(({ id }) => id === editingId) ?? null);
  if (!category || (editingId !== null && !existing)) {
    return state;
  }

  const id = existing?.id ?? state.nextTemporaryId;
  const savedAccount = formToAssetAccount(id, form);
  const assetCategories = state.draft.assetCategories.map((candidate) =>
    candidate.key === categoryKey
      ? recalculateAssetCategory({
          ...candidate,
          accounts: existing
            ? candidate.accounts.map((account) =>
                account.id === existing.id ? savedAccount : account
              )
            : [...candidate.accounts, savedAccount],
        })
      : candidate
  );

  return commitDraft(
    state,
    { ...state.draft, assetCategories },
    existing ? state.nextTemporaryId : state.nextTemporaryId - 1
  );
}

function saveDebt(
  state: FinancialsDraftWorkspaceState,
  editingId: number | null,
  form: AssetFormState
) {
  if (!state.draft) {
    return state;
  }

  const existing =
    editingId === null ? null : state.draft.debtAccounts.find(({ id }) => id === editingId);
  if (editingId !== null && !existing) {
    return state;
  }

  const id = existing?.id ?? state.nextTemporaryId;
  const savedAccount = formToDebtAccount(id, form);
  const debtAccounts = existing
    ? state.draft.debtAccounts.map((account) =>
        account.id === existing.id ? savedAccount : account
      )
    : [...state.draft.debtAccounts, savedAccount];

  return commitDraft(
    state,
    { ...state.draft, debtAccounts },
    existing ? state.nextTemporaryId : state.nextTemporaryId - 1
  );
}

function saveIncomeSummaryItem(
  state: FinancialsDraftWorkspaceState,
  editingId: number | null,
  form: IncomeSummaryFormState
) {
  if (!state.draft) {
    return state;
  }

  const editingSource =
    editingId === null ? null : state.draft.incomeSummaryItems.find(({ id }) => id === editingId);
  if (editingId !== null && !editingSource) {
    return state;
  }

  const sourceForm = { ...form, category: form.category.trim(), interval: form.interval.trim() };
  const matchingSource =
    editingId === null
      ? state.draft.incomeSummaryItems.find((item) => incomeSummarySourceMatches(item, sourceForm))
      : null;
  const targetId = editingSource?.id ?? matchingSource?.id ?? state.nextTemporaryId;
  const savedItem = formToIncomeSummaryItem(targetId, sourceForm);
  const hasExistingSource = state.draft.incomeSummaryItems.some(({ id }) => id === targetId);
  const incomeSummaryItems = hasExistingSource
    ? state.draft.incomeSummaryItems.map((item) => (item.id === targetId ? savedItem : item))
    : [...state.draft.incomeSummaryItems, savedItem];

  return commitDraft(
    state,
    { ...state.draft, incomeSummaryItems },
    hasExistingSource ? state.nextTemporaryId : state.nextTemporaryId - 1
  );
}

function saveIncomeEvent(
  state: FinancialsDraftWorkspaceState,
  editingId: number | null,
  form: IncomeEventFormState
) {
  if (!state.draft) {
    return state;
  }

  const existing =
    editingId === null ? null : state.draft.incomeEvents.find(({ id }) => id === editingId);
  if (editingId !== null && !existing) {
    return state;
  }

  const id = existing?.id ?? state.nextTemporaryId;
  const savedEvent = formToIncomeEvent(id, form);
  const incomeEvents = existing
    ? state.draft.incomeEvents.map((event) => (event.id === existing.id ? savedEvent : event))
    : [...state.draft.incomeEvents, savedEvent];

  return commitDraft(
    state,
    { ...state.draft, incomeEvents },
    existing ? state.nextTemporaryId : state.nextTemporaryId - 1
  );
}

function generatePaydays(state: FinancialsDraftWorkspaceState, form: RecurringPaydayFormState) {
  if (!state.draft) {
    return state;
  }

  const generatedPaydays = generateRecurringPaydays(form, state.nextTemporaryId);
  if (generatedPaydays.length === 0) {
    return state;
  }

  const retainedEvents = form.replaceExistingYear
    ? state.draft.incomeEvents.filter((event) => !isNumberedIncomeEventInYear(event, form.year))
    : state.draft.incomeEvents;

  return commitDraft(
    state,
    { ...state.draft, incomeEvents: [...retainedEvents, ...generatedPaydays] },
    state.nextTemporaryId - generatedPaydays.length
  );
}

function saveImportantDate(
  state: FinancialsDraftWorkspaceState,
  editingId: number | null,
  form: ImportantDateFormState
) {
  if (!state.draft) {
    return state;
  }

  const existing =
    editingId === null ? null : state.draft.importantDates.find(({ id }) => id === editingId);
  if (editingId !== null && !existing) {
    return state;
  }

  const id = existing?.id ?? state.nextTemporaryId;
  const savedDate = formToImportantDate(id, form);
  const importantDates = existing
    ? state.draft.importantDates.map((date) => (date.id === existing.id ? savedDate : date))
    : [...state.draft.importantDates, savedDate];

  return commitDraft(
    state,
    { ...state.draft, importantDates },
    existing ? state.nextTemporaryId : state.nextTemporaryId - 1
  );
}

function requestRemoval(state: FinancialsDraftWorkspaceState, removal: PendingRemoval) {
  return state.draft && canRemove(state.draft, removal)
    ? { ...state, pendingRemoval: removal }
    : state;
}

function canRemove(draft: FinancialsDraft, removal: PendingRemoval) {
  switch (removal.type) {
    case 'bill': {
      const bill = draft.bills.find(({ id }) => id === removal.id);
      return Boolean(bill && bill.id !== draft.projectionRoles.rentBillId);
    }
    case 'annual-withdrawal':
      return draft.annualWithdrawals.some(({ id }) => id === removal.id);
    case 'asset': {
      const account = draft.assetCategories
        .find(({ key }) => key === removal.categoryKey)
        ?.accounts.find(({ id }) => id === removal.id);
      return Boolean(account && account.id !== draft.projectionRoles.rentReserveAssetAccountId);
    }
    case 'debt':
      return draft.debtAccounts.some(({ id }) => id === removal.id);
    case 'income-summary': {
      const item = draft.incomeSummaryItems.find(({ id }) => id === removal.id);
      return Boolean(item && item.id !== draft.projectionRoles.primaryPaycheckIncomeSummaryItemId);
    }
    case 'income':
      return draft.incomeEvents.some(({ id }) => id === removal.id);
    case 'important-date':
      return draft.importantDates.some(({ id }) => id === removal.id);
  }
}

function updateProjectionRole(
  state: FinancialsDraftWorkspaceState,
  role: keyof FinancialProjectionRoles,
  recordId: number
) {
  return updateDraft(state, (draft) => {
    const recordExists =
      role === 'rentBillId'
        ? draft.bills.some(({ id }) => id === recordId)
        : role === 'rentReserveAssetAccountId'
          ? draft.assetCategories.some((category) =>
              category.accounts.some(({ id }) => id === recordId)
            )
          : draft.incomeSummaryItems.some(({ id }) => id === recordId);
    if (!recordExists || draft.projectionRoles[role] === recordId) {
      return draft;
    }
    return {
      ...draft,
      projectionRoles: { ...draft.projectionRoles, [role]: recordId },
    };
  });
}

function confirmRemoval(state: FinancialsDraftWorkspaceState) {
  if (!state.draft || !state.pendingRemoval) {
    return state;
  }

  const pendingRemoval = state.pendingRemoval;
  let draft: FinancialsDraft;

  switch (pendingRemoval.type) {
    case 'bill':
      draft = {
        ...state.draft,
        bills: state.draft.bills.filter(({ id }) => id !== pendingRemoval.id),
      };
      break;
    case 'annual-withdrawal':
      draft = {
        ...state.draft,
        annualWithdrawals: state.draft.annualWithdrawals.filter(
          ({ id }) => id !== pendingRemoval.id
        ),
      };
      break;
    case 'asset':
      draft = {
        ...state.draft,
        assetCategories: state.draft.assetCategories.map((category) =>
          category.key === pendingRemoval.categoryKey
            ? recalculateAssetCategory({
                ...category,
                accounts: category.accounts.filter(({ id }) => id !== pendingRemoval.id),
              })
            : category
        ),
      };
      break;
    case 'debt':
      draft = {
        ...state.draft,
        debtAccounts: state.draft.debtAccounts.filter(({ id }) => id !== pendingRemoval.id),
      };
      break;
    case 'income-summary':
      draft = {
        ...state.draft,
        incomeSummaryItems: state.draft.incomeSummaryItems.filter(
          ({ id }) => id !== pendingRemoval.id
        ),
      };
      break;
    case 'income':
      draft = {
        ...state.draft,
        incomeEvents: state.draft.incomeEvents.filter(({ id }) => id !== pendingRemoval.id),
      };
      break;
    case 'important-date':
      draft = {
        ...state.draft,
        importantDates: state.draft.importantDates.filter(({ id }) => id !== pendingRemoval.id),
      };
      break;
  }

  return {
    ...commitDraft(state, draft, state.nextTemporaryId),
    pendingRemoval: null,
  };
}

function updateDraft(
  state: FinancialsDraftWorkspaceState,
  update: (draft: FinancialsDraft) => FinancialsDraft
) {
  if (!state.draft) {
    return state;
  }

  const draft = update(state.draft);
  return draft === state.draft ? state : commitDraft(state, draft, state.nextTemporaryId);
}

function commitDraft(
  state: FinancialsDraftWorkspaceState,
  draft: FinancialsDraft,
  nextTemporaryId: number
): FinancialsDraftWorkspaceState {
  return {
    ...state,
    draft,
    nextTemporaryId,
    revision: state.revision + 1,
  };
}

function incomeSummarySourceMatches(
  item: Pick<FinancialsDraft['incomeSummaryItems'][number], 'category' | 'interval'>,
  form: Pick<IncomeSummaryFormState, 'category' | 'interval'>
) {
  return (
    item.category.trim().toLowerCase() === form.category.trim().toLowerCase() &&
    item.interval.trim().toLowerCase() === form.interval.trim().toLowerCase()
  );
}
