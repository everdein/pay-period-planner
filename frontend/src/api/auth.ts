export type ApiCredentials = {
  password: string;
  username: string;
};

const AUTH_TOKEN_STORAGE_KEY = 'end-to-end-app.auth.basicToken';

export function saveApiCredentials(credentials: ApiCredentials) {
  const token = basicAuthorizationHeader(credentials);
  storage()?.setItem(AUTH_TOKEN_STORAGE_KEY, token);
}

export function clearApiCredentials() {
  storage()?.removeItem(AUTH_TOKEN_STORAGE_KEY);
}

export function getAuthorizationHeader() {
  return storage()?.getItem(AUTH_TOKEN_STORAGE_KEY) ?? null;
}

export function hasApiCredentials() {
  return getAuthorizationHeader() !== null;
}

function basicAuthorizationHeader({ password, username }: ApiCredentials) {
  return `Basic ${base64Utf8(`${username}:${password}`)}`;
}

function base64Utf8(value: string) {
  const bytes = new TextEncoder().encode(value);
  let binary = '';
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
}

function storage() {
  try {
    return window.sessionStorage;
  } catch {
    return null;
  }
}
