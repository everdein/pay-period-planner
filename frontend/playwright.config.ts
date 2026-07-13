import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { defineConfig, devices } from '@playwright/test';

const frontendDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(frontendDir, '..');
const backendDir = path.join(repoRoot, 'backend');
const backendPort = 18080;
const backendTarget = `http://127.0.0.1:${backendPort}`;
const e2eDataPath = path.join(
  repoRoot,
  'test-results',
  'financials-e2e',
  `financials-${process.pid}.local.json`
);
const mavenWrapper = process.platform === 'win32' ? '.\\mvnw.cmd' : './mvnw';

export default defineConfig({
  expect: {
    timeout: 5_000,
  },
  forbidOnly: Boolean(process.env.CI),
  fullyParallel: true,
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
        FINANCIALS_DATA_PATH: e2eDataPath,
        SERVER_PORT: String(backendPort),
        SPRING_PROFILES_ACTIVE: 'json',
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
