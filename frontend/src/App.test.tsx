import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mockDispatch = vi.fn();
const mockGetMonthlyExpenses = vi.hoisted(() => vi.fn());
const mockRecover = vi.hoisted(() => vi.fn());
const mockSignIn = vi.hoisted(() => vi.fn());
const mockSignUp = vi.hoisted(() => vi.fn());
const mockSignOut = vi.hoisted(() => vi.fn());
const mockSelectWorkspace = vi.hoisted(() => vi.fn());
const account = {
  userId: 7,
  email: 'alex@example.com',
  displayName: 'Alex Morgan',
  expiresAt: '2026-06-29T12:00:00Z',
  workspaces: [{ id: 11, name: 'Personal', role: 'OWNER' }],
};
const activeSession = { account, workspaceId: 11 };
const mockFinancialsState = {
  snapshot: {
    version: 1,
    payPeriodStart: '2026-06-12',
    payPeriodEnd: '2026-06-26',
    currentDate: '2026-06-18',
    planningSettings: { payCadence: 'BIWEEKLY', timeZone: 'America/New_York' },
    projectionRoles: {
      primaryPaycheckIncomeSummaryItemId: 2,
      rentBillId: 1,
      rentReserveAssetAccountId: 2,
    },
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
            account: 'Workplace Retirement',
            company: 'Example Retirement Provider',
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
        account: 'Rewards Card',
        company: 'Example Card Issuer',
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
        bill: 'Annual Membership',
        month: 1,
        day: 11,
        dateLabel: '01/11/2026',
        dueDate: '2026-01-11',
        amount: 140.58,
        account: 'Rewards Card',
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
const mockFinancialsError: {
  current: null | {
    kind: 'conflict' | 'error';
    message: string;
    operation: 'save';
    requestId?: string;
    status?: number;
    title?: string;
  };
} = { current: null };

vi.mock('./app/hooks', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: vi.fn(() => ({ ...mockFinancialsState, error: mockFinancialsError.current })),
}));

vi.mock('./api/auth', () => ({
  accountSessionService: {
    recover: mockRecover,
    selectWorkspace: mockSelectWorkspace,
    signIn: mockSignIn,
    signOut: mockSignOut,
    signUp: mockSignUp,
  },
  clearAccountSession: vi.fn(),
}));

vi.mock('./api/endpoints/financials', () => ({
  financialsService: {
    getMonthlyExpenses: mockGetMonthlyExpenses,
  },
}));

import { ApiError } from './api/client';
import App from './App';

async function renderAuthenticatedApp() {
  await act(async () => {
    render(<App />);
  });
}

describe('App', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-06-22T12:00:00'));
    mockRecover.mockResolvedValue(activeSession);
    mockSignIn.mockResolvedValue(activeSession);
    mockSignUp.mockResolvedValue(activeSession);
    mockSignOut.mockResolvedValue(undefined);
    mockSelectWorkspace.mockImplementation((session, workspaceId) => ({
      ...session,
      workspaceId,
    }));
    mockGetMonthlyExpenses.mockResolvedValue(mockFinancialsState.snapshot);
    mockFinancialsError.current = null;
  });

  afterEach(() => {
    vi.useRealTimers();
    window.sessionStorage.clear();
    mockDispatch.mockClear();
    mockGetMonthlyExpenses.mockReset();
    mockRecover.mockReset();
    mockSignIn.mockReset();
    mockSignUp.mockReset();
    mockSignOut.mockReset();
    mockSelectWorkspace.mockReset();
  });

  it('renders sign in when no account session can be recovered', async () => {
    mockRecover.mockRejectedValue(new ApiError('Unauthorized', 401, 'request-401'));

    await renderAuthenticatedApp();

    expect(screen.getByRole('heading', { name: /welcome back/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
  });

  it('supports keyboard navigation between the account access tabs', async () => {
    mockRecover.mockRejectedValue(new ApiError('Unauthorized', 401, 'request-401'));

    await renderAuthenticatedApp();

    const signInTab = screen.getByRole('tab', { name: 'Sign In' });
    const createAccountTab = screen.getByRole('tab', { name: 'Create Account' });
    expect(signInTab).toHaveAttribute('aria-controls', 'account-panel');
    expect(signInTab).toHaveAttribute('tabindex', '0');
    expect(createAccountTab).toHaveAttribute('tabindex', '-1');
    expect(screen.getByRole('tabpanel', { name: 'Sign In' })).toContainElement(
      screen.getByRole('heading', { name: 'Welcome back' })
    );

    signInTab.focus();
    fireEvent.keyDown(signInTab, { key: 'ArrowRight' });

    expect(createAccountTab).toHaveFocus();
    expect(createAccountTab).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tabpanel', { name: 'Create Account' })).toContainElement(
      screen.getByRole('heading', { name: 'Create your account' })
    );

    fireEvent.keyDown(createAccountTab, { key: 'Home' });
    expect(signInTab).toHaveFocus();
    expect(signInTab).toHaveAttribute('aria-selected', 'true');
  });

  it('signs in through the account session API', async () => {
    vi.useRealTimers();
    mockRecover.mockRejectedValue(new ApiError('Unauthorized', 401, 'request-401'));

    await renderAuthenticatedApp();

    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'alex@example.com' } });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: { value: 'correct horse battery staple' },
    });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() =>
      expect(mockSignIn).toHaveBeenCalledWith({
        email: 'alex@example.com',
        password: 'correct horse battery staple',
      })
    );

    expect(await screen.findByText('alex@example.com')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Pay Period Planner' })).toBeInTheDocument();
  });

  it('creates an account and validates password confirmation', async () => {
    vi.useRealTimers();
    mockRecover.mockRejectedValue(new ApiError('Unauthorized', 401, 'request-401'));

    await renderAuthenticatedApp();
    fireEvent.click(screen.getByRole('tab', { name: /create account/i }));
    fireEvent.change(screen.getByLabelText(/display name/i), { target: { value: 'Alex Morgan' } });
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'alex@example.com' } });
    fireEvent.change(screen.getByLabelText(/^password$/i), {
      target: { value: 'correct horse battery staple' },
    });
    fireEvent.change(screen.getByLabelText(/confirm password/i), {
      target: { value: 'different password' },
    });
    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/passwords do not match/i);
    expect(mockSignUp).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText(/confirm password/i), {
      target: { value: 'correct horse battery staple' },
    });
    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    await waitFor(() =>
      expect(mockSignUp).toHaveBeenCalledWith({
        displayName: 'Alex Morgan',
        email: 'alex@example.com',
        password: 'correct horse battery staple',
      })
    );
  });

  it('keeps the sign-in form visible when the backend proxy is unavailable', async () => {
    vi.useRealTimers();
    mockRecover.mockRejectedValue(new ApiError('Unauthorized', 401, 'request-401'));
    mockSignIn.mockRejectedValue(new Error('HTTP 502 Bad Gateway'));

    await renderAuthenticatedApp();

    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'alex@example.com' } });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: { value: 'correct horse battery staple' },
    });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/unable to sign in/i);
    expect(screen.getByRole('heading', { name: /welcome back/i })).toBeInTheDocument();
  });

  it('revokes the account session before returning to sign in', async () => {
    vi.useRealTimers();
    await renderAuthenticatedApp();

    fireEvent.click(screen.getByRole('button', { name: /sign out/i }));

    await waitFor(() => expect(mockSignOut).toHaveBeenCalledOnce());
    expect(screen.getByRole('heading', { name: /welcome back/i })).toBeInTheDocument();
  });

  it('switches among workspaces without retaining the prior snapshot boundary', async () => {
    const secondWorkspaceSession = {
      account: {
        ...account,
        workspaces: [...account.workspaces, { id: 12, name: 'Household', role: 'MEMBER' }],
      },
      workspaceId: 11,
    };
    mockRecover.mockResolvedValue(secondWorkspaceSession);

    await renderAuthenticatedApp();
    fireEvent.change(screen.getByLabelText(/workspace/i), { target: { value: '12' } });

    expect(mockSelectWorkspace).toHaveBeenCalledWith(secondWorkspaceSession, 12);
    expect(mockDispatch).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'financials/resetFinancials' })
    );
  });

  it('renders the monthly expenses feature', async () => {
    await renderAuthenticatedApp();

    expect(screen.getByRole('heading', { name: 'Pay Period Planner' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /monthly withdrawals/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Projection' })).toBeInTheDocument();
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
    expect(screen.getByText(/^Debt left after plan$/i)).toBeInTheDocument();
    expect(screen.getAllByText('$33.78')).not.toHaveLength(0);
    expect(screen.getByText('$140.58')).toBeInTheDocument();
    expect(screen.getByText(/^Next important date$/i)).toBeInTheDocument();
    expect(screen.getByText(/Christmas/i)).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: /retirement/i })).toBeInTheDocument();
  });

  it('keeps save and reset controls available on asset tabs', async () => {
    await renderAuthenticatedApp();

    fireEvent.click(screen.getByRole('button', { name: /retirement/i }));

    expect(screen.getByRole('button', { name: /save changes/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^reset$/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /add account/i })).toBeInTheDocument();
  });

  it('shows explicit empty collection states', async () => {
    await renderAuthenticatedApp();

    fireEvent.click(screen.getByRole('button', { name: 'Investments' }));

    expect(screen.getByRole('cell', { name: 'No accounts in this category yet.' })).toBeVisible();
  });

  it('keeps the draft visible and offers conflict recovery', async () => {
    mockFinancialsError.current = {
      kind: 'conflict',
      message: 'Snapshot version conflict',
      operation: 'save',
      requestId: 'request-conflict',
      status: 409,
      title: '409 CONFLICT',
    };

    await renderAuthenticatedApp();

    const alert = screen.getByRole('alert');
    expect(within(alert).getByText('A newer snapshot is available')).toBeVisible();
    expect(within(alert).getByText('Request reference: request-conflict')).toBeVisible();
    expect(screen.getByText('Household Overview')).toBeVisible();

    fireEvent.click(within(alert).getByRole('button', { name: 'Discard Draft and Reload' }));

    expect(mockDispatch).toHaveBeenCalledWith({ type: 'financials/clearFinancialsError' });
  });

  it('offers retry and dismissal after a save error', async () => {
    mockFinancialsError.current = {
      kind: 'error',
      message: 'The backend is temporarily unavailable.',
      operation: 'save',
    };

    await renderAuthenticatedApp();

    const alert = screen.getByRole('alert');
    expect(within(alert).getByText('Changes not saved')).toBeVisible();
    expect(within(alert).getByRole('button', { name: 'Try Save Again' })).toBeVisible();

    fireEvent.click(within(alert).getByRole('button', { name: 'Dismiss' }));

    expect(mockDispatch).toHaveBeenCalledWith({ type: 'financials/clearFinancialsError' });
  });

  it('opens financial workflows from the overview dashboard', async () => {
    await renderAuthenticatedApp();

    fireEvent.click(screen.getByRole('button', { name: /open income workflow/i }));
    expect(screen.getByRole('heading', { name: /income summary/i })).toBeInTheDocument();

    fireEvent.change(screen.getByRole('combobox', { name: /financial section/i }), {
      target: { value: 'important-dates' },
    });
    expect(screen.getByRole('heading', { name: /important dates/i })).toBeInTheDocument();
  });

  it('edits an asset account and recalculates its draft total', async () => {
    await renderAuthenticatedApp();

    fireEvent.click(screen.getByRole('button', { name: /retirement/i }));
    fireEvent.click(screen.getByRole('button', { name: /edit workplace retirement/i }));
    fireEvent.change(screen.getByLabelText(/^amount$/i), { target: { value: '110700' } });
    fireEvent.click(screen.getByRole('button', { name: /update draft/i }));

    expect(screen.getAllByText('$110,700.00')).toHaveLength(2);
    expect(screen.getByRole('status')).toHaveTextContent(/unsaved changes/i);
  });

  it('renders income calendar and important dates tabs', async () => {
    await renderAuthenticatedApp();

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

  it('generates recurring payday rows in the income calendar draft', async () => {
    await renderAuthenticatedApp();

    fireEvent.click(screen.getByRole('button', { name: /income calendar/i }));
    fireEvent.change(screen.getByLabelText(/^first payday$/i), {
      target: { value: '2026-01-09' },
    });
    fireEvent.click(screen.getByRole('button', { name: /generate paydays/i }));

    expect(screen.getByText(/unsaved changes/i)).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: '26' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: '12/25/2026' })).toBeInTheDocument();
  });

  it('renders annual withdrawals tab', async () => {
    await renderAuthenticatedApp();

    fireEvent.click(screen.getByRole('button', { name: /annual withdrawals/i }));

    expect(screen.getByRole('heading', { name: /annual withdrawals/i })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: /annual membership/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/^date$/i)).toHaveAttribute('type', 'date');
    expect(screen.getAllByText('$140.58')).not.toHaveLength(0);
  });

  it('renders pay period projection tab', async () => {
    await renderAuthenticatedApp();

    fireEvent.click(screen.getByRole('button', { name: 'Projection' }));

    expect(screen.getByRole('heading', { name: /next paycheck projection/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /next pay period/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /current period context/i })).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: /housing payment/i })).toHaveValue('1');
    expect(screen.getByRole('combobox', { name: /housing reserve/i })).toHaveValue('2');
    expect(screen.getByRole('combobox', { name: /primary paycheck/i })).toHaveValue('2');
    expect(screen.getByRole('combobox', { name: /pay cadence/i })).toHaveValue('BIWEEKLY');
    expect(screen.getByRole('combobox', { name: /planning time zone/i })).toHaveValue(
      'America/New_York'
    );
    expect(screen.getAllByRole('cell', { name: /housing reserve set-aside/i })).not.toHaveLength(0);
    expect(screen.getAllByRole('cell', { name: /housing paid from reserve/i })).not.toHaveLength(0);
    expect(screen.getByText(/^Possible savings transfer$/i)).toBeInTheDocument();
    expect(screen.getAllByText('$0.00')).not.toHaveLength(0);
    expect(screen.getAllByText('$33.78')).not.toHaveLength(0);
  });

  it('renders income summary tab', async () => {
    await renderAuthenticatedApp();

    fireEvent.click(screen.getByRole('button', { name: /income summary/i }));

    expect(screen.getAllByRole('heading', { name: /income summary/i })).not.toHaveLength(0);
    expect(screen.getAllByText(/net income/i)).not.toHaveLength(0);
    expect(screen.getAllByRole('cell', { name: /bi-weekly/i })).not.toHaveLength(0);
    expect(screen.getByDisplayValue('3396.25')).toBeInTheDocument();
    expect(screen.getByText('$4,758.54')).toBeInTheDocument();
  });

  it('renders and edits persisted non-primary income summary source rows', async () => {
    await renderAuthenticatedApp();

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

  it('renders debt tab', async () => {
    await renderAuthenticatedApp();

    fireEvent.click(screen.getByRole('button', { name: /^debt$/i }));

    expect(screen.getByRole('heading', { name: /^debt$/i })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: /^rewards card$/i })).toBeInTheDocument();
    expect(screen.getAllByText('$2,130.03')).not.toHaveLength(0);
  });

  it('edits a debt account and recalculates the draft total', async () => {
    await renderAuthenticatedApp();

    fireEvent.click(screen.getByRole('button', { name: /^debt$/i }));
    fireEvent.click(screen.getByRole('button', { name: /edit rewards card/i }));
    fireEvent.change(screen.getByLabelText(/^balance$/i), { target: { value: '2000' } });
    fireEvent.click(screen.getByRole('button', { name: /update draft/i }));

    expect(
      within(screen.getByRole('region', { name: /debt summary/i })).getByText('$2,000.00')
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole('table', { name: /debt balances/i })).getByRole('cell', {
        name: '$2,000.00',
      })
    ).toBeInTheDocument();
    expect(screen.getByText(/unsaved changes/i)).toBeInTheDocument();
  });

  it('locks configured projection records and confirms other removals', async () => {
    await renderAuthenticatedApp();

    fireEvent.click(screen.getByRole('button', { name: /monthly withdrawals/i }));
    expect(screen.getByRole('button', { name: /remove rent/i })).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: /annual withdrawals/i }));
    const removeButton = screen.getByRole('button', { name: /remove annual membership/i });
    removeButton.focus();
    fireEvent.click(removeButton);

    const dialog = screen.getByRole('dialog', { name: /remove annual withdrawal/i });

    expect(dialog).toBeInTheDocument();
    expect(within(dialog).getByText(/annual membership/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancel/i })).toHaveFocus();

    fireEvent.keyDown(dialog, { key: 'Tab' });
    expect(within(dialog).getByRole('button', { name: /^remove$/i })).toHaveFocus();

    fireEvent.keyDown(dialog, { key: 'Tab', shiftKey: true });
    expect(screen.getByRole('button', { name: /cancel/i })).toHaveFocus();

    fireEvent.keyDown(dialog, { key: 'Escape' });

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(removeButton).toHaveFocus();
    expect(screen.getByRole('cell', { name: /annual membership/i })).toBeInTheDocument();
  });
});
