import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { expect, test } from '@playwright/test';

import {
  currentWorkspaceId,
  seedSyntheticSnapshot,
  signUp,
} from '../e2e/support/syntheticWorkspace';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const evidenceDirectory = path.join(repoRoot, 'docs', 'images', 'portfolio');

test('captures the synthetic portfolio walkthrough', async ({ page }) => {
  const email = 'portfolio-demo@example.test';

  await mkdir(evidenceDirectory, { recursive: true });
  await page.setViewportSize({ height: 1000, width: 1440 });
  await page.goto('/');
  await signUp(page, 'Portfolio Demo', email);
  const workspaceId = await currentWorkspaceId(page);
  await seedSyntheticSnapshot(page, workspaceId, 350);
  await page.reload();

  await expect(page.getByRole('heading', { name: 'Pay Period Planner' })).toBeVisible();
  await page.getByRole('button', { exact: true, name: 'Overview' }).click();
  await expect(page.getByRole('heading', { name: 'Household Overview' })).toBeVisible();
  await page.screenshot({
    fullPage: true,
    path: path.join(evidenceDirectory, 'overview-desktop.png'),
  });

  await page.getByRole('button', { exact: true, name: 'Projection' }).click();
  await expect(page.getByRole('heading', { name: 'Next Paycheck Projection' })).toBeVisible();
  await page.screenshot({
    fullPage: true,
    path: path.join(evidenceDirectory, 'projection-desktop.png'),
  });

  await page.setViewportSize({ height: 844, width: 390 });
  await page
    .getByRole('combobox', { name: 'Financial section' })
    .selectOption('monthly-withdrawals');
  await expect(page.getByRole('heading', { name: 'Monthly Withdrawals' })).toBeVisible();
  await page.screenshot({
    fullPage: false,
    path: path.join(evidenceDirectory, 'monthly-mobile.png'),
  });
});
