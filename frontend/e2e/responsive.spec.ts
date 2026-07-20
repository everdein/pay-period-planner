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
const recordListSections = new Set([
  'monthly-withdrawals',
  'annual-withdrawals',
  'income-summary',
  'income-calendar',
  'retirement',
  'investments',
  'cash-savings',
  'insurance-benefits',
  'debt',
  'important-dates',
]);
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
  await page.getByRole('button', { name: 'Switch to dark theme' }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');

  await page.getByRole('tab', { name: 'Create Account' }).click();
  await expectResponsiveLayout(page, 'narrow phone dark account creation');
  await page.getByLabel('Display name').fill('Long Synthetic Responsive Browser User');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(accountPassword);
  await page.getByLabel('Confirm password').fill(accountPassword);
  await page.getByRole('button', { name: 'Create Account' }).click();

  await expect(page.getByRole('heading', { name: 'Start your planning workspace' })).toBeVisible();
  await expectResponsiveLayout(page, 'narrow phone onboarding');
  await page.getByRole('button', { name: 'Create Financial Snapshot' }).click();
  await expect(page.getByRole('heading', { name: 'Income Summary' }).first()).toBeVisible();

  const setupNavigation = page.getByRole('combobox', { name: 'Financial section' });
  await setupNavigation.selectOption('annual-withdrawals');
  await page.getByLabel('Withdrawal', { exact: true }).fill('Synthetic Annual Membership Renewal');
  await page.getByLabel('Date', { exact: true }).fill('2026-12-31');
  await page.getByLabel('Amount', { exact: true }).fill('149.99');
  await page.getByLabel('Account', { exact: true }).fill('Synthetic Checking Account');
  await page.getByRole('button', { name: 'Add to Draft' }).click();

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
      await expectDesktopShellToSpanViewport(page, viewport.label);
      await expectDesktopRailContrast(page, viewport.label);
    }

    for (const [value, label] of financialSections) {
      if (viewport.width <= 900) {
        await compactNavigation.selectOption(value);
      } else {
        await sidebar.getByRole('button', { exact: true, name: label }).click();
      }

      await expect(page.locator('.mobile-section-picker select')).toHaveValue(value);
      await expectResponsiveLayout(page, `${viewport.label} ${label}`);
      await expectVisibleTablesToFit(page, `${viewport.label} ${label}`);
      if (recordListSections.has(value)) {
        await expectVisibleRecordListsToFit(page, `${viewport.label} ${label}`);
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
    const elementOverflow = Array.from(document.querySelectorAll('body *'))
      .filter(visible)
      .filter((element) => {
        const rect = element.getBoundingClientRect();
        return rect.left < -1 || rect.right > viewportWidth + 1;
      })
      .slice(0, 12)
      .map((element) => {
        const rect = element.getBoundingClientRect();
        return `${element.tagName.toLowerCase()}.${element.className || 'no-class'} "${describe(
          element
        )}" (${Math.round(rect.left)}px to ${Math.round(rect.right)}px)`;
      });
    return {
      controlOverflow,
      elementOverflow,
      horizontalOverflow: Math.max(0, pageWidth - viewportWidth),
      tableRegionOverflow,
      undersizedControls,
    };
  });

  expect(
    result.horizontalOverflow,
    `${state} page overflow: ${result.elementOverflow.join(', ')}`
  ).toBeLessThanOrEqual(1);
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

async function expectDesktopRailContrast(page: Page, state: string) {
  const colors = await page.evaluate(() => {
    const brand = document.querySelector<HTMLElement>('.app-header > div:first-child');
    const content = document.querySelector<HTMLElement>('.financials-content');
    const sidebar = document.querySelector<HTMLElement>('.sidebar');

    return {
      brand: brand ? window.getComputedStyle(brand).backgroundColor : null,
      content: content ? window.getComputedStyle(content).backgroundColor : null,
      sidebar: sidebar ? window.getComputedStyle(sidebar).backgroundColor : null,
    };
  });

  expect(colors.sidebar, `${state} sidebar background`).not.toBeNull();
  expect(colors.brand, `${state} rail continuity`).toBe(colors.sidebar);
  expect(colors.sidebar, `${state} rail/content contrast`).not.toBe(colors.content);
}

async function expectDesktopShellToSpanViewport(page: Page, state: string) {
  const geometry = await page.locator('.workspace-shell').evaluate((shell) => {
    const rect = shell.getBoundingClientRect();
    return {
      left: rect.left,
      rightGap: document.documentElement.clientWidth - rect.right,
    };
  });

  expect(Math.abs(geometry.left), `${state} shell left edge`).toBeLessThanOrEqual(1);
  expect(Math.abs(geometry.rightGap), `${state} shell right edge`).toBeLessThanOrEqual(1);
}

async function expectVisibleRecordListsToFit(page: Page, state: string) {
  const result = await page.evaluate(() => {
    const lists = Array.from(document.querySelectorAll<HTMLElement>('.record-list-section')).filter(
      (element) => {
        const style = window.getComputedStyle(element);
        const rect = element.getBoundingClientRect();
        return style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0;
      }
    );
    const viewportWidth = document.documentElement.clientWidth;
    const overflow = lists
      .filter((element) => element.scrollWidth > element.clientWidth + 1)
      .map((element) => element.textContent?.trim().replace(/\s+/g, ' ').slice(0, 80) ?? 'list');
    const outsideViewport = lists
      .filter((element) => {
        const rect = element.getBoundingClientRect();
        return rect.left < -1 || rect.right > viewportWidth + 1;
      })
      .map((element) => element.textContent?.trim().replace(/\s+/g, ' ').slice(0, 80) ?? 'list');

    return { count: lists.length, outsideViewport, overflow };
  });

  expect(result.count, `${state} record list count`).toBeGreaterThan(0);
  expect(result.outsideViewport, `${state} record lists outside the viewport`).toEqual([]);
  expect(result.overflow, `${state} record lists with horizontal scrolling`).toEqual([]);
}
