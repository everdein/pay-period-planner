import type { FormEvent } from 'react';

import { EditButton } from './EditButton';
import { FinancialRecordList, FinancialRecordListItem } from './FinancialRecordList';
import { currency } from './financialsFormatters';
import type { DraftIncomeSummaryItem, IncomeSummaryFormState } from './financialsTypes';
import { RemoveButton } from './RemoveButton';

export function IncomeSummaryTab({
  cancelIncomeSummaryItemEdit,
  derivedIncomeSummaryItems,
  editingIncomeSummaryItemId,
  incomeSummaryForm,
  primaryPaycheckIncomeSummaryItemId,
  requestRemoveIncomeSummaryItem,
  sourceIncomeSummaryItems,
  startIncomeSummaryItemEdit,
  submitIncomeSummaryItem,
  updateIncomeSummaryForm,
}: {
  cancelIncomeSummaryItemEdit: () => void;
  derivedIncomeSummaryItems: DraftIncomeSummaryItem[];
  editingIncomeSummaryItemId: number | null;
  incomeSummaryForm: IncomeSummaryFormState;
  primaryPaycheckIncomeSummaryItemId: number;
  requestRemoveIncomeSummaryItem: (item: DraftIncomeSummaryItem) => void;
  sourceIncomeSummaryItems: DraftIncomeSummaryItem[];
  startIncomeSummaryItemEdit: (item: DraftIncomeSummaryItem) => void;
  submitIncomeSummaryItem: (event: FormEvent<HTMLFormElement>) => void;
  updateIncomeSummaryForm: <K extends keyof IncomeSummaryFormState>(
    key: K,
    value: IncomeSummaryFormState[K]
  ) => void;
}) {
  const derivedCategories = Array.from(
    new Set(derivedIncomeSummaryItems.map((item) => item.category))
  );
  const derivedCategoryItems = derivedCategories.map((category) => ({
    category,
    items: derivedIncomeSummaryItems.filter((item) => item.category === category),
  }));
  return (
    <>
      <section className="section-header">
        <div>
          <h2>Income Summary</h2>
          <p>
            Edit saved income source rows and review the calculated net/disposable income summary.
          </p>
        </div>
      </section>

      <section className="expenses-layout">
        <div className="record-list-stack">
          <FinancialRecordList
            description="Saved income source rows used for planning."
            emptyDescription="Add an income source to begin the household income plan."
            emptyTitle="No income sources yet."
            headingId="saved-income-sources-heading"
            itemCount={sourceIncomeSummaryItems.length}
            summaryLabel="Saved income source summary"
            title="Saved income sources"
          >
            {sourceIncomeSummaryItems.map((item) => (
              <FinancialRecordListItem
                actions={
                  <>
                    <EditButton
                      label={`Edit ${item.category} ${item.interval}`}
                      onClick={() => startIncomeSummaryItemEdit(item)}
                    />
                    <RemoveButton
                      disabled={item.id === primaryPaycheckIncomeSummaryItemId}
                      label={`Remove ${item.category} ${item.interval}`}
                      onClick={() => requestRemoveIncomeSummaryItem(item)}
                    />
                  </>
                }
                key={item.id}
                metadata={[item.interval]}
                primary={item.category}
                value={<strong>{currency.format(item.amount)}</strong>}
              />
            ))}
          </FinancialRecordList>

          {derivedCategoryItems.map(({ category, items }) => (
            <FinancialRecordList
              description="Calculated income amounts by planning interval."
              emptyDescription="Calculated values appear when this category is available."
              emptyTitle={`No ${category.toLowerCase()} values yet.`}
              headingId={`income-summary-${category.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`}
              itemCount={items.length}
              key={category}
              summaryLabel={`${category} income summary`}
              title={category}
            >
              {items.map((item) => (
                <FinancialRecordListItem
                  key={item.id}
                  primary={item.interval}
                  value={<strong>{currency.format(item.amount)}</strong>}
                />
              ))}
            </FinancialRecordList>
          ))}
        </div>

        <form className="bill-form" onSubmit={submitIncomeSummaryItem}>
          <h2>
            {editingIncomeSummaryItemId === null ? 'Add Income Source' : 'Edit Income Source'}
          </h2>
          <label>
            Category
            <input
              onChange={(event) => updateIncomeSummaryForm('category', event.target.value)}
              required
              value={incomeSummaryForm.category}
            />
          </label>
          <label>
            Interval
            <input
              onChange={(event) => updateIncomeSummaryForm('interval', event.target.value)}
              required
              value={incomeSummaryForm.interval}
            />
          </label>
          <label>
            Amount
            <input
              min={0}
              onChange={(event) => updateIncomeSummaryForm('amount', event.target.value)}
              required
              step="0.01"
              type="number"
              value={incomeSummaryForm.amount}
            />
          </label>
          <p className="helper-text">
            Disposable income annualizes the primary paycheck using the planning cadence, then
            subtracts monthly withdrawals. Additional source rows are saved for planning, but they
            do not yet change projection math.
          </p>
          <div className="form-actions">
            <button type="submit">
              {editingIncomeSummaryItemId === null ? 'Add to Draft' : 'Update Draft'}
            </button>
            {editingIncomeSummaryItemId !== null && (
              <button className="ghost" onClick={cancelIncomeSummaryItemEdit} type="button">
                Cancel
              </button>
            )}
          </div>
        </form>
      </section>
    </>
  );
}
