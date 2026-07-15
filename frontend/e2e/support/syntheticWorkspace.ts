import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { expect, type Page } from '@playwright/test';

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

export async function migrateSyntheticSnapshot(
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
