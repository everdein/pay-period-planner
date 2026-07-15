// cspell:ignore unstub
import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  ApiError,
  clearApiSessionContext,
  httpGet,
  httpPostVoid,
  httpPut,
  setUnauthorizedHandler,
} from './client';

describe('API request correlation', () => {
  afterEach(() => {
    sessionStorage.clear();
    clearApiSessionContext();
    setUnauthorizedHandler(null);
    vi.unstubAllGlobals();
  });

  it('adds a unique request ID to API requests', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ version: 1 }), {
        headers: { 'Content-Type': 'application/json' },
        status: 200,
      })
    );
    vi.stubGlobal('fetch', fetchMock);

    await httpGet<{ version: number }>('/api/v1/financials');

    expect(fetchMock).toHaveBeenCalledOnce();
    const init = fetchMock.mock.calls[0]?.[1] as { headers: Record<string, string> };
    expect(init.headers['X-Request-ID']).toMatch(/^[0-9a-f-]{36}$/);
    expect(init.headers.Authorization).toBeUndefined();
    expect(init).toMatchObject({ credentials: 'same-origin' });
  });

  it('aborts requests that outlive their account session context', async () => {
    const fetchMock = vi.fn(
      (_input: Parameters<typeof fetch>[0], init?: Parameters<typeof fetch>[1]) =>
        new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener(
            'abort',
            () => reject(new DOMException('The request was aborted.', 'AbortError')),
            { once: true }
          );
        })
    );
    vi.stubGlobal('fetch', fetchMock);

    const request = httpGet('/api/v1/financials', { workspaceId: 42 });
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());

    clearApiSessionContext();

    await expect(request).rejects.toMatchObject({ name: 'AbortError' });
    expect(
      (fetchMock.mock.calls[0]?.[1] as NonNullable<Parameters<typeof fetch>[1]>).signal?.aborted
    ).toBe(true);
  });

  it('bootstraps CSRF and scopes mutations to the requested workspace', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ headerName: 'X-XSRF-TOKEN', token: 'csrf-proof' }), {
          headers: { 'Content-Type': 'application/json' },
          status: 200,
        })
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ version: 2 }), {
          headers: { 'Content-Type': 'application/json' },
          status: 200,
        })
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ headerName: 'X-XSRF-TOKEN', token: 'fresh-proof' }), {
          headers: { 'Content-Type': 'application/json' },
          status: 200,
        })
      );
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    await httpPut('/api/v1/financials', { version: 2 }, { workspaceId: 42 });
    await httpPostVoid('/api/v1/auth/signout');

    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/auth/csrf');
    const mutation = fetchMock.mock.calls[1];
    expect(mutation?.[0]).toBe('/api/v1/financials');
    expect(mutation?.[1]).toMatchObject({ credentials: 'same-origin', method: 'PUT' });
    expect((mutation?.[1] as { headers: Record<string, string> }).headers).toMatchObject({
      'X-Workspace-ID': '42',
      'X-XSRF-TOKEN': 'csrf-proof',
    });
    expect(fetchMock.mock.calls[2]?.[0]).toBe('/api/v1/auth/csrf');
    expect(
      (fetchMock.mock.calls[3]?.[1] as { headers: Record<string, string> }).headers
    ).toMatchObject({ 'X-XSRF-TOKEN': 'fresh-proof' });
  });

  it('surfaces the backend request ID with API errors', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          detail: 'The financial snapshot changed after it was loaded.',
          requestId: 'server-request-456',
          title: '409 CONFLICT',
        }),
        {
          headers: {
            'Content-Type': 'application/problem+json',
            'X-Request-ID': 'server-request-456',
          },
          status: 409,
          statusText: 'Conflict',
        }
      )
    );
    vi.stubGlobal('fetch', fetchMock);

    const request = httpPut('/api/v1/financials', { version: 1 });

    await expect(request).rejects.toBeInstanceOf(ApiError);
    await expect(request).rejects.toMatchObject({
      detail: 'The financial snapshot changed after it was loaded.',
      message: 'The financial snapshot changed after it was loaded.',
      requestId: 'server-request-456',
      status: 409,
      title: '409 CONFLICT',
    });
  });
});
