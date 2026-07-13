import { fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mockDispatch = vi.fn();
const AUTH_TOKEN_STORAGE_KEY = 'end-to-end-app.auth.basicToken';
const mockFinancialsState = {
  snapshot: {
    version: 1,
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
      {
        key: 'cash-savings',
        label: 'Cash & Savings',
        total: 1300,
        accounts: [
          {
            id: 2,
            account: 'Member Savings (Rent)',
            company: 'Example Credit Union',
            amount: 1300,
          },
        ],
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
        category: 'Net Income',
        interval: 'Bi-Weekly',
        amount: 3396.25,
      },
      {
        id: 3,
        category: 'Disposable Income',
        interval: 'Bi-Weekly',
        amount: 1901.58,
      },
      {
        id: 4,
        category: 'Side Income',
        interval: 'Month',
        amount: 125,
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
    window.sessionStorage.setItem(AUTH_TOKEN_STORAGE_KEY, 'Basic dGVzdDp0ZXN0');
  });

  afterEach(() => {
    vi.useRealTimers();
    window.sessionStorage.clear();
    mockDispatch.mockClear();
  });

  it('renders sign in before credentials are stored', () => {
    window.sessionStorage.clear();

    render(<App />);

    expect(screen.getByRole('heading', { name: /sign in to financials/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
  });

  it('renders the monthly expenses feature', () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: /financials/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /monthly withdrawals/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /projection/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /annual withdrawals/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /income summary/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /income calendar/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^debt$/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /important dates/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /save changes/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^reset$/i })).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /export saved financial snapshot backup/i })
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign out/i })).toBeInTheDocument();
    expect(screen.getByText(/^Tracked assets$/i)).toBeInTheDocument();
    expect(screen.getByText(/^Total debt$/i)).toBeInTheDocument();
    expect(screen.getByText(/^Net worth$/i)).toBeInTheDocument();
    expect(screen.getByText(/^Primary paycheck$/i)).toBeInTheDocument();
    expect(screen.getByText(/^Current paycheck$/i)).toBeInTheDocument();
    expect(screen.getByText('#12')).toBeInTheDocument();
    expect(screen.getByText(/^Cash after bills$/i)).toBeInTheDocument();
    expect(screen.getAllByText('$2,096.25')).not.toHaveLength(0);
    expect(screen.getByText(/^Debt left after payment$/i)).toBeInTheDocument();
    expect(screen.getAllByText('$33.78')).not.toHaveLength(0);
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

  it('generates recurring payday rows in the income calendar draft', () => {
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /income calendar/i }));
    fireEvent.change(screen.getByLabelText(/^first payday$/i), {
      target: { value: '2026-01-09' },
    });
    fireEvent.click(screen.getByRole('button', { name: /generate paydays/i }));

    expect(screen.getByText(/unsaved changes/i)).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: '26' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: '12/25/2026' })).toBeInTheDocument();
  });

  it('renders annual withdrawals tab', () => {
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /annual withdrawals/i }));

    expect(screen.getByRole('heading', { name: /annual withdrawals/i })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: /amazon prime/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/^date$/i)).toHaveAttribute('type', 'date');
    expect(screen.getAllByText('$140.58')).not.toHaveLength(0);
  });

  it('renders pay period projection tab', () => {
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /projection/i }));

    expect(screen.getByRole('heading', { name: /next paycheck projection/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /next pay period/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /current period context/i })).toBeInTheDocument();
    expect(screen.getAllByRole('cell', { name: /rent set aside/i })).not.toHaveLength(0);
    expect(screen.getAllByRole('cell', { name: /rent paid from savings/i })).not.toHaveLength(0);
    expect(screen.getByText(/^Possible HYSA transfer$/i)).toBeInTheDocument();
    expect(screen.getAllByText('$0.00')).not.toHaveLength(0);
    expect(screen.getAllByText('$33.78')).not.toHaveLength(0);
  });

  it('renders income summary tab', () => {
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /income summary/i }));

    expect(screen.getAllByRole('heading', { name: /income summary/i })).not.toHaveLength(0);
    expect(screen.getAllByText(/net income/i)).not.toHaveLength(0);
    expect(screen.getAllByRole('cell', { name: /bi-weekly/i })).not.toHaveLength(0);
    expect(screen.getByDisplayValue('3396.25')).toBeInTheDocument();
    expect(screen.getByText('$4,192.50')).toBeInTheDocument();
  });

  it('renders and edits persisted non-primary income summary source rows', () => {
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /income summary/i }));

    const sourceTable = screen.getByRole('table', { name: /saved income source rows/i });

    expect(within(sourceTable).getByRole('cell', { name: /side income/i })).toBeInTheDocument();
    expect(within(sourceTable).getByRole('cell', { name: /^month$/i })).toBeInTheDocument();
    expect(within(sourceTable).getByRole('cell', { name: '$125.00' })).toBeInTheDocument();

    fireEvent.click(within(sourceTable).getByRole('button', { name: /edit side income month/i }));
    fireEvent.change(screen.getByLabelText(/^amount$/i), { target: { value: '200' } });
    fireEvent.click(screen.getByRole('button', { name: /update draft/i }));

    expect(within(sourceTable).getByRole('cell', { name: '$200.00' })).toBeInTheDocument();
    expect(screen.getByText(/unsaved changes/i)).toBeInTheDocument();
  });

  it('renders debt tab', () => {
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /^debt$/i }));

    expect(screen.getByRole('heading', { name: /^debt$/i })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: /^apple$/i })).toBeInTheDocument();
    expect(screen.getAllByText('$2,130.03')).not.toHaveLength(0);
  });

  it('locks projection anchors and confirms before removing non-anchor withdrawals', () => {
    render(<App />);

    fireEvent.click(screen.getByRole('button', { name: /monthly withdrawals/i }));
    expect(screen.getByRole('button', { name: /remove rent/i })).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: /annual withdrawals/i }));
    const removeButton = screen.getByRole('button', { name: /remove amazon prime/i });
    removeButton.focus();
    fireEvent.click(removeButton);

    const dialog = screen.getByRole('dialog', { name: /remove annual withdrawal/i });

    expect(dialog).toBeInTheDocument();
    expect(within(dialog).getByText(/amazon prime/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancel/i })).toHaveFocus();

    fireEvent.keyDown(dialog, { key: 'Tab' });
    expect(within(dialog).getByRole('button', { name: /^remove$/i })).toHaveFocus();

    fireEvent.keyDown(dialog, { key: 'Tab', shiftKey: true });
    expect(screen.getByRole('button', { name: /cancel/i })).toHaveFocus();

    fireEvent.keyDown(dialog, { key: 'Escape' });

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(removeButton).toHaveFocus();
    expect(screen.getByRole('cell', { name: /amazon prime/i })).toBeInTheDocument();
  });
});
