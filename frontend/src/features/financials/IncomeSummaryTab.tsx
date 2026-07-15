import type { FormEvent } from 'react';

import { EditButton } from './EditButton';
import { EmptyTableRow } from './EmptyTableRow';
import { currency } from './financialsFormatters';
import type { DraftIncomeSummaryItem, IncomeSummaryFormState } from './financialsTypes';
import { RemoveButton } from './RemoveButton';
import { ScrollableTableRegion } from './ScrollableTableRegion';

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
        <div className="stacked-tables">
          <ScrollableTableRegion label="Saved income sources">
            <table className="income-source-table">
              <colgroup>
                <col className="name-column" />
                <col className="type-column" />
                <col className="amount-column" />
                <col className="actions-column" />
              </colgroup>
              <caption>Saved income source rows.</caption>
              <thead>
                <tr>
                  <th>Category</th>
                  <th>Interval</th>
                  <th>Amount</th>
                  <th aria-label="Actions" />
                </tr>
              </thead>
              <tbody>
                {sourceIncomeSummaryItems.length === 0 && (
                  <EmptyTableRow columns={4} message="No income sources yet." />
                )}
                {sourceIncomeSummaryItems.map((item) => (
                  <tr key={item.id}>
                    <td>{item.category}</td>
                    <td>{item.interval}</td>
                    <td className="amount">{currency.format(item.amount)}</td>
                    <td className="actions">
                      <EditButton
                        label={`Edit ${item.category} ${item.interval}`}
                        onClick={() => startIncomeSummaryItemEdit(item)}
                      />
                      <RemoveButton
                        disabled={item.id === primaryPaycheckIncomeSummaryItemId}
                        label={`Remove ${item.category} ${item.interval}`}
                        onClick={() => requestRemoveIncomeSummaryItem(item)}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </ScrollableTableRegion>

          {derivedCategories.map((category) => (
            <ScrollableTableRegion key={category} label={`${category} income summary`}>
              <table className="income-summary-table">
                <colgroup>
                  <col className="name-column" />
                  <col className="amount-column" />
                </colgroup>
                <caption>{category}</caption>
                <thead>
                  <tr>
                    <th>Interval</th>
                    <th>Amount</th>
                  </tr>
                </thead>
                <tbody>
                  {derivedIncomeSummaryItems
                    .filter((item) => item.category === category)
                    .map((item) => (
                      <tr key={item.id}>
                        <td>{item.interval}</td>
                        <td className="amount">{currency.format(item.amount)}</td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </ScrollableTableRegion>
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
