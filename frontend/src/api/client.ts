import { getAuthorizationHeader } from './auth';

async function responseErrorMessage(res: Response): Promise<string> {
  if (res.status === 401) {
    return 'HTTP 401 Unauthorized: sign in to access financial data';
  }

  const text = await res.text().catch(() => '');

  if (!text) {
    return `HTTP ${res.status} ${res.statusText}`;
  }

  try {
    const problem = JSON.parse(text) as { detail?: unknown; title?: unknown };
    const detail = typeof problem.detail === 'string' ? problem.detail : undefined;
    const title = typeof problem.title === 'string' ? problem.title : undefined;
    return `HTTP ${res.status} ${res.statusText}: ${detail ?? title ?? text}`;
  } catch {
    return `HTTP ${res.status} ${res.statusText}: ${text}`;
  }
}

async function assertOk(res: Response): Promise<void> {
  if (!res.ok) {
    throw new Error(await responseErrorMessage(res));
  }
}

export async function httpGet<T>(url: string): Promise<T> {
  const res = await fetch(url, {
    headers: requestHeaders('application/json'),
  });

  await assertOk(res);

  return (await res.json()) as T;
}

export async function httpGetBlob(url: string): Promise<Blob> {
  const res = await fetch(url, {
    headers: requestHeaders(),
  });

  await assertOk(res);

  return await res.blob();
}

export async function httpPost<T, B = unknown>(url: string, body: B): Promise<T> {
  const res = await fetch(url, {
    method: 'POST',
    headers: requestHeaders('application/json'),
    body: JSON.stringify(body as unknown),
  });

  await assertOk(res);

  return (await res.json()) as T;
}

export async function httpPostRaw<T>(
  url: string,
  body: string | Blob | ArrayBuffer,
  contentType: string
): Promise<T> {
  const res = await fetch(url, {
    method: 'POST',
    headers: requestHeaders(contentType),
    body,
  });

  await assertOk(res);

  return (await res.json()) as T;
}

export async function httpPut<T, B = unknown>(url: string, body: B): Promise<T> {
  const res = await fetch(url, {
    method: 'PUT',
    headers: requestHeaders('application/json'),
    body: JSON.stringify(body as unknown),
  });

  await assertOk(res);

  return (await res.json()) as T;
}

export async function httpDelete(url: string): Promise<void> {
  const res = await fetch(url, {
    method: 'DELETE',
    headers: requestHeaders('application/json'),
  });

  await assertOk(res);
}

function requestHeaders(contentType?: string) {
  const headers: Record<string, string> = {};
  const authorization = getAuthorizationHeader();

  if (contentType) {
    headers['content-type'] = contentType;
  }

  if (authorization) {
    headers.Authorization = authorization;
  }

  return headers;
}
