import type { AssetCategory } from '../../api/endpoints/financials';
import { currency, formatDate } from './financialsFormatters';
import type { DraftImportantDate, DraftIncomeEvent } from './financialsTypes';

export function Overview({
  annualTotal,
  assetCategories,
  biWeeklyDisposableIncome,
  currentPaycheck,
  netWorth,
  nextImportantDate,
  totalDebt,
  totalTrackedAssets,
  withdrawalTotal,
}: {
  annualTotal: number;
  assetCategories: AssetCategory[];
  biWeeklyDisposableIncome?: number;
  currentPaycheck?: DraftIncomeEvent;
  netWorth: number;
  nextImportantDate?: DraftImportantDate;
  totalDebt: number;
  totalTrackedAssets: number;
  withdrawalTotal: number;
}) {
  return (
    <>
      <section aria-label="Financial overview" className="summary-grid">
        <div>
          <span>Tracked assets</span>
          <strong>{currency.format(totalTrackedAssets)}</strong>
        </div>
        <div>
          <span>Total debt</span>
          <strong>{currency.format(totalDebt)}</strong>
        </div>
        <div>
          <span>Net worth</span>
          <strong>{currency.format(netWorth)}</strong>
        </div>
        <div>
          <span>Monthly withdrawals</span>
          <strong>{currency.format(withdrawalTotal)}</strong>
        </div>
        <div>
          <span>Bi-weekly disposable</span>
          <strong>{currency.format(biWeeklyDisposableIncome ?? 0)}</strong>
        </div>
        <div>
          <span>Annual withdrawals</span>
          <strong>{currency.format(annualTotal)}</strong>
        </div>
        <div>
          <span>Current paycheck</span>
          <strong>
            {currentPaycheck?.checkNumber ? `#${currentPaycheck.checkNumber}` : 'Not started'}
          </strong>
          {currentPaycheck && <small>{formatDate(currentPaycheck.date)}</small>}
        </div>
        <div>
          <span>Next important date</span>
          <strong>{nextImportantDate?.event ?? 'Nothing scheduled'}</strong>
          {nextImportantDate && <small>{formatDate(nextImportantDate.date)}</small>}
        </div>
        {assetCategories.slice(0, 2).map((category) => (
          <div key={category.key}>
            <span>{category.label}</span>
            <strong>{currency.format(category.total)}</strong>
          </div>
        ))}
      </section>
      <section className="table-wrap">
        <table>
          <caption>Asset category totals</caption>
          <thead>
            <tr>
              <th>Category</th>
              <th>Accounts</th>
              <th>Total</th>
            </tr>
          </thead>
          <tbody>
            {assetCategories.map((category) => (
              <tr key={category.key}>
                <td>{category.label}</td>
                <td>{category.accounts.length}</td>
                <td className="amount">{currency.format(category.total)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <td colSpan={2}>Total tracked assets</td>
              <td className="amount">{currency.format(totalTrackedAssets)}</td>
            </tr>
          </tfoot>
        </table>
      </section>
    </>
  );
}
