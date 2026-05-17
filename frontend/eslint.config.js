import js from '@eslint/js';
import globals from 'globals';
import react from 'eslint-plugin-react';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import simpleImportSort from 'eslint-plugin-simple-import-sort';
import tsEslint from '@typescript-eslint/eslint-plugin';
import tsParser from '@typescript-eslint/parser';
import { defineConfig } from 'eslint/config';

export default defineConfig([
  {
    ignores: ['dist'],
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
      ...tsEslint.configs.recommended.rules,
      ...react.configs.recommended.rules,

      '@typescript-eslint/no-explicit-any': 'error',

      'simple-import-sort/imports': 'error',
      'simple-import-sort/exports': 'error',

      'react/react-in-jsx-scope': 'off',
      'react/jsx-uses-react': 'off',

      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',

      'react-refresh/only-export-components': [
        'warn',
        {
          allowConstantExport: true,
        },
      ],

      'jsx-a11y/anchor-is-valid': 'warn',
    },
  },
]);
