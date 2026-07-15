import { useReducer } from 'react';

import type { FinancialsDraft } from './financialsDraft';
import {
  createFinancialsDraftWorkspaceState,
  financialsDraftReducer,
} from './financialsDraftReducer';

export function createTestFinancialsDraft(
  overrides: Partial<FinancialsDraft> = {}
): FinancialsDraft {
  return {
    annualWithdrawals: [],
    assetCategories: [],
    bills: [],
    debtAccounts: [],
    importantDates: [],
    incomeEvents: [],
    incomeSummaryItems: [],
    payPeriodEnd: '2026-06-15',
    payPeriodStart: '2026-06-01',
    planningSettings: { payCadence: 'BIWEEKLY', timeZone: 'UTC' },
    projectionRoles: {
      primaryPaycheckIncomeSummaryItemId: 1,
      rentBillId: 1,
      rentReserveAssetAccountId: 1,
    },
    version: 1,
    ...overrides,
  };
}

export function useCanonicalDraftTestState(initialDraft: FinancialsDraft) {
  const [state, dispatch] = useReducer(
    financialsDraftReducer,
    initialDraft,
    createFinancialsDraftWorkspaceState
  );

  return { dispatch, state };
}
