import { describe, expect, it } from 'vitest';

import type { ExpenseSnapshot } from '../../api/endpoints/financials';
import {
  createFinancialsDraftWorkspaceState,
  financialsDraftReducer,
  initialFinancialsDraftWorkspaceState,
  selectCommittedBaseline,
  selectDraftRevision,
  selectDraftSnapshotVersion,
  selectFinancialsDraft,
  selectFinancialsDraftIsDirty,
  selectFinancialsSaveRequest,
  selectRemovalConfirmation,
} from './financialsDraftReducer';
import { createTestFinancialsDraft } from './financialsDraftTestSupport';
import type { DraftBill } from './financialsTypes';

function bill(overrides: Partial<DraftBill> = {}): DraftBill {
  return {
    account: 'Checking',
    amount: 100,
    bill: 'Utilities',
    dueDate: '2026-06-10',
    dueDay: 10,
    dueLabel: '10th',
    id: 2,
    inPayPeriod: true,
    paid: false,
    ...overrides,
  };
}

function snapshot(overrides: Partial<ExpenseSnapshot> = {}): ExpenseSnapshot {
  return {
    annualPayPeriodTotal: 0,
    annualWithdrawals: [],
    assetCategories: [],
    bills: [],
    currentDate: '2026-06-10',
    debtAccounts: [],
    importantDates: [],
    incomeEvents: [],
    incomeSummaryItems: [],
    netWorth: 0,
    paidTotal: 0,
    payPeriodEnd: '2026-06-15',
    payPeriodStart: '2026-06-01',
    planningSettings: { payCadence: 'BIWEEKLY', timeZone: 'UTC' },
    projectionRoles: {
      primaryPaycheckIncomeSummaryItemId: 1,
      rentBillId: 1,
      rentReserveAssetAccountId: 1,
    },
    payPeriodTotal: 0,
    totalAnnualWithdrawals: 0,
    totalDebt: 0,
    totalMonthlyExpenses: 0,
    totalTrackedAssets: 0,
    unpaidTotal: 0,
    version: 1,
    ...overrides,
  };
}

describe('financialsDraftReducer', () => {
  it('owns the baseline, editable aggregate, version, revision, and save request', () => {
    const draft = createTestFinancialsDraft({ bills: [bill()], version: 7 });
    const state = createFinancialsDraftWorkspaceState(draft);

    expect(selectCommittedBaseline(state)).toBe(draft);
    expect(selectFinancialsDraft(state)).toBe(draft);
    expect(selectDraftSnapshotVersion(state)).toBe(7);
    expect(selectDraftRevision(state)).toBe(0);
    expect(selectFinancialsDraftIsDirty(state)).toBe(false);
    expect(selectFinancialsSaveRequest(state)).toMatchObject({
      bills: [expect.objectContaining({ bill: 'Utilities', id: 2 })],
      version: 7,
    });
  });

  it('allocates temporary identities and revisions across domain commands', () => {
    let state = createFinancialsDraftWorkspaceState(createTestFinancialsDraft());

    state = financialsDraftReducer(state, {
      editingId: null,
      form: { account: 'Checking', amount: '75', bill: 'Internet', dueDay: '5', paid: false },
      type: 'save-bill',
    });
    state = financialsDraftReducer(state, {
      editingId: null,
      form: { account: 'Card', amount: '250', company: 'Example lender' },
      type: 'save-debt',
    });

    expect(state.draft?.bills[0]?.id).toBe(-1);
    expect(state.draft?.debtAccounts[0]?.id).toBe(-2);
    expect(state.nextTemporaryId).toBe(-3);
    expect(state.revision).toBe(2);
    expect(selectFinancialsDraftIsDirty(state)).toBe(true);
  });

  it('keeps anchor removal as a no-op and resets a confirmed removal to baseline', () => {
    const rent = bill({ bill: 'Rent', id: 1 });
    const utilities = bill();
    let state = createFinancialsDraftWorkspaceState(
      createTestFinancialsDraft({ bills: [rent, utilities] })
    );

    state = financialsDraftReducer(state, {
      removal: { id: rent.id, name: rent.bill, type: 'bill' },
      type: 'request-removal',
    });
    expect(state.pendingRemoval).toBeNull();

    state = financialsDraftReducer(state, {
      removal: { id: utilities.id, name: utilities.bill, type: 'bill' },
      type: 'request-removal',
    });
    expect(selectRemovalConfirmation(state)).toEqual({
      itemName: 'Utilities',
      itemType: 'withdrawal',
    });

    state = financialsDraftReducer(state, { type: 'confirm-removal' });
    expect(state.draft?.bills).toEqual([rent]);
    expect(selectFinancialsDraftIsDirty(state)).toBe(true);

    state = financialsDraftReducer(state, { type: 'reset' });
    expect(state.draft?.bills).toEqual([rent, utilities]);
    expect(state.revision).toBe(1);
    expect(state.baselineRevision).toBe(0);
    expect(state.pendingRemoval).toBeNull();
    expect(selectFinancialsDraftIsDirty(state)).toBe(false);
  });

  it('reassigns a projection role by ID before releasing the old record', () => {
    const rent = bill({ bill: 'Housing', id: 1 });
    const utilities = bill();
    let state = createFinancialsDraftWorkspaceState(
      createTestFinancialsDraft({ bills: [rent, utilities] })
    );

    state = financialsDraftReducer(state, {
      recordId: utilities.id,
      role: 'rentBillId',
      type: 'update-projection-role',
    });
    state = financialsDraftReducer(state, {
      removal: { id: rent.id, name: rent.bill, type: 'bill' },
      type: 'request-removal',
    });

    expect(state.draft?.projectionRoles.rentBillId).toBe(utilities.id);
    expect(selectRemovalConfirmation(state)?.itemName).toBe('Housing');
  });

  it('adopts a matching save response as the new committed baseline', () => {
    let state = createFinancialsDraftWorkspaceState(createTestFinancialsDraft({ version: 7 }));
    state = financialsDraftReducer(state, {
      type: 'update-pay-period-start',
      value: '2026-06-02',
    });

    state = financialsDraftReducer(state, {
      savedRevision: 1,
      snapshot: snapshot({ payPeriodStart: '2026-06-02', version: 8 }),
      type: 'synchronize',
    });

    expect(state.baseline?.payPeriodStart).toBe('2026-06-02');
    expect(state.draft?.payPeriodStart).toBe('2026-06-02');
    expect(selectDraftSnapshotVersion(state)).toBe(8);
    expect(state.revision).toBe(1);
    expect(state.baselineRevision).toBe(1);
    expect(selectFinancialsDraftIsDirty(state)).toBe(false);
  });

  it('advances baseline and version without overwriting edits made after save submission', () => {
    let state = createFinancialsDraftWorkspaceState(createTestFinancialsDraft({ version: 7 }));
    state = financialsDraftReducer(state, {
      type: 'update-pay-period-start',
      value: '2026-06-02',
    });
    state = financialsDraftReducer(state, {
      type: 'update-pay-period-end',
      value: '2026-06-20',
    });

    state = financialsDraftReducer(state, {
      savedRevision: 1,
      snapshot: snapshot({
        payPeriodEnd: '2026-06-15',
        payPeriodStart: '2026-06-02',
        version: 8,
      }),
      type: 'synchronize',
    });

    expect(state.baseline).toMatchObject({
      payPeriodEnd: '2026-06-15',
      payPeriodStart: '2026-06-02',
      version: 8,
    });
    expect(state.draft).toMatchObject({
      payPeriodEnd: '2026-06-20',
      payPeriodStart: '2026-06-02',
      version: 8,
    });
    expect(state.revision).toBe(2);
    expect(state.baselineRevision).toBe(1);
    expect(selectFinancialsDraftIsDirty(state)).toBe(true);
    expect(selectFinancialsSaveRequest(state)?.version).toBe(8);
  });

  it('does not reuse a submitted revision after reset while a save is in flight', () => {
    let state = createFinancialsDraftWorkspaceState(createTestFinancialsDraft({ version: 7 }));
    state = financialsDraftReducer(state, {
      type: 'update-pay-period-start',
      value: '2026-06-02',
    });
    const submittedRevision = state.revision;

    state = financialsDraftReducer(state, { type: 'reset' });
    expect(state.revision).toBe(submittedRevision);
    expect(selectFinancialsDraftIsDirty(state)).toBe(false);

    state = financialsDraftReducer(state, {
      type: 'update-pay-period-end',
      value: '2026-06-20',
    });
    expect(state.revision).toBe(submittedRevision + 1);

    state = financialsDraftReducer(state, {
      savedRevision: submittedRevision,
      snapshot: snapshot({ payPeriodStart: '2026-06-02', version: 8 }),
      type: 'synchronize',
    });

    expect(state.draft).toMatchObject({ payPeriodEnd: '2026-06-20', version: 8 });
    expect(state.baseline).toMatchObject({ payPeriodEnd: '2026-06-15', version: 8 });
    expect(selectFinancialsDraftIsDirty(state)).toBe(true);
  });

  it('ignores edit commands until a snapshot establishes the draft', () => {
    const state = financialsDraftReducer(initialFinancialsDraftWorkspaceState, {
      type: 'update-pay-period-start',
      value: '2026-06-02',
    });

    expect(state).toBe(initialFinancialsDraftWorkspaceState);
    expect(selectFinancialsSaveRequest(state)).toBeNull();
  });
});
