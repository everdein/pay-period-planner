import {
  clearApiSessionContext,
  httpGet,
  httpPost,
  httpPostVoid,
  setActiveWorkspaceId,
} from './client';

export type WorkspaceAccess = {
  id: number;
  name: string;
  role: string;
};

export type AccountSession = {
  userId: number;
  email: string;
  displayName: string;
  expiresAt: string;
  workspaces: WorkspaceAccess[];
};

export type ActiveAccountSession = {
  account: AccountSession;
  workspaceId: number;
};

export type AccountSignInRequest = {
  email: string;
  password: string;
};

export type AccountSignUpRequest = AccountSignInRequest & {
  displayName: string;
};

const WORKSPACE_STORAGE_KEY = 'end-to-end-app.auth.workspaceId';

export const accountSessionService = {
  recover: async () =>
    activateSession(await httpGet<AccountSession>('/api/v1/auth/session', false)),
  signIn: async (request: AccountSignInRequest) => {
    setActiveWorkspaceId(null);
    return activateSession(
      await httpPost<AccountSession, AccountSignInRequest>('/api/v1/auth/signin', request)
    );
  },
  signUp: async (request: AccountSignUpRequest) => {
    setActiveWorkspaceId(null);
    return activateSession(
      await httpPost<AccountSession, AccountSignUpRequest>('/api/v1/auth/signup', request)
    );
  },
  signOut: async () => {
    await httpPostVoid('/api/v1/auth/signout');
    clearAccountSession();
  },
  selectWorkspace: (activeSession: ActiveAccountSession, workspaceId: number) => {
    const workspace = activeSession.account.workspaces.find((candidate) => candidate.id === workspaceId);
    if (!workspace) {
      throw new Error('The selected workspace is not available to this account.');
    }

    persistWorkspaceId(workspace.id);
    setActiveWorkspaceId(workspace.id);
    return { ...activeSession, workspaceId: workspace.id };
  },
};

export function clearAccountSession() {
  storage()?.removeItem(WORKSPACE_STORAGE_KEY);
  clearApiSessionContext();
}

function activateSession(account: AccountSession): ActiveAccountSession {
  const preferredWorkspaceId = storedWorkspaceId();
  const workspace =
    account.workspaces.find((candidate) => candidate.id === preferredWorkspaceId) ??
    account.workspaces[0];

  if (!workspace) {
    clearAccountSession();
    throw new Error('This account does not have access to a financial workspace.');
  }

  persistWorkspaceId(workspace.id);
  setActiveWorkspaceId(workspace.id);
  return { account, workspaceId: workspace.id };
}

function storedWorkspaceId() {
  const value = storage()?.getItem(WORKSPACE_STORAGE_KEY);
  if (!value) {
    return null;
  }

  const workspaceId = Number(value);
  return Number.isSafeInteger(workspaceId) && workspaceId > 0 ? workspaceId : null;
}

function persistWorkspaceId(workspaceId: number) {
  storage()?.setItem(WORKSPACE_STORAGE_KEY, String(workspaceId));
}

function storage() {
  try {
    return window.sessionStorage;
  } catch {
    return null;
  }
}
