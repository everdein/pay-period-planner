import path from 'node:path';
import { fileURLToPath } from 'node:url';

import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const backendTarget = process.env.VITE_BACKEND_TARGET ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],

  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },

  server: {
    port: 3000,
    strictPort: true,
    open: false,

    proxy: {
      '/api': {
        target: backendTarget,
        changeOrigin: true,
      },
    },
  },

  test: {
    environment: 'jsdom',

    globals: true,

    setupFiles: ['./src/setupTests.ts'],

    include: ['src/**/*.{test,spec}.{js,ts,jsx,tsx}'],

    exclude: ['node_modules', 'dist', 'coverage'],

    coverage: {
      provider: 'v8',

      reporter: ['text', 'html', 'json-summary'],

      reportsDirectory: './coverage',

      thresholds: {
        statements: 45,
        branches: 45,
        functions: 35,
        lines: 46,
      },

      exclude: ['src/main.tsx', 'src/setupTests.ts', 'src/vite-env.d.ts', '**/*.config.{js,ts}'],
    },
  },
});
