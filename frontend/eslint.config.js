import js from '@eslint/js';
import globals from 'globals';
import react from 'eslint-plugin-react';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import simpleImportSort from 'eslint-plugin-simple-import-sort';
import tsEslint from '@typescript-eslint/eslint-plugin';
import tsParser from '@typescript-eslint/parser';
import eslintConfigPrettier from 'eslint-config-prettier';
import { defineConfig } from 'eslint/config';

export default defineConfig([
  {
    ignores: ['dist', 'playwright-report', 'test-results'],
  },

  js.configs.recommended,

  {
    files: ['**/*.{ts,tsx}'],

    plugins: {
      react,
      'jsx-a11y': jsxA11y,
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
      '@typescript-eslint': tsEslint,
      'simple-import-sort': simpleImportSort,
    },

    languageOptions: {
      parser: tsParser,
      ecmaVersion: 2020,
      sourceType: 'module',
      globals: globals.browser,

      parserOptions: {
        ecmaFeatures: {
          jsx: true,
        },
      },
    },

    settings: {
      react: {
        version: 'detect',
      },
    },

    rules: {
      // Base recommended rules
      ...tsEslint.configs.recommended.rules,
      ...react.configs.recommended.rules,

      // General code quality
      curly: ['error', 'all'],
      eqeqeq: ['error', 'always'],

      'no-console': [
        'warn',
        {
          allow: ['warn', 'error'],
        },
      ],

      'no-var': 'error',
      'prefer-const': 'error',
      'object-shorthand': 'error',

      // TypeScript
      '@typescript-eslint/no-explicit-any': 'error',

      // Import sorting
      'simple-import-sort/imports': 'error',
      'simple-import-sort/exports': 'error',

      // React
      'react/react-in-jsx-scope': 'off',
      'react/jsx-uses-react': 'off',

      // React hooks
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',

      // Vite / Fast Refresh
      'react-refresh/only-export-components': [
        'warn',
        {
          allowConstantExport: true,
        },
      ],

      // Accessibility
      'jsx-a11y/anchor-is-valid': 'warn',
    },
  },

  {
    files: ['*.config.{js,ts}'],
    languageOptions: {
      globals: globals.node,
    },
  },

  // Must be LAST so Prettier can disable conflicting formatting rules
  eslintConfigPrettier,
]);
