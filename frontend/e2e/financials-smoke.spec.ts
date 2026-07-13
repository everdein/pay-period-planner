import { expect, test } from '@playwright/test';

test('loads, edits, saves, refreshes, and confirms deletion with the live backend', async ({
  page,
}) => {
  await page.goto('/');

  await page.getByLabel('Username').fill('financial_app');
  await page.getByLabel('Password').fill('financial_app_local_password');
  await page.getByRole('button', { name: 'Sign In' }).click();

  await expect(page.getByRole('heading', { name: 'Financials' })).toBeVisible();
  await expect(
    page.getByRole('button', { name: /export saved financial snapshot backup/i })
  ).toBeVisible();

  await page.getByRole('button', { name: 'Monthly Withdrawals' }).click();
  const monthlyWithdrawalsTable = page.locator('table.withdrawals-table').first();
  const transferRow = monthlyWithdrawalsTable.getByRole('row', {
    name: /Example Savings Transfer/,
  });
  await expect(
    transferRow.getByRole('cell', { exact: true, name: 'Example Savings Transfer' })
  ).toBeVisible();
  await expect(transferRow.getByRole('cell', { name: '$250.00' })).toBeVisible();

  await page.getByRole('button', { name: 'Edit Example Savings Transfer' }).click();
  await page.getByLabel('Amount').fill('275.50');
  await page.getByRole('button', { name: 'Update Draft' }).click();

  await expect(page.getByText('You have unsaved changes.')).toBeVisible();
  await expect(transferRow.getByRole('cell', { name: '$275.50' })).toBeVisible();

  await page.getByRole('button', { name: 'Save Changes' }).click();

  await expect(page.getByText('You have unsaved changes.')).toBeHidden();
  await expect(transferRow.getByRole('cell', { name: '$275.50' })).toBeVisible();

  await page.reload();
  await page.getByRole('button', { name: 'Monthly Withdrawals' }).click();
  await expect(
    transferRow.getByRole('cell', { exact: true, name: 'Example Savings Transfer' })
  ).toBeVisible();
  await expect(transferRow.getByRole('cell', { name: '$275.50' })).toBeVisible();

  const removeButton = transferRow.getByRole('button', {
    name: 'Remove Example Savings Transfer',
  });
  await expect(removeButton).toBeEnabled();
  await removeButton.click();

  const dialog = page.getByRole('dialog', { name: /remove withdrawal/i });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText('Example Savings Transfer')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cancel' })).toBeFocused();

  await dialog.getByRole('button', { name: 'Remove' }).click();
  await expect(dialog).toBeHidden();
  await expect(transferRow).toBeHidden();
  await expect(page.getByText('You have unsaved changes.')).toBeVisible();

  await page.getByRole('button', { name: 'Save Changes' }).click();
  await expect(page.getByText('You have unsaved changes.')).toBeHidden();

  await page.reload();
  await page.getByRole('button', { name: 'Monthly Withdrawals' }).click();
  await expect(transferRow).toBeHidden();
  await expect(
    monthlyWithdrawalsTable.getByRole('cell', { exact: true, name: 'Rent' })
  ).toBeVisible();
});
