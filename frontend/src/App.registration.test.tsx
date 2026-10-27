import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

const dispatch = vi.hoisted(() => vi.fn());
vi.mock('./app/hooks', () => ({ useAppDispatch: () => dispatch }));
vi.mock('./features/financials/FinancialsPage', () => ({ default: () => null }));

import { clearAccountSession } from './api/auth';
import App from './App';

function pendingResponse() {
  let resolve!: (response: Response) => void;
  const promise = new Promise<Response>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

describe('registration availability with the real account client', () => {
  afterEach(() => {
    cleanup();
    clearAccountSession();
    vi.unstubAllGlobals();
  });

  it('keeps account creation available after an anonymous session triggers request cleanup', async () => {
    const session = pendingResponse();
    const registration = pendingResponse();
    let registrationSignal: AbortSignal | null = null;
    let aborted = false;
    const fetchMock = vi.fn(
      (input: Parameters<typeof fetch>[0], init?: Parameters<typeof fetch>[1]) => {
        if (String(input) === '/api/v1/auth/session') return session.promise;
        if (String(input) === '/api/v1/auth/registration') {
          registrationSignal = init?.signal ?? null;
          return new Promise<Response>((resolve, reject) => {
            registrationSignal?.addEventListener(
              'abort',
              () => {
                aborted = true;
                reject(new DOMException('The request was aborted.', 'AbortError'));
              },
              { once: true }
            );
            void registration.promise.then(resolve);
          });
        }
        throw new Error(`Unexpected request: ${String(input)}`);
      }
    );
    vi.stubGlobal('fetch', fetchMock);
    sessionStorage.setItem('pay-period-planner.auth.workspaceId', '42');
    render(<App />);

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/session', expect.anything())
    );
    await act(async () => session.resolve(new Response(null, { status: 401 })));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/registration', expect.anything())
    );
    await act(async () =>
      registration.resolve(
        new Response(JSON.stringify({ registrationEnabled: true }), {
          headers: { 'Content-Type': 'application/json' },
          status: 200,
        })
      )
    );

    expect(await screen.findByRole('tab', { name: 'Create Account' })).toBeInTheDocument();
    expect(sessionStorage.getItem('pay-period-planner.auth.workspaceId')).toBeNull();
    expect(aborted).toBe(false);
    expect(registrationSignal).not.toBeNull();
  });

  it('does not start an availability request after unmounting during session recovery', async () => {
    const session = pendingResponse();
    const fetchMock = vi.fn(() => session.promise);
    vi.stubGlobal('fetch', fetchMock);
    const view = render(<App />);
    view.unmount();
    await act(async () => session.resolve(new Response(null, { status: 401 })));
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/session', expect.anything());
  });
});
