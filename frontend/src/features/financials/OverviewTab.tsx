import type { ReactNode } from 'react';

import type { AssetCategory } from '../../api/endpoints/financials';
import { currency, formatDate } from './financialsFormatters';
import type { DraftImportantDate, DraftIncomeEvent, ProjectionSummary } from './financialsTypes';

export function Overview({
  annualTotal,
  assetCategories,
  biWeeklyDisposableIncome,
  currentPaycheck,
  netWorth,
  nextImportantDate,
  projection,
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
  projection: ProjectionSummary;
  totalDebt: number;
  totalTrackedAssets: number;
  withdrawalTotal: number;
}) {
  return (
    <>
      <section aria-label="Financial overview" className="overview-sections">
        <OverviewGroup title="Projection">
          <MetricCard
            label="Cash after bills"
            tone={projection.nextPayPeriodCashAfterBills >= 0 ? 'good' : 'bad'}
            value={currency.format(projection.nextPayPeriodCashAfterBills)}
            detail="Next period after bills and rent"
          />
          <MetricCard
            label="Debt left after payment"
            tone={projection.nextPayPeriodDebtRemaining > 0 ? 'bad' : 'good'}
            value={currency.format(projection.nextPayPeriodDebtRemaining)}
            detail="After next period payment"
          />
          <MetricCard
            label="Possible HYSA transfer"
            tone={projection.nextPayPeriodHysaTransfer > 0 ? 'good' : 'neutral'}
            value={currency.format(projection.nextPayPeriodHysaTransfer)}
            detail="Only after debt is covered"
          />
        </OverviewGroup>

        <OverviewGroup title="Balance Sheet">
          <MetricCard
            label="Tracked assets"
            tone="good"
            value={currency.format(totalTrackedAssets)}
          />
          <MetricCard label="Total debt" tone="bad" value={currency.format(totalDebt)} />
          <MetricCard
            label="Net worth"
            tone={netWorth >= 0 ? 'good' : 'bad'}
            value={currency.format(netWorth)}
          />
          {assetCategories.slice(0, 1).map((category) => (
            <MetricCard
              key={category.key}
              label={category.label}
              tone="good"
              value={currency.format(category.total)}
            />
          ))}
        </OverviewGroup>

        <OverviewGroup title="Cash Flow">
          <MetricCard
            label="Monthly withdrawals"
            tone="caution"
            value={currency.format(withdrawalTotal)}
          />
          <MetricCard
            label="Annual withdrawals"
            tone="caution"
            value={currency.format(annualTotal)}
          />
          <MetricCard
            label="Bi-weekly disposable"
            tone={(biWeeklyDisposableIncome ?? 0) >= 0 ? 'good' : 'bad'}
            value={currency.format(biWeeklyDisposableIncome ?? 0)}
          />
          {assetCategories.slice(1, 2).map((category) => (
            <MetricCard
              key={category.key}
              label={category.label}
              tone="good"
              value={currency.format(category.total)}
            />
          ))}
        </OverviewGroup>

        <OverviewGroup title="Calendar">
          <MetricCard
            label="Current paycheck"
            tone="neutral"
            value={currentPaycheck?.checkNumber ? `#${currentPaycheck.checkNumber}` : 'Not started'}
            detail={currentPaycheck ? formatDate(currentPaycheck.date) : undefined}
          />
          <MetricCard
            label="Next important date"
            tone="neutral"
            value={nextImportantDate?.event ?? 'Nothing scheduled'}
            detail={nextImportantDate ? formatDate(nextImportantDate.date) : undefined}
          />
        </OverviewGroup>
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

function OverviewGroup({ children, title }: { children: ReactNode; title: string }) {
  return (
    <section className="overview-group">
      <h2>{title}</h2>
      <div className="overview-grid">{children}</div>
    </section>
  );
}

function MetricCard({
  detail,
  label,
  tone,
  value,
}: {
  detail?: string;
  label: string;
  tone: 'bad' | 'caution' | 'good' | 'neutral';
  value: string;
}) {
  return (
    <div className={`metric-card ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
      {detail && <small>{detail}</small>}
    </div>
  );
}
