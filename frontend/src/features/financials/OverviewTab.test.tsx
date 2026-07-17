import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import type { AssetCategory } from '../../api/endpoints/financials';
import type { ProjectionSummary } from './financialsTypes';
import { Overview } from './OverviewTab';

const emptyProjection: ProjectionSummary = {
  currentDebt: 0,
  debtCoveredByProjectedCash: 0,
  debtCoveragePercent: 100,
  nextPayPeriodCashAfterBills: 0,
  nextPayPeriodDebtPayment: 0,
  nextPayPeriodDebtRemaining: 0,
  nextPayPeriodSavingsTransfer: 0,
  projectedAfterDebt: 0,
  projectedBeforeDebt: 0,
  remainingDebtAfterProjectedCash: 0,
  periods: [],
};

const emptyAssetCategories: AssetCategory[] = [
  { accounts: [], key: 'retirement', label: 'Retirement', total: 0 },
  { accounts: [], key: 'investments', label: 'Investments', total: 0 },
];

describe('Overview metric tones', () => {
  it('keeps untouched zero-value metrics neutral', () => {
    renderOverview();

    [
      'Cash after bills',
      'Debt left after plan',
      'Possible savings transfer',
      'Tracked assets',
      'Total debt',
      'Net worth',
      'Retirement',
      'Monthly withdrawals',
      'Annual withdrawals',
      'Primary paycheck',
      'Investments',
    ].forEach((label) => expect(metricCard(label)).toHaveClass('neutral'));
  });

  it('uses semantic tones once values are meaningful', () => {
    renderOverview({
      annualTotal: 120,
      assetCategories: [
        { accounts: [], key: 'retirement', label: 'Retirement', total: 600 },
        { accounts: [], key: 'investments', label: 'Investments', total: 400 },
      ],
      netWorth: 500,
      primaryPaycheckIncome: 2000,
      projection: {
        ...emptyProjection,
        currentDebt: 500,
        nextPayPeriodCashAfterBills: 800,
        nextPayPeriodDebtRemaining: 200,
        nextPayPeriodSavingsTransfer: 300,
      },
      totalDebt: 500,
      totalTrackedAssets: 1000,
      withdrawalTotal: 300,
    });

    ['Cash after bills', 'Possible savings transfer', 'Tracked assets', 'Net worth'].forEach(
      (label) => expect(metricCard(label)).toHaveClass('good')
    );
    expect(metricCard('Total debt')).toHaveClass('bad');
    expect(metricCard('Debt left after plan')).toHaveClass('bad');
    expect(metricCard('Monthly withdrawals')).toHaveClass('caution');
    expect(metricCard('Annual withdrawals')).toHaveClass('caution');
  });

  it('marks a completed payoff as good only when debt was tracked', () => {
    renderOverview({
      projection: {
        ...emptyProjection,
        currentDebt: 500,
        nextPayPeriodDebtRemaining: 0,
      },
    });

    expect(metricCard('Debt left after plan')).toHaveClass('good');
  });

  it('marks negative cash and net worth as bad', () => {
    renderOverview({
      netWorth: -100,
      projection: { ...emptyProjection, nextPayPeriodCashAfterBills: -25 },
    });

    expect(metricCard('Cash after bills')).toHaveClass('bad');
    expect(metricCard('Net worth')).toHaveClass('bad');
  });
});

function renderOverview(overrides: Partial<Parameters<typeof Overview>[0]> = {}) {
  const props: Parameters<typeof Overview>[0] = {
    annualTotal: 0,
    assetCategories: emptyAssetCategories,
    netWorth: 0,
    onNavigate: vi.fn(),
    projection: emptyProjection,
    totalDebt: 0,
    totalTrackedAssets: 0,
    withdrawalTotal: 0,
    ...overrides,
  };

  return render(<Overview {...props} />);
}

function metricCard(label: string) {
  const card = screen.getByText(label, { selector: '.metric-card span' }).closest('.metric-card');
  expect(card).not.toBeNull();
  return card as HTMLElement;
}
