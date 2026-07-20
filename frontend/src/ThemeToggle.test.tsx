import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { initializeTheme, THEME_STORAGE_KEY } from './theme';
import { ThemeToggle } from './ThemeToggle';

describe('ThemeToggle', () => {
  afterEach(() => {
    window.localStorage.clear();
    delete document.documentElement.dataset.theme;
    vi.unstubAllGlobals();
  });

  it('uses the operating-system preference when no choice is saved', () => {
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({ matches: true }));

    initializeTheme();

    expect(document.documentElement).toHaveAttribute('data-theme', 'dark');
  });

  it('uses a saved preference when the theme is initialized', () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, 'dark');

    initializeTheme();

    expect(document.documentElement).toHaveAttribute('data-theme', 'dark');
  });

  it('switches themes and persists the selection', () => {
    document.documentElement.dataset.theme = 'light';
    render(<ThemeToggle />);

    const toggle = screen.getByRole('button', { name: 'Switch to dark theme' });
    fireEvent.click(toggle);

    expect(document.documentElement).toHaveAttribute('data-theme', 'dark');
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
    expect(toggle).toHaveAccessibleName('Switch to light theme');
    expect(toggle).toHaveAttribute('aria-pressed', 'true');
  });
});
