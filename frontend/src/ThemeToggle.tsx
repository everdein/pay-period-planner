import { useSyncExternalStore } from 'react';

import { getTheme, setTheme, THEME_CHANGE_EVENT } from './theme';

function subscribeToTheme(onStoreChange: () => void) {
  window.addEventListener(THEME_CHANGE_EVENT, onStoreChange);
  return () => window.removeEventListener(THEME_CHANGE_EVENT, onStoreChange);
}

export function ThemeToggle() {
  const theme = useSyncExternalStore(subscribeToTheme, getTheme, () => 'light');
  const nextTheme = theme === 'dark' ? 'light' : 'dark';

  return (
    <button
      aria-label={`Switch to ${nextTheme} theme`}
      aria-pressed={theme === 'dark'}
      className="theme-control"
      onClick={() => setTheme(nextTheme)}
      title={`Switch to ${nextTheme} theme`}
      type="button"
    >
      <span aria-hidden="true" className="theme-icon theme-icon-sun">
        {'\u2600'}
      </span>
      <span aria-hidden="true" className="theme-icon theme-icon-moon">
        {'\u263e'}
      </span>
    </button>
  );
}
