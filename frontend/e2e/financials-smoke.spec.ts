import type { Page, Route } from '@playwright/test';
import { expect, test } from '@playwright/test';

type SaveSnapshotRequest = {
  bills: Array<{
    amount: number;
    bill: string;
    id: number | null;
  }>;
};

const financialsSnapshot = {
  annualPayPeriodTotal: 0,
  annualWithdrawals: [
    {
      account: 'Checking',
      amount: 99,
      bill: 'Example Membership',
      dateLabel: '01/15/2026',
      dueDate: '2026-01-15',
      id: 1,
      inPayPeriod: true,
      month: 1,
      paid: false,
      day: 15,
    },
  ],
  assetCategories: [
    {
      accounts: [
        {
          account: 'Example 401k',
          amount: 10_000,
          company: 'Example Provider',
          id: 1,
        },
      ],
      key: 'retirement',
      label: 'Retirement',
      total: 10_000,
    },
    {
      accounts: [],
      key: 'investments',
      label: 'Investments',
      total: 0,
    },
    {
      accounts: [
        {
          account: 'Rent Reserve',
          amount: 5_000,
          company: 'Example Bank',
          id: 2,
        },
      ],
      key: 'cash-savings',
      label: 'Cash & Savings',
      total: 5_000,
    },
    {
      accounts: [],
      key: 'insurance-benefits',
      label: 'Insurance / Benefits',
      total: 0,
    },
  ],
  bills: [
    {
      account: 'Checking',
      amount: 1_200,
      bill: 'Rent',
      dueDate: '2026-01-01',
      dueDay: 1,
      dueLabel: '1st',
      id: 1,
      inPayPeriod: true,
      paid: false,
    },
    {
      account: 'Savings',
      amount: 250,
      bill: 'Example Savings Transfer',
      dueDate: '2026-01-15',
      dueDay: 15,
      dueLabel: '15th',
      id: 2,
      inPayPeriod: true,
      paid: false,
    },
  ],
  debtAccounts: [
    {
      account: 'Example Credit Card',
      amount: 500,
      company: 'Example Bank',
      id: 1,
    },
  ],
  importantDates: [
    {
      date: '2026-12-25',
      event: 'Christmas',
      id: 1,
      type: 'Holiday',
    },
  ],
  incomeEvents: [
    {
      checkNumber: 1,
      checksInMonth: 2,
      date: '2026-01-09',
      id: 1,
      label: 'Paycheck',
      type: 'Paycheck',
    },
  ],
  incomeSummaryItems: [
    {
      amount: 2_884.62,
      category: 'Net Income',
      id: 1,
      interval: 'Bi-Weekly',
    },
  ],
  netWorth: 14_500,
  paidTotal: 0,
  payPeriodEnd: '2026-01-15',
  payPeriodStart: '2026-01-01',
  payPeriodTotal: 1_450,
  totalAnnualWithdrawals: 99,
  totalDebt: 500,
  totalMonthlyExpenses: 1_450,
  totalTrackedAssets: 15_000,
  unpaidTotal: 1_450,
};

async function mockFinancialsApi(page: Page, savedRequests: SaveSnapshotRequest[]) {
  await page.route('**/api/v1/financials', async (route: Route) => {
    const request = route.request();

    if (request.method() === 'GET') {
      await route.fulfill({ json: financialsSnapshot });
      return;
    }

    if (request.method() === 'PUT') {
      savedRequests.push(request.postDataJSON() as SaveSnapshotRequest);
      await route.fulfill({ json: financialsSnapshot });
      return;
    }

    await route.fulfill({ status: 405 });
  });
}

test('loads synthetic financials and saves an edited monthly withdrawal', async ({ page }) => {
  const savedRequests: SaveSnapshotRequest[] = [];
  await mockFinancialsApi(page, savedRequests);

  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'Financials' })).toBeVisible();

  await page.getByRole('button', { name: 'Monthly Withdrawals' }).click();
  await expect(page.getByText('Example Savings Transfer')).toBeVisible();
  await page.getByRole('button', { name: 'Edit Example Savings Transfer' }).click();
  await page.getByLabel('Amount').fill('275.50');
  await page.getByRole('button', { name: 'Update Draft' }).click();

  await expect(page.getByText('You have unsaved changes.')).toBeVisible();

  await page.getByRole('button', { name: 'Save Changes' }).click();

  await expect.poll(() => savedRequests.length).toBe(1);
  expect(savedRequests[0].bills).toContainEqual(
    expect.objectContaining({
      amount: 275.5,
      bill: 'Example Savings Transfer',
      id: 2,
    })
  );
});
