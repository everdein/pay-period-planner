import { randomUUID } from 'node:crypto';

import AxeBuilder from '@axe-core/playwright';
import { expect, type Page, test } from '@playwright/test';

const accountPassword = 'accessibility test password';
const wcagTags = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22a', 'wcag22aa'];
const financialSections = [
  'Overview',
  'Projection',
  'Monthly Withdrawals',
  'Annual Withdrawals',
  'Income Summary',
  'Income Calendar',
  'Retirement',
  'Investments',
  'Cash & Savings',
  'Insurance / Benefits',
  'Debt',
  'Important Dates',
] as const;

test('public account forms meet WCAG A and AA automated rules', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  await expectNoAccessibilityViolations(page, 'sign-in form');

  await page.getByRole('tab', { name: 'Create Account' }).click();
  await expect(page.getByRole('heading', { name: 'Create your account' })).toBeVisible();
  await expectNoAccessibilityViolations(page, 'account creation form');
});

test('onboarding, financial sections, and removal dialog meet WCAG A and AA rules', async ({
  page,
}) => {
  const email = `accessibility-${randomUUID()}@example.test`;

  await page.goto('/');
  await signUp(page, email);
  await expectNoAccessibilityViolations(page, 'workspace onboarding');

  await page.getByRole('button', { name: 'Create Financial Snapshot' }).click();
  await expect(page.getByRole('heading', { name: 'Income Summary' }).first()).toBeVisible();

  for (const section of financialSections) {
    const navigationButton = page.getByRole('button', { exact: true, name: section });
    await navigationButton.click();
    await expect(navigationButton).toHaveAttribute('aria-current', 'page');
    await expectNoAccessibilityViolations(page, `${section} section`);
  }

  await page.getByRole('button', { exact: true, name: 'Annual Withdrawals' }).click();
  const annualWithdrawalForm = page.locator('form.bill-form');
  await annualWithdrawalForm
    .getByRole('textbox', { name: 'Withdrawal' })
    .fill('Accessibility audit renewal');
  await annualWithdrawalForm.getByLabel('Date', { exact: true }).fill('2026-08-15');
  await annualWithdrawalForm.getByLabel('Amount', { exact: true }).fill('25');
  await annualWithdrawalForm.getByRole('textbox', { name: 'Account' }).fill('Synthetic checking');
  await annualWithdrawalForm.getByRole('button', { name: 'Add to Draft' }).click();
  await page.getByRole('button', { name: 'Remove Accessibility audit renewal' }).click();

  const dialog = page.getByRole('dialog', { name: 'Remove annual withdrawal?' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Cancel' })).toBeFocused();
  await expectNoAccessibilityViolations(page, 'removal confirmation dialog');
  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden();
  await expect(
    page.getByRole('button', { name: 'Remove Accessibility audit renewal' })
  ).toBeFocused();
});

async function signUp(page: Page, email: string) {
  await page.getByRole('tab', { name: 'Create Account' }).click();
  await page.getByLabel('Display name').fill('Accessibility Browser User');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(accountPassword);
  await page.getByLabel('Confirm password').fill(accountPassword);
  await page.getByRole('button', { name: 'Create Account' }).click();
  await expect(page.getByRole('heading', { name: 'Start your planning workspace' })).toBeVisible();
}

async function expectNoAccessibilityViolations(page: Page, state: string) {
  const results = await new AxeBuilder({ page }).withTags(wcagTags).analyze();
  const summary = results.violations
    .map(
      ({ help, id, impact, nodes }) =>
        `${id} (${impact ?? 'unknown impact'}): ${help}; targets: ${nodes
          .flatMap((node) => node.target)
          .join(', ')}`
    )
    .join('\n');

  expect(results.violations, `${state} accessibility violations:\n${summary}`).toEqual([]);
}
