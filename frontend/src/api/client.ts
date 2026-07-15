const REQUEST_ID_HEADER = 'X-Request-ID';
const CSRF_PATH = '/api/v1/auth/csrf';
const WORKSPACE_ID_HEADER = 'X-Workspace-ID';

type CsrfProof = {
  headerName: string;
  token: string;
};

let csrfProofRequest: Promise<CsrfProof> | null = null;
let unauthorizedHandler: (() => void) | null = null;
const activeRequestControllers = new Set<AbortController>();

type ApiRequestOptions = {
  notifyUnauthorized?: boolean;
  workspaceId?: number;
};

type ResponseErrorDetails = {
  detail: string;
  requestId?: string;
  title?: string;
};

export class ApiError extends Error {
  readonly detail: string;
  readonly requestId: string;
  readonly status: number;
  readonly title: string | undefined;

  constructor(detail: string, status: number, requestId: string, title?: string) {
    super(detail);
    this.name = 'ApiError';
    this.detail = detail;
    this.requestId = requestId;
    this.status = status;
    this.title = title;
  }
}

async function responseErrorDetails(res: Response): Promise<ResponseErrorDetails> {
  if (res.status === 401) {
    return { detail: 'Sign in to access financial data' };
  }

  const text = await res.text().catch(() => '');

  if (!text) {
    return { detail: res.statusText || `Request failed with status ${res.status}` };
  }

  try {
    const problem = JSON.parse(text) as {
      detail?: unknown;
      requestId?: unknown;
      title?: unknown;
    };
    const detail = typeof problem.detail === 'string' ? problem.detail : undefined;
    const requestId = typeof problem.requestId === 'string' ? problem.requestId : undefined;
    const title = typeof problem.title === 'string' ? problem.title : undefined;
    return {
      detail: detail ?? title ?? text,
      ...(requestId ? { requestId } : {}),
      ...(title ? { title } : {}),
    };
  } catch {
    return { detail: text };
  }
}

async function assertOk(
  res: Response,
  clientRequestId: string,
  notifyUnauthorized = true
): Promise<void> {
  if (!res.ok) {
    const details = await responseErrorDetails(res);
    const requestId = res.headers.get(REQUEST_ID_HEADER) ?? details.requestId ?? clientRequestId;
    if (res.status === 401 && notifyUnauthorized) {
      unauthorizedHandler?.();
    }
    throw new ApiError(details.detail, res.status, requestId, details.title);
  }
}

export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

export function clearApiSessionContext() {
  activeRequestControllers.forEach((controller) => controller.abort());
  activeRequestControllers.clear();
  csrfProofRequest = null;
}

export async function httpGet<T>(url: string, options: ApiRequestOptions = {}): Promise<T> {
  const { requestId, response } = await apiFetch(url, {}, 'application/json', options.workspaceId);

  await assertOk(response, requestId, options.notifyUnauthorized);

  return (await response.json()) as T;
}

export async function httpGetBlob(url: string, options: ApiRequestOptions = {}): Promise<Blob> {
  const { requestId, response } = await apiFetch(url, {}, undefined, options.workspaceId);

  await assertOk(response, requestId);

  return await response.blob();
}

export async function httpPost<T, B = unknown>(
  url: string,
  body: B,
  options: ApiRequestOptions = {}
): Promise<T> {
  const { requestId, response } = await apiFetch(
    url,
    {
      method: 'POST',
      body: JSON.stringify(body as unknown),
    },
    'application/json',
    options.workspaceId
  );

  await assertOk(response, requestId);

  return (await response.json()) as T;
}

export async function httpPostVoid(url: string, options: ApiRequestOptions = {}): Promise<void> {
  const { requestId, response } = await apiFetch(
    url,
    { method: 'POST' },
    undefined,
    options.workspaceId
  );

  await assertOk(response, requestId);
}

export async function httpPut<T, B = unknown>(
  url: string,
  body: B,
  options: ApiRequestOptions = {}
): Promise<T> {
  const { requestId, response } = await apiFetch(
    url,
    {
      method: 'PUT',
      body: JSON.stringify(body as unknown),
    },
    'application/json',
    options.workspaceId
  );

  await assertOk(response, requestId);

  return (await response.json()) as T;
}

export async function httpDelete(url: string, options: ApiRequestOptions = {}): Promise<void> {
  const { requestId, response } = await apiFetch(
    url,
    { method: 'DELETE' },
    'application/json',
    options.workspaceId
  );

  await assertOk(response, requestId);
}

async function apiFetch(
  url: string,
  init: NonNullable<Parameters<typeof fetch>[1]> = {},
  contentType?: string,
  workspaceId?: number
): Promise<{ requestId: string; response: Response }> {
  const requestId = createRequestId();
  const proof = isUnsafeMethod(init.method) ? await requireCsrfProof() : null;
  const response = await trackedFetch(url, {
    ...init,
    credentials: 'same-origin',
    headers: requestHeaders(requestId, contentType, proof, workspaceId),
  });

  return { requestId, response };
}

function requestHeaders(
  requestId: string,
  contentType?: string,
  proof?: CsrfProof | null,
  workspaceId?: number
) {
  const headers: Record<string, string> = { [REQUEST_ID_HEADER]: requestId };

  if (contentType) {
    headers['content-type'] = contentType;
  }

  if (workspaceId !== undefined) {
    headers[WORKSPACE_ID_HEADER] = String(workspaceId);
  }

  if (proof) {
    headers[proof.headerName] = proof.token;
  }

  return headers;
}

async function requireCsrfProof() {
  csrfProofRequest ??= fetchCsrfProof();
  const currentRequest = csrfProofRequest;
  try {
    return await currentRequest;
  } finally {
    if (csrfProofRequest === currentRequest) {
      csrfProofRequest = null;
    }
  }
}

async function fetchCsrfProof(): Promise<CsrfProof> {
  const requestId = createRequestId();
  const response = await trackedFetch(CSRF_PATH, {
    credentials: 'same-origin',
    headers: requestHeaders(requestId),
  });

  await assertOk(response, requestId);
  const candidate = (await response.json()) as Partial<CsrfProof>;
  if (!candidate.headerName || !candidate.token) {
    throw new ApiError('The backend returned an invalid CSRF proof', 500, requestId);
  }

  return { headerName: candidate.headerName, token: candidate.token };
}

async function trackedFetch(
  input: Parameters<typeof fetch>[0],
  init: NonNullable<Parameters<typeof fetch>[1]>
) {
  const controller = new AbortController();
  activeRequestControllers.add(controller);

  try {
    return await fetch(input, { ...init, signal: controller.signal });
  } finally {
    activeRequestControllers.delete(controller);
  }
}

function isUnsafeMethod(method?: string) {
  return !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes((method ?? 'GET').toUpperCase());
}

function createRequestId() {
  return globalThis.crypto.randomUUID();
}
