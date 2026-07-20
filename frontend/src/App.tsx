import { type FormEvent, type KeyboardEvent, useEffect, useRef, useState } from 'react';

import { accountSessionService, type ActiveAccountSession, clearAccountSession } from './api/auth';
import { ApiError, setUnauthorizedHandler } from './api/client';
import { useAppDispatch } from './app/hooks';
import FinancialsPage from './features/financials/FinancialsPage';
import { resetFinancials } from './features/financials/financialsSlice';
import { ThemeToggle } from './ThemeToggle';

type AuthMode = 'sign-in' | 'sign-up';

export default function App() {
  const dispatch = useAppDispatch();
  const [activeSession, setActiveSession] = useState<ActiveAccountSession | null>(null);
  const [recoveringSession, setRecoveringSession] = useState(true);
  const [authMode, setAuthMode] = useState<AuthMode>('sign-in');
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const signInTabRef = useRef<HTMLButtonElement>(null);
  const signUpTabRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearAccountSession();
      dispatch(resetFinancials());
      setActiveSession(null);
      setPassword('');
      setConfirmPassword('');
      setAuthError('Your session expired. Sign in again to continue.');
    });

    return () => setUnauthorizedHandler(null);
  }, [dispatch]);

  useEffect(() => {
    let current = true;

    accountSessionService
      .recover()
      .then((session) => {
        if (current) {
          dispatch(resetFinancials());
          setActiveSession(session);
        }
      })
      .catch((error: unknown) => {
        if (!current) {
          return;
        }

        if (error instanceof ApiError && error.status === 401) {
          clearAccountSession();
        } else {
          setAuthError(sessionRecoveryFailureMessage(error));
        }
      })
      .finally(() => {
        if (current) {
          setRecoveringSession(false);
        }
      });

    return () => {
      current = false;
    };
  }, [dispatch]);

  async function submitAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (authMode === 'sign-up' && password !== confirmPassword) {
      setAuthError('Passwords do not match.');
      return;
    }

    setSubmitting(true);
    setAuthError(null);

    try {
      const session =
        authMode === 'sign-up'
          ? await accountSessionService.signUp({ displayName, email, password })
          : await accountSessionService.signIn({ email, password });
      dispatch(resetFinancials());
      setActiveSession(session);
      setPassword('');
      setConfirmPassword('');
    } catch (error) {
      setAuthError(accountFailureMessage(error, authMode));
    } finally {
      setSubmitting(false);
    }
  }

  async function signOut() {
    setSigningOut(true);
    setAuthError(null);

    try {
      await accountSessionService.signOut();
      dispatch(resetFinancials());
      setActiveSession(null);
      setPassword('');
      setConfirmPassword('');
    } catch (error) {
      setAuthError(actionFailureMessage(error, 'sign out'));
    } finally {
      setSigningOut(false);
    }
  }

  function selectWorkspace(workspaceId: number) {
    if (!activeSession || workspaceId === activeSession.workspaceId) {
      return;
    }

    dispatch(resetFinancials());
    setActiveSession(accountSessionService.selectWorkspace(activeSession, workspaceId));
  }

  function switchAuthMode(mode: AuthMode) {
    setAuthMode(mode);
    setAuthError(null);
    setPassword('');
    setConfirmPassword('');
  }

  function handleAuthTabKeyDown(event: KeyboardEvent<HTMLButtonElement>, mode: AuthMode) {
    let nextMode: AuthMode | null = null;

    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
      nextMode = mode === 'sign-in' ? 'sign-up' : 'sign-in';
    } else if (event.key === 'Home') {
      nextMode = 'sign-in';
    } else if (event.key === 'End') {
      nextMode = 'sign-up';
    }

    if (!nextMode) {
      return;
    }

    event.preventDefault();
    switchAuthMode(nextMode);
    (nextMode === 'sign-in' ? signInTabRef : signUpTabRef).current?.focus();
  }

  if (recoveringSession) {
    return (
      <main className="expenses-shell auth-shell">
        <section className="auth-card auth-status" aria-live="polite">
          <div className="auth-brand">
            <div>
              <p className="eyebrow">Household planning</p>
              <h1>Pay Period Planner</h1>
            </div>
            <ThemeToggle />
          </div>
          <p>Checking your session...</p>
        </section>
      </main>
    );
  }

  if (!activeSession) {
    const signingUp = authMode === 'sign-up';

    return (
      <main className="expenses-shell auth-shell">
        <section className="auth-card" aria-labelledby="account-heading">
          <div className="auth-brand">
            <div>
              <p className="eyebrow">Household planning</p>
              <h1>Pay Period Planner</h1>
            </div>
            <ThemeToggle />
          </div>
          <div className="auth-tabs" role="tablist" aria-label="Account access">
            <button
              aria-controls="account-panel"
              aria-selected={!signingUp}
              className={!signingUp ? 'active' : ''}
              id="sign-in-tab"
              onClick={() => switchAuthMode('sign-in')}
              onKeyDown={(event) => handleAuthTabKeyDown(event, 'sign-in')}
              ref={signInTabRef}
              role="tab"
              tabIndex={signingUp ? -1 : 0}
              type="button"
            >
              Sign In
            </button>
            <button
              aria-controls="account-panel"
              aria-selected={signingUp}
              className={signingUp ? 'active' : ''}
              id="sign-up-tab"
              onClick={() => switchAuthMode('sign-up')}
              onKeyDown={(event) => handleAuthTabKeyDown(event, 'sign-up')}
              ref={signUpTabRef}
              role="tab"
              tabIndex={signingUp ? 0 : -1}
              type="button"
            >
              Create Account
            </button>
          </div>
          <div
            aria-labelledby={signingUp ? 'sign-up-tab' : 'sign-in-tab'}
            id="account-panel"
            role="tabpanel"
          >
            <h2 id="account-heading">{signingUp ? 'Create your account' : 'Welcome back'}</h2>
            <form className="auth-form" onSubmit={(event) => void submitAccount(event)}>
              {signingUp && (
                <label>
                  Display name
                  <input
                    autoComplete="name"
                    disabled={submitting}
                    maxLength={120}
                    onChange={(event) => setDisplayName(event.target.value)}
                    required
                    type="text"
                    value={displayName}
                  />
                </label>
              )}
              <label>
                Email
                <input
                  autoComplete="email"
                  disabled={submitting}
                  maxLength={320}
                  onChange={(event) => setEmail(event.target.value)}
                  required
                  type="email"
                  value={email}
                />
              </label>
              <label>
                Password
                <input
                  autoComplete={signingUp ? 'new-password' : 'current-password'}
                  disabled={submitting}
                  maxLength={72}
                  minLength={signingUp ? 12 : undefined}
                  onChange={(event) => setPassword(event.target.value)}
                  required
                  type="password"
                  value={password}
                />
              </label>
              {signingUp && (
                <label>
                  Confirm password
                  <input
                    autoComplete="new-password"
                    disabled={submitting}
                    maxLength={72}
                    minLength={12}
                    onChange={(event) => setConfirmPassword(event.target.value)}
                    required
                    type="password"
                    value={confirmPassword}
                  />
                </label>
              )}
              {authError && (
                <p className="auth-error" role="alert">
                  {authError}
                </p>
              )}
              <button disabled={submitting} type="submit">
                {submitting ? 'Please wait...' : signingUp ? 'Create Account' : 'Sign In'}
              </button>
            </form>
          </div>
        </section>
      </main>
    );
  }

  return (
    <FinancialsPage
      account={activeSession.account}
      activeWorkspaceId={activeSession.workspaceId}
      onSignOut={signOut}
      onWorkspaceChange={selectWorkspace}
      sessionError={authError}
      signingOut={signingOut}
    />
  );
}

function accountFailureMessage(error: unknown, mode: AuthMode) {
  const action = mode === 'sign-up' ? 'create your account' : 'sign in';
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return `The email or password was not accepted. Reference: ${error.requestId}.`;
    }
    if (error.status === 409) {
      return `An account already exists for that email. Reference: ${error.requestId}.`;
    }
  }

  return actionFailureMessage(error, action);
}

function sessionRecoveryFailureMessage(error: unknown) {
  if (error instanceof ApiError && error.status === 502) {
    return `The frontend could not reach the backend. Reference: ${error.requestId}.`;
  }

  return actionFailureMessage(error, 'recover your session');
}

function actionFailureMessage(error: unknown, action: string) {
  if (error instanceof ApiError) {
    return `Unable to ${action}. ${error.detail} Reference: ${error.requestId}.`;
  }
  if (error instanceof Error) {
    return `Unable to ${action}: ${error.message}`;
  }
  return `Unable to ${action}. Confirm the backend is running, then try again.`;
}
