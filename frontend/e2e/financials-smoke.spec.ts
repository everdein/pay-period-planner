import { createHash, randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { expect, type Page, test } from '@playwright/test';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const exampleSnapshotPath = path.join(repoRoot, 'backend', 'data', 'financials.example.json');
const accountPassword = 'browser test password';

test('creates and saves a first financial snapshot from an empty workspace', async ({ page }) => {
  const email = `onboarding-${randomUUID()}@example.test`;

  await page.goto('/');
  await signUp(page, 'New Browser User', email);
  await page.getByLabel('Pay period starts').fill('2026-07-10');
  await page.getByLabel('Pay period ends').fill('2026-07-23');
  await page.getByRole('button', { name: 'Create Financial Snapshot' }).click();

  await expect(page.getByText('Workspace ready')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Income Summary' }).first()).toBeVisible();
  await page.getByLabel('Amount').fill('3200');
  await page.getByRole('button', { name: 'Add to Draft' }).click();
  await page.getByRole('button', { name: 'Save Changes' }).click();

  await expect(page.getByRole('status')).toContainText('Changes saved. Snapshot version 2.');
  await page.reload();
  await page.getByRole('button', { name: 'Income Summary' }).click();
  await expect(page.getByRole('cell', { name: '$3,200.00' }).first()).toBeVisible();

  await page.getByRole('button', { name: 'Overview' }).click();
  await expect(page.getByRole('heading', { name: 'Financial Overview' })).toBeVisible();
  await page.getByRole('button', { name: 'Open monthly workflow' }).click();
  await expect(page.getByRole('heading', { name: 'Monthly Withdrawals' })).toBeVisible();

  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('combobox', { name: 'Financial section' }).selectOption('debt');
  await expect(page.getByRole('heading', { exact: true, name: 'Debt' })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'No debt accounts yet.' })).toBeVisible();
});

test('keeps browser sessions isolated while editing the live PostgreSQL workspace', async ({
  page,
}) => {
  const suffix = randomUUID();
  const firstEmail = `first-${suffix}@example.test`;
  const secondEmail = `second-${suffix}@example.test`;

  await page.goto('/');
  await signUp(page, 'First Browser User', firstEmail);
  const firstWorkspaceId = await currentWorkspaceId(page);
  await migrateSyntheticSnapshot(page, firstEmail, firstWorkspaceId, 250);
  await page.reload();

  await expect(page.getByRole('heading', { name: 'Financials' })).toBeVisible();
  await expect(
    page.getByRole('button', { name: /export saved financial snapshot backup/i })
  ).toBeVisible();

  await page.getByRole('button', { name: 'Monthly Withdrawals' }).click();
  const monthlyWithdrawalsTable = page.locator('table.withdrawals-table').first();
  const transferRow = monthlyWithdrawalsTable.getByRole('row', {
    name: /Example Savings Transfer/,
  });
  await expect(transferRow.getByRole('cell', { name: '$250.00' })).toBeVisible();

  await page.getByRole('button', { name: 'Edit Example Savings Transfer' }).click();
  await page.getByLabel('Amount').fill('275.50');
  await page.getByRole('button', { name: 'Update Draft' }).click();
  await page.getByRole('button', { name: 'Save Changes' }).click();
  await expect(page.getByText('You have unsaved changes.')).toBeHidden();

  await page.getByRole('button', { name: 'Sign Out' }).click();
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();

  await signUp(page, 'Second Browser User', secondEmail);
  const secondWorkspaceId = await currentWorkspaceId(page);
  await migrateSyntheticSnapshot(page, secondEmail, secondWorkspaceId, 875);
  await page.reload();
  await page.getByRole('button', { name: 'Monthly Withdrawals' }).click();

  const secondUserTransferRow = page
    .locator('table.withdrawals-table')
    .first()
    .getByRole('row', { name: /Example Savings Transfer/ });
  await expect(secondUserTransferRow.getByRole('cell', { name: '$875.00' })).toBeVisible();
  await expect(secondUserTransferRow.getByRole('cell', { name: '$275.50' })).toHaveCount(0);

  await page.getByRole('button', { name: 'Sign Out' }).click();
  await signIn(page, firstEmail);
  await page.getByRole('button', { name: 'Monthly Withdrawals' }).click();
  await expect(transferRow.getByRole('cell', { name: '$275.50' })).toBeVisible();
  await expect(transferRow.getByRole('cell', { name: '$875.00' })).toHaveCount(0);

  const removeButton = transferRow.getByRole('button', {
    name: 'Remove Example Savings Transfer',
  });
  await removeButton.click();

  const dialog = page.getByRole('dialog', { name: /remove withdrawal/i });
  await expect(dialog).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cancel' })).toBeFocused();
  await dialog.getByRole('button', { name: 'Remove' }).click();
  await page.getByRole('button', { name: 'Save Changes' }).click();
  await expect(page.getByText('You have unsaved changes.')).toBeHidden();

  await page.reload();
  await page.getByRole('button', { name: 'Monthly Withdrawals' }).click();
  await expect(transferRow).toBeHidden();
  await expect(
    monthlyWithdrawalsTable.getByRole('cell', { exact: true, name: 'Rent' })
  ).toBeVisible();
});

test('keeps a stale draft visible and reloads after a concurrent save conflict', async ({
  context,
  page,
}) => {
  const email = `conflict-${randomUUID()}@example.test`;

  await page.goto('/');
  await signUp(page, 'Conflict Browser User', email);
  await page.getByRole('button', { name: 'Create Financial Snapshot' }).click();
  await expect(page.getByRole('heading', { name: 'Income Summary' }).first()).toBeVisible();

  const otherPage = await context.newPage();
  await otherPage.goto('/');
  await otherPage.getByRole('button', { name: 'Monthly Withdrawals' }).click();
  await otherPage.getByRole('button', { name: 'Edit Rent' }).click();
  await otherPage.getByLabel('Amount').fill('2700');
  await otherPage.getByRole('button', { name: 'Update Draft' }).click();
  await otherPage.getByRole('button', { name: 'Save Changes' }).click();
  await expect(otherPage.getByRole('status')).toContainText('Snapshot version 2');

  await page.getByLabel('Amount').fill('1000');
  await page.getByRole('button', { name: 'Add to Draft' }).click();
  await expect(page.getByRole('cell', { name: '$1,000.00' }).first()).toBeVisible();
  await page.getByRole('button', { name: 'Save Changes' }).click();

  const conflict = page.getByRole('alert');
  await expect(conflict).toContainText('A newer snapshot is available');
  await expect(page.getByRole('cell', { name: '$1,000.00' }).first()).toBeVisible();
  await conflict.getByRole('button', { name: 'Discard Draft and Reload' }).click();

  await expect(conflict).toBeHidden();
  await expect(page.getByRole('cell', { name: '$1,000.00' }).first()).toBeHidden();
  await page.getByRole('button', { name: 'Monthly Withdrawals' }).click();
  await expect(page.getByRole('cell', { name: '$2,700.00' })).toBeVisible();
  await otherPage.close();
});

async function signUp(page: Page, displayName: string, email: string) {
  await page.getByRole('tab', { name: 'Create Account' }).click();
  await page.getByLabel('Display name').fill(displayName);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(accountPassword);
  await page.getByLabel('Confirm password').fill(accountPassword);
  await page.getByRole('button', { name: 'Create Account' }).click();
  await expect(page.getByRole('heading', { name: 'Start your financial workspace' })).toBeVisible();
}

async function signIn(page: Page, email: string) {
  await page.getByRole('tab', { name: 'Sign In' }).click();
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(accountPassword);
  await page.getByRole('button', { name: 'Sign In', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Financials' })).toBeVisible();
}

async function currentWorkspaceId(page: Page) {
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

async function migrateSyntheticSnapshot(
  page: Page,
  email: string,
  workspaceId: number,
  savingsTransferAmount: number
) {
  const source = await syntheticSnapshot(savingsTransferAmount);
  const fingerprint = createHash('sha256').update(source).digest('hex');
  const query = new URLSearchParams({
    destinationEmail: email,
    expectedFingerprint: fingerprint,
    workspaceId: String(workspaceId),
  });
  const result = await page.evaluate(
    async ({ body, path: requestPath }) => {
      const response = await fetch(requestPath, {
        body,
        credentials: 'omit',
        headers: {
          Authorization: `Basic ${btoa('financial_app:financial_app_local_password')}`,
          'Content-Type': 'application/json',
          'X-Confirm-Financial-Migration': 'APPLY',
        },
        method: 'POST',
      });
      return { body: await response.text(), status: response.status };
    },
    {
      body: source,
      path: `/api/v1/admin/workspace-migrations/apply/json-file?${query.toString()}`,
    }
  );

  expect(result.status, result.body).toBe(200);
}

async function syntheticSnapshot(savingsTransferAmount: number) {
  const snapshot = JSON.parse(await readFile(exampleSnapshotPath, 'utf8')) as {
    bills: Array<{ amount: number; bill: string }>;
  };
  const transfer = snapshot.bills.find(({ bill }) => bill === 'Example Savings Transfer');
  if (!transfer) {
    throw new Error('Synthetic example snapshot is missing the savings transfer row.');
  }

  transfer.amount = savingsTransferAmount;
  return JSON.stringify(snapshot);
}
