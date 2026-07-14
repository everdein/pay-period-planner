import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { defineConfig, devices } from '@playwright/test';

const frontendDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(frontendDir, '..');
const backendDir = path.join(repoRoot, 'backend');
const backendPort = 18080;
const backendTarget = `http://127.0.0.1:${backendPort}`;
const browserTestSchema = process.env.BROWSER_TEST_SCHEMA ?? `financials_browser_${process.pid}`;
const browserDatabaseUrl =
  process.env.BROWSER_TEST_DATABASE_URL ??
  `jdbc:postgresql://localhost:5432/financial_app?currentSchema=${browserTestSchema}`;
const mavenWrapper = process.platform === 'win32' ? '.\\mvnw.cmd' : './mvnw';

export default defineConfig({
  expect: {
    timeout: 5_000,
  },
  forbidOnly: Boolean(process.env.CI),
  fullyParallel: false,
  outputDir: 'test-results',
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  reporter: process.env.CI
    ? [['github'], ['html', { open: 'never' }]]
    : [['list'], ['html', { open: 'never' }]],
  retries: process.env.CI ? 1 : 0,
  testDir: './e2e',
  timeout: 30_000,
  use: {
    baseURL: 'http://127.0.0.1:3000',
    screenshot: 'only-on-failure',
    trace: 'on-first-retry',
  },
  webServer: [
    {
      command: `${mavenWrapper} -B spring-boot:run`,
      cwd: backendDir,
      env: {
        DATABASE_PASSWORD: process.env.DATABASE_PASSWORD ?? 'financial_app_password',
        DATABASE_URL: browserDatabaseUrl,
        DATABASE_USERNAME: process.env.DATABASE_USERNAME ?? 'financial_app_user',
        SERVER_PORT: String(backendPort),
        SPRING_FLYWAY_DEFAULT_SCHEMA: browserTestSchema,
        SPRING_FLYWAY_SCHEMAS: browserTestSchema,
      },
      reuseExistingServer: false,
      stderr: 'pipe',
      stdout: 'pipe',
      timeout: 120_000,
      url: `${backendTarget}/actuator/health`,
    },
    {
      command: 'npm run dev -- --host 127.0.0.1',
      cwd: frontendDir,
      env: {
        VITE_BACKEND_TARGET: backendTarget,
      },
      reuseExistingServer: false,
      stderr: 'pipe',
      stdout: 'pipe',
      timeout: 120_000,
      url: 'http://127.0.0.1:3000',
    },
  ],
});
