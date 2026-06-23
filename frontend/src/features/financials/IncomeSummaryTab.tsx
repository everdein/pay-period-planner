import type { FormEvent } from 'react';

import { currency } from './financialsFormatters';
import type { DraftIncomeSummaryItem, IncomeSummaryFormState } from './financialsTypes';

export function IncomeSummaryTab({
  cancelIncomeSummaryEdit,
  editingIncomeSummaryId,
  incomeSummaryForm,
  incomeSummaryItems,
  requestRemoveIncomeSummaryItem,
  startIncomeSummaryEdit,
  submitIncomeSummaryItem,
  updateIncomeSummaryForm,
}: {
  cancelIncomeSummaryEdit: () => void;
  editingIncomeSummaryId: number | null;
  incomeSummaryForm: IncomeSummaryFormState;
  incomeSummaryItems: DraftIncomeSummaryItem[];
  requestRemoveIncomeSummaryItem: (item: DraftIncomeSummaryItem) => void;
  startIncomeSummaryEdit: (item: DraftIncomeSummaryItem) => void;
  submitIncomeSummaryItem: (event: FormEvent<HTMLFormElement>) => void;
  updateIncomeSummaryForm: <K extends keyof IncomeSummaryFormState>(
    key: K,
    value: IncomeSummaryFormState[K]
  ) => void;
}) {
  const isEditing = editingIncomeSummaryId !== null;
  const categories = Array.from(new Set(incomeSummaryItems.map((item) => item.category)));

  return (
    <>
      <section className="section-header">
        <div>
          <h2>Income Summary</h2>
          <p>Net and disposable income assumptions by planning interval.</p>
        </div>
      </section>

      <section className="expenses-layout">
        <div className="stacked-tables">
          {categories.map((category) => (
            <div className="table-wrap" key={category}>
              <table>
                <caption>{category}</caption>
                <thead>
                  <tr>
                    <th>Interval</th>
                    <th>Amount</th>
                    <th aria-label="Actions" />
                  </tr>
                </thead>
                <tbody>
                  {incomeSummaryItems
                    .filter((item) => item.category === category)
                    .map((item) => (
                      <tr key={item.id}>
                        <td>{item.interval}</td>
                        <td className="amount">{currency.format(item.amount)}</td>
                        <td className="actions">
                          <button onClick={() => startIncomeSummaryEdit(item)} type="button">
                            Edit
                          </button>
                          <button
                            className="ghost"
                            onClick={() => requestRemoveIncomeSummaryItem(item)}
                            type="button"
                          >
                            Remove
                          </button>
                        </td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          ))}
        </div>

        <form className="bill-form" onSubmit={submitIncomeSummaryItem}>
          <h2>{isEditing ? 'Edit Income Summary' : 'Add Income Summary'}</h2>
          <label>
            Category
            <select
              onChange={(event) => updateIncomeSummaryForm('category', event.target.value)}
              value={incomeSummaryForm.category}
            >
              <option>Net Income</option>
              <option>Disposable Income</option>
            </select>
          </label>
          <label>
            Interval
            <select
              onChange={(event) => updateIncomeSummaryForm('interval', event.target.value)}
              value={incomeSummaryForm.interval}
            >
              <option>Annual</option>
              <option>Month</option>
              <option>Bi-Weekly</option>
              <option>Weekly</option>
            </select>
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
          <div className="form-actions">
            <button type="submit">{isEditing ? 'Update Draft' : 'Add to Draft'}</button>
            {isEditing && (
              <button className="ghost" onClick={cancelIncomeSummaryEdit} type="button">
                Cancel
              </button>
            )}
          </div>
        </form>
      </section>
    </>
  );
}
