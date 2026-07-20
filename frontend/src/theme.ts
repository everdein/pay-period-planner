export const THEME_STORAGE_KEY = 'pay-period-planner-theme';
export const THEME_CHANGE_EVENT = 'pay-period-planner-theme-change';

export type Theme = 'light' | 'dark';

export function getTheme(): Theme {
  const appliedTheme = document.documentElement.dataset.theme;
  if (appliedTheme === 'light' || appliedTheme === 'dark') {
    return appliedTheme;
  }

  return getStoredTheme() ?? getPreferredTheme();
}

export function initializeTheme() {
  applyTheme(getTheme(), false);
}

export function setTheme(theme: Theme) {
  applyTheme(theme, true);
}

function applyTheme(theme: Theme, persist: boolean) {
  document.documentElement.dataset.theme = theme;
  document
    .querySelector('meta[name="theme-color"]')
    ?.setAttribute('content', theme === 'dark' ? '#101311' : '#ffffff');

  if (persist) {
    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, theme);
    } catch {
      // The selected theme still applies when storage is unavailable.
    }
  }

  window.dispatchEvent(new Event(THEME_CHANGE_EVENT));
}

function getStoredTheme(): Theme | null {
  try {
    const storedTheme = window.localStorage.getItem(THEME_STORAGE_KEY);
    return storedTheme === 'light' || storedTheme === 'dark' ? storedTheme : null;
  } catch {
    return null;
  }
}

function getPreferredTheme(): Theme {
  return typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-color-scheme: dark)').matches
    ? 'dark'
    : 'light';
}
