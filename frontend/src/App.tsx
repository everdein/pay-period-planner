import { type FormEvent, useState } from 'react';

import { clearApiCredentials, hasApiCredentials, saveApiCredentials } from './api/auth';
import { financialsService } from './api/endpoints/financials';
import FinancialsPage from './features/financials/FinancialsPage';

export default function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(hasApiCredentials);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isSigningIn, setIsSigningIn] = useState(false);
  const [signInError, setSignInError] = useState<string | null>(null);

  async function signIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSigningIn(true);
    setSignInError(null);
    saveApiCredentials({ password, username });

    try {
      await financialsService.getMonthlyExpenses();
      setIsAuthenticated(true);
    } catch (error) {
      clearApiCredentials();
      setIsAuthenticated(false);
      setSignInError(signInFailureMessage(error));
    } finally {
      setIsSigningIn(false);
    }
  }

  function signOut() {
    clearApiCredentials();
    setIsAuthenticated(false);
    setPassword('');
  }

  if (!isAuthenticated) {
    return (
      <main className="expenses-shell auth-shell">
        <section className="auth-card" aria-labelledby="sign-in-heading">
          <p className="eyebrow">Personal finance</p>
          <h1 id="sign-in-heading">Sign in to Financials</h1>
          <p>
            Use the local financial API credentials configured for this backend session. The
            defaults are documented in the README for local-only development.
          </p>
          <p className="auth-hint">
            Default local app username: <code>financial_app</code>. The PostgreSQL user{' '}
            <code>financial_app_user</code> is not an app login.
          </p>
          <form className="auth-form" onSubmit={signIn}>
            <label>
              Username
              <input
                autoComplete="username"
                disabled={isSigningIn}
                onChange={(event) => setUsername(event.target.value)}
                required
                type="text"
                value={username}
              />
            </label>
            <label>
              Password
              <input
                autoComplete="current-password"
                disabled={isSigningIn}
                onChange={(event) => setPassword(event.target.value)}
                required
                type="password"
                value={password}
              />
            </label>
            {signInError && (
              <p className="auth-error" role="alert">
                {signInError}
              </p>
            )}
            <button disabled={isSigningIn} type="submit">
              {isSigningIn ? 'Signing in...' : 'Sign In'}
            </button>
          </form>
        </section>
      </main>
    );
  }

  return <FinancialsPage onSignOut={signOut} />;
}

function signInFailureMessage(error: unknown) {
  const message = error instanceof Error ? error.message : '';

  if (message.includes('HTTP 401')) {
    return 'The app credentials were not accepted. Use the local API username and password, not the PostgreSQL database user.';
  }

  if (message.includes('HTTP 502')) {
    return 'The frontend could not reach the backend. Start or restart the backend, then try signing in again.';
  }

  if (message) {
    return `Unable to sign in: ${message}`;
  }

  return 'Unable to sign in. Confirm the backend is running, then try again.';
}
