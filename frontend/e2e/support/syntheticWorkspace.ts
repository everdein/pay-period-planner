import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { expect, type Page } from '@playwright/test';

import type { ExpenseSnapshotRequest, PayPeriodRequest } from '../../src/api/endpoints/financials';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
const exampleSnapshotPath = path.join(repoRoot, 'backend', 'data', 'financials.example.json');
const accountPassword = 'synthetic browser password';

export async function signUp(page: Page, displayName: string, email: string) {
  await page.getByRole('tab', { name: 'Create Account' }).click();
  await page.getByLabel('Display name').fill(displayName);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(accountPassword);
  await page.getByLabel('Confirm password').fill(accountPassword);
  await page.getByRole('button', { name: 'Create Account' }).click();
  await expect(page.getByRole('heading', { name: 'Start your planning workspace' })).toBeVisible();
}

export async function signIn(page: Page, email: string) {
  await page.getByRole('tab', { name: 'Sign In' }).click();
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(accountPassword);
  await page.getByRole('button', { name: 'Sign In', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Pay Period Planner' })).toBeVisible();
}

export async function currentWorkspaceId(page: Page) {
  return await page.evaluate(async () => {
    const response = await fetch('/api/v1/auth/session');
    if (!response.ok) {
      throw new Error(`Unable to recover browser session: ${response.status}`);
    }

    const session = (await response.json()) as { workspaces: Array<{ id: number }> };
    const workspaceId = session.workspaces[0]?.id;
    if (!workspaceId) {
      throw new Error('The browser account does not have a workspace.');
    }
    return workspaceId;
  });
}

export async function seedSyntheticSnapshot(
  page: Page,
  workspaceId: number,
  savingsTransferAmount: number
) {
  const source = await syntheticSnapshot(savingsTransferAmount);
  const payPeriod: PayPeriodRequest = {
    endDate: source.payPeriodEnd,
    planningSettings: source.planningSettings,
    startDate: source.payPeriodStart,
  };
  const initialized = await sendWorkspaceMutation<{ version: number }>(
    page,
    workspaceId,
    '/api/v1/financials',
    'POST',
    payPeriod,
    201
  );

  source.version = initialized.version;
  await sendWorkspaceMutation(page, workspaceId, '/api/v1/financials', 'PUT', source, 200);
}

async function syntheticSnapshot(savingsTransferAmount: number) {
  const snapshot = JSON.parse(await readFile(exampleSnapshotPath, 'utf8')) as {
    annualWithdrawals: ExpenseSnapshotRequest['annualWithdrawals'];
    assetAccounts: Array<{
      account: string;
      amount: number;
      categoryKey: string;
      categoryLabel: string;
      company: string;
      id: number;
    }>;
    bills: ExpenseSnapshotRequest['bills'];
    debtAccounts: ExpenseSnapshotRequest['debtAccounts'];
    importantDates: ExpenseSnapshotRequest['importantDates'];
    incomeEvents: ExpenseSnapshotRequest['incomeEvents'];
    incomeSummaryItems: ExpenseSnapshotRequest['incomeSummaryItems'];
    payPeriodEnd: string;
    payPeriodStart: string;
  };
  const transfer = snapshot.bills.find(({ bill }) => bill === 'Example Savings Transfer');
  if (!transfer) {
    throw new Error('Synthetic example snapshot is missing the savings transfer row.');
  }

  transfer.amount = savingsTransferAmount;
  const assetCategories = new Map<string, ExpenseSnapshotRequest['assetCategories'][number]>();
  for (const asset of snapshot.assetAccounts) {
    const category = assetCategories.get(asset.categoryKey) ?? {
      accounts: [],
      key: asset.categoryKey,
      label: asset.categoryLabel,
    };
    category.accounts.push({
      account: asset.account,
      amount: asset.amount,
      company: asset.company,
      id: asset.id,
    });
    assetCategories.set(asset.categoryKey, category);
  }

  return {
    annualWithdrawals: snapshot.annualWithdrawals,
    assetCategories: [...assetCategories.values()],
    bills: snapshot.bills,
    debtAccounts: snapshot.debtAccounts,
    importantDates: snapshot.importantDates,
    incomeEvents: snapshot.incomeEvents,
    incomeSummaryItems: snapshot.incomeSummaryItems,
    payPeriodEnd: snapshot.payPeriodEnd,
    payPeriodStart: snapshot.payPeriodStart,
    planningSettings: {
      payCadence: 'BIWEEKLY' as const,
      timeZone: 'America/New_York',
    },
    projectionRoles: {
      primaryPaycheckIncomeSummaryItemId: 1,
      rentBillId: 1,
      rentReserveAssetAccountId: 2,
    },
    version: 1,
  } satisfies ExpenseSnapshotRequest;
}

async function sendWorkspaceMutation<T>(
  page: Page,
  workspaceId: number,
  requestPath: string,
  method: 'POST' | 'PUT',
  payload: unknown,
  expectedStatus: number
) {
  const result = await page.evaluate(
    async ({ body, path, requestMethod, selectedWorkspaceId }) => {
      const csrfResponse = await fetch('/api/v1/auth/csrf');
      if (!csrfResponse.ok) {
        return { body: await csrfResponse.text(), status: csrfResponse.status };
      }

      const csrf = (await csrfResponse.json()) as { headerName: string; token: string };
      const response = await fetch(path, {
        body: JSON.stringify(body),
        credentials: 'same-origin',
        headers: {
          'Content-Type': 'application/json',
          'X-Workspace-ID': String(selectedWorkspaceId),
          [csrf.headerName]: csrf.token,
        },
        method: requestMethod,
      });
      return { body: await response.text(), status: response.status };
    },
    {
      body: payload,
      path: requestPath,
      requestMethod: method,
      selectedWorkspaceId: workspaceId,
    }
  );

  expect(result.status, result.body).toBe(expectedStatus);
  return JSON.parse(result.body) as T;
}
