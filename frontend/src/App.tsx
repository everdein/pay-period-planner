import { type FormEvent, useState } from 'react';

import { clearApiCredentials, hasApiCredentials, saveApiCredentials } from './api/auth';
import FinancialsPage from './features/financials/FinancialsPage';

export default function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(hasApiCredentials);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  function signIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    saveApiCredentials({ password, username });
    setIsAuthenticated(true);
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
          <form className="auth-form" onSubmit={signIn}>
            <label>
              Username
              <input
                autoComplete="username"
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
                onChange={(event) => setPassword(event.target.value)}
                required
                type="password"
                value={password}
              />
            </label>
            <button type="submit">Sign In</button>
          </form>
        </section>
      </main>
    );
  }

  return <FinancialsPage onSignOut={signOut} />;
}
