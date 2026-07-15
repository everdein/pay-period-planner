import type { ReactNode } from 'react';

import type { AssetCategory } from '../../api/endpoints/financials';
import { currency, formatDate } from './financialsFormatters';
import type {
  DraftImportantDate,
  DraftIncomeEvent,
  FinancialTab,
  ProjectionSummary,
} from './financialsTypes';

const dashboardWorkflows: Array<{
  detail: string;
  label: string;
  tab: FinancialTab;
}> = [
  { detail: 'Next paycheck', label: 'Projection', tab: 'projection' },
  { detail: 'Paychecks and sources', label: 'Income', tab: 'income-summary' },
  { detail: 'Recurring bills', label: 'Monthly', tab: 'monthly-withdrawals' },
  { detail: 'Yearly charges', label: 'Annual', tab: 'annual-withdrawals' },
  { detail: 'Cash and accounts', label: 'Assets', tab: 'cash-savings' },
  { detail: 'Balances and payoff', label: 'Debt', tab: 'debt' },
  { detail: 'Paydays and deposits', label: 'Calendar', tab: 'income-calendar' },
  { detail: 'Holidays and reminders', label: 'Dates', tab: 'important-dates' },
];

export function Overview({
  annualTotal,
  assetCategories,
  currentPaycheck,
  netWorth,
  nextImportantDate,
  onNavigate,
  primaryPaycheckIncome,
  projection,
  totalDebt,
  totalTrackedAssets,
  withdrawalTotal,
}: {
  annualTotal: number;
  assetCategories: AssetCategory[];
  currentPaycheck?: DraftIncomeEvent;
  netWorth: number;
  nextImportantDate?: DraftImportantDate;
  onNavigate: (tab: FinancialTab) => void;
  primaryPaycheckIncome?: number;
  projection: ProjectionSummary;
  totalDebt: number;
  totalTrackedAssets: number;
  withdrawalTotal: number;
}) {
  return (
    <>
      <section className="overview-header">
        <div>
          <p className="eyebrow">Dashboard</p>
          <h2>Financial Overview</h2>
        </div>
        <nav aria-label="Financial workflows" className="dashboard-workflows">
          {dashboardWorkflows.map((workflow) => (
            <button
              aria-label={`Open ${workflow.label.toLowerCase()} workflow`}
              className="dashboard-workflow"
              key={workflow.tab}
              onClick={() => onNavigate(workflow.tab)}
              type="button"
            >
              <strong>{workflow.label}</strong>
              <span>{workflow.detail}</span>
            </button>
          ))}
        </nav>
      </section>
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
            label="Primary paycheck"
            tone={(primaryPaycheckIncome ?? 0) >= 0 ? 'good' : 'bad'}
            value={currency.format(primaryPaycheckIncome ?? 0)}
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
        <table className="overview-table">
          <colgroup>
            <col className="name-column" />
            <col className="count-column" />
            <col className="amount-column" />
          </colgroup>
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
                <td className="count-cell">{category.accounts.length}</td>
                <td className="amount">{currency.format(category.total)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p className="table-total">
          Total tracked assets: <strong>{currency.format(totalTrackedAssets)}</strong>
        </p>
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
