import { fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mockDispatch = vi.fn();
const mockFinancialsState = {
  snapshot: {
    payPeriodStart: '2026-06-12',
    payPeriodEnd: '2026-06-26',
    totalMonthlyExpenses: 4890.92,
    paidTotal: 1784.76,
    unpaidTotal: 3106.16,
    payPeriodTotal: 1901.58,
    totalAnnualWithdrawals: 2321,
    annualPayPeriodTotal: 0,
    totalTrackedAssets: 313378.99,
    totalDebt: 2130.03,
    netWorth: 311248.96,
    assetCategories: [
      {
        key: 'retirement',
        label: 'Retirement',
        total: 246133.89,
        accounts: [
          {
            id: 1,
            account: '401k 10%',
            company: 'Vanguard',
            amount: 110653.42,
          },
        ],
      },
      {
        key: 'investments',
        label: 'Investments',
        total: 39658.11,
        accounts: [],
      },
    ],
    debtAccounts: [
      {
        id: 1,
        account: 'Apple',
        company: 'Apple Card',
        amount: 2130.03,
      },
    ],
    incomeSummaryItems: [
      {
        id: 1,
        category: 'Net Income',
        interval: 'Annual',
        amount: 88302.5,
      },
      {
        id: 2,
        category: 'Disposable Income',
        interval: 'Bi-Weekly',
        amount: 1901.58,
      },
    ],
    incomeEvents: [
      {
        id: 1,
        date: '2026-06-12',
        label: 'Paycheck',
        type: 'Paycheck',
        checkNumber: 12,
        checksInMonth: 2,
      },
    ],
    annualWithdrawals: [
      {
        id: 1,
        bill: 'Amazon Prime',
        month: 1,
        day: 11,
        dateLabel: '01/11/2026',
        dueDate: '2026-01-11',
        amount: 140.58,
        account: 'Apple',
        paid: true,
        inPayPeriod: false,
      },
    ],
    importantDates: [
      {
        id: 1,
        date: '2026-12-25',
        event: 'Christmas',
        type: 'Holiday',
      },
    ],
    bills: [
      {
        id: 1,
        bill: 'Rent',
        dueDay: 1,
        dueLabel: '1st',
        dueDate: '2026-06-01',
        amount: 2600,
        account: 'Check',
        paid: true,
        inPayPeriod: false,
      },
    ],
  },
  status: 'succeeded',
  saving: false,
  error: null,
};

vi.mock('./app/hooks', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: vi.fn(() => mockFinancialsState),
}));

import App from './App';

describe('App', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-06-22T12:00:00'));
  });

  afterEach(() => {
    vi.useRealTimers();
    mockDispatch.mockClear();
  });

  it('renders the monthly expenses feature', () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: /financials/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /monthly withdrawals/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /annual withdrawals/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /income summary/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /income calendar/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^debt$/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /important dates/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /save changes/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^reset$/i })).toBeInTheDocument();
    expect(screen.getByText(/^Tracked assets$/i)).toBeInTheDocument();
    expect(screen.getByText(/^Total debt$/i)).toBeInTheDocument();
    expect(screen.getByText(/^Net worth$/i)).toBeInTheDocument();
    expect(screen.getByText(/^Bi-weekly disposable$/i)).toBeInTheDocument();
    expect(screen.getByText(/^Current paycheck$/i)).toBeInTheDocument();
    expect(screen.getByText('#12')).toBeInTheDocument();
    expect(screen.getByText('$140.58')).toBeInTheDocument();
    expect(screen.getByText(/^Next important date$/i)).toBeInTheDocument();
    expect(screen.getByText(/Christmas/i)).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: /retirement/i })).toBeInTheDocument();
  });

  it('keeps save and reset controls available on asset tabs', () => {
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /retirement/i }));

    expect(screen.getByRole('button', { name: /save changes/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^reset$/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /add account/i })).toBeInTheDocument();
  });

  it('renders income calendar and important dates tabs', () => {
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /income calendar/i }));

    expect(screen.getByRole('heading', { name: /income calendar/i })).toBeInTheDocument();
    expect(screen.getAllByRole('cell', { name: /paycheck/i })).toHaveLength(2);
    expect(screen.getByRole('cell', { name: '1' })).toBeInTheDocument();
    expect(screen.getByText(/current/i)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /important dates/i }));

    expect(screen.getByRole('heading', { name: /important dates/i })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: /christmas/i })).toBeInTheDocument();
    expect(screen.getByText(/^Next$/i)).toBeInTheDocument();
  });

  it('renders annual withdrawals tab', () => {
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /annual withdrawals/i }));

    expect(screen.getByRole('heading', { name: /annual withdrawals/i })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: /amazon prime/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/^date$/i)).toHaveAttribute('type', 'date');
    expect(screen.getAllByText('$140.58')).not.toHaveLength(0);
  });

  it('renders income summary tab', () => {
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /income summary/i }));

    expect(screen.getAllByRole('heading', { name: /income summary/i })).not.toHaveLength(0);
    expect(screen.getAllByText(/net income/i)).not.toHaveLength(0);
    expect(screen.getByRole('cell', { name: /bi-weekly/i })).toBeInTheDocument();
    expect(screen.getByText('$1,901.58')).toBeInTheDocument();
  });

  it('renders debt tab', () => {
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /^debt$/i }));

    expect(screen.getByRole('heading', { name: /^debt$/i })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: /^apple$/i })).toBeInTheDocument();
    expect(screen.getAllByText('$2,130.03')).not.toHaveLength(0);
  });

  it('confirms before removing a withdrawal row', () => {
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /monthly withdrawals/i }));
    fireEvent.click(screen.getByRole('button', { name: /remove/i }));

    const dialog = screen.getByRole('dialog', { name: /remove withdrawal/i });

    expect(dialog).toBeInTheDocument();
    expect(within(dialog).getByText(/rent/i)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /cancel/i }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('cell', { name: /rent/i })).toBeInTheDocument();
  });
});
