import { randomUUID } from 'node:crypto';

import { expect, type Page, test } from '@playwright/test';

const accountPassword = 'responsive test password';
const financialSections = [
  ['overview', 'Overview'],
  ['projection', 'Projection'],
  ['monthly-withdrawals', 'Monthly Withdrawals'],
  ['annual-withdrawals', 'Annual Withdrawals'],
  ['income-summary', 'Income Summary'],
  ['income-calendar', 'Income Calendar'],
  ['retirement', 'Retirement'],
  ['investments', 'Investments'],
  ['cash-savings', 'Cash & Savings'],
  ['insurance-benefits', 'Insurance / Benefits'],
  ['debt', 'Debt'],
  ['important-dates', 'Important Dates'],
] as const;
const viewports = [
  { height: 800, label: 'narrow phone', width: 320 },
  { height: 844, label: 'phone', width: 390 },
  { height: 1024, label: 'tablet', width: 768 },
  { height: 768, label: 'desktop', width: 1024 },
] as const;

test('keeps every financial workflow contained and operable across supported widths', async ({
  page,
}) => {
  const email = `responsive-${randomUUID()}@very-long-synthetic-domain.example.test`;

  await page.setViewportSize(viewports[0]);
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  await expectResponsiveLayout(page, 'narrow phone sign-in');

  await page.getByRole('tab', { name: 'Create Account' }).click();
  await expectResponsiveLayout(page, 'narrow phone account creation');
  await page.getByLabel('Display name').fill('Long Synthetic Responsive Browser User');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(accountPassword);
  await page.getByLabel('Confirm password').fill(accountPassword);
  await page.getByRole('button', { name: 'Create Account' }).click();

  await expect(page.getByRole('heading', { name: 'Start your planning workspace' })).toBeVisible();
  await expectResponsiveLayout(page, 'narrow phone onboarding');
  await page.getByRole('button', { name: 'Create Financial Snapshot' }).click();
  await expect(page.getByRole('heading', { name: 'Income Summary' }).first()).toBeVisible();

  for (const viewport of viewports) {
    await page.setViewportSize(viewport);
    const compactNavigation = page.getByRole('combobox', { name: 'Financial section' });
    const sidebar = page.getByRole('complementary', { name: 'Financial sections' });

    if (viewport.width <= 900) {
      await expect(compactNavigation).toBeVisible();
      await expect(sidebar).toBeHidden();
    } else {
      await expect(compactNavigation).toBeHidden();
      await expect(sidebar).toBeVisible();
    }

    for (const [value, label] of financialSections) {
      if (viewport.width <= 900) {
        await compactNavigation.selectOption(value);
      } else {
        await sidebar.getByRole('button', { exact: true, name: label }).click();
      }

      await expect(page.locator('.mobile-section-picker select')).toHaveValue(value);
      await expectResponsiveLayout(page, `${viewport.label} ${label}`);
      if (
        viewport.width >= 768 &&
        (value === 'monthly-withdrawals' || value === 'annual-withdrawals')
      ) {
        await expectVisibleTablesToFit(page, `${viewport.label} ${label}`);
      }
    }
  }
});

async function expectResponsiveLayout(page: Page, state: string) {
  const result = await page.evaluate(() => {
    const visible = (element: Element) => {
      const style = window.getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      return (
        style.display !== 'none' &&
        style.visibility !== 'hidden' &&
        rect.width > 0 &&
        rect.height > 0
      );
    };
    const describe = (element: Element) => {
      const htmlElement = element as HTMLElement;
      return (
        htmlElement.getAttribute('aria-label') ??
        htmlElement.textContent?.trim().replace(/\s+/g, ' ').slice(0, 80) ??
        htmlElement.tagName.toLowerCase()
      );
    };
    const viewportWidth = document.documentElement.clientWidth;
    const pageWidth = Math.max(document.documentElement.scrollWidth, document.body.scrollWidth);
    const controls = Array.from(
      document.querySelectorAll('button, input:not([type="hidden"]), select')
    ).filter(visible);
    const controlOverflow = controls
      .filter((element) => {
        const rect = element.getBoundingClientRect();
        return rect.left < -1 || rect.right > viewportWidth + 1;
      })
      .map(describe);
    const undersizedControls = controls
      .filter((element) => !(element instanceof HTMLInputElement && element.type === 'checkbox'))
      .filter((element) => {
        const rect = element.getBoundingClientRect();
        return rect.width < 24 || rect.height < 24;
      })
      .map(describe);
    const tableRegionOverflow = Array.from(document.querySelectorAll('.table-wrap'))
      .filter(visible)
      .filter((element) => {
        const rect = element.getBoundingClientRect();
        return rect.left < -1 || rect.right > viewportWidth + 1;
      })
      .map(describe);
    return {
      controlOverflow,
      horizontalOverflow: Math.max(0, pageWidth - viewportWidth),
      tableRegionOverflow,
      undersizedControls,
    };
  });

  expect(result.horizontalOverflow, `${state} page overflow`).toBeLessThanOrEqual(1);
  expect(result.controlOverflow, `${state} controls outside the viewport`).toEqual([]);
  expect(result.tableRegionOverflow, `${state} table regions outside the viewport`).toEqual([]);
  expect(result.undersizedControls, `${state} controls smaller than 24px`).toEqual([]);
}

async function expectVisibleTablesToFit(page: Page, state: string) {
  const overflow = await page.evaluate(() =>
    Array.from(document.querySelectorAll<HTMLElement>('.table-wrap'))
      .filter((element) => {
        const style = window.getComputedStyle(element);
        const rect = element.getBoundingClientRect();
        return style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0;
      })
      .filter((element) => element.scrollWidth > element.clientWidth + 1)
      .map((element) => {
        const label =
          element.getAttribute('aria-label') ?? element.textContent?.trim().slice(0, 80) ?? 'table';
        return `${label} (${element.scrollWidth}px content / ${element.clientWidth}px region)`;
      })
  );

  expect(overflow, `${state} tables with unnecessary horizontal scrolling`).toEqual([]);
}
