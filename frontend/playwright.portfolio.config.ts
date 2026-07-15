import { defineConfig } from '@playwright/test';

import browserConfig from './playwright.config';

export default defineConfig({
  ...browserConfig,
  outputDir: 'test-results/portfolio',
  reporter: [['list']],
  testDir: './portfolio',
  timeout: 60_000,
  use: {
    ...browserConfig.use,
    screenshot: 'off',
    trace: 'off',
  },
});
