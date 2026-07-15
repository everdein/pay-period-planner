import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mockClearApiSessionContext = vi.hoisted(() => vi.fn());
const mockHttpGet = vi.hoisted(() => vi.fn());
const mockHttpPost = vi.hoisted(() => vi.fn());
const mockHttpPostVoid = vi.hoisted(() => vi.fn());

vi.mock('./client', () => ({
  clearApiSessionContext: mockClearApiSessionContext,
  httpGet: mockHttpGet,
  httpPost: mockHttpPost,
  httpPostVoid: mockHttpPostVoid,
}));

import { accountSessionService, clearAccountSession } from './auth';

const account = {
  userId: 7,
  email: 'alex@example.com',
  displayName: 'Alex Morgan',
  expiresAt: '2026-06-29T12:00:00Z',
  workspaces: [
    { id: 11, name: 'Personal', role: 'OWNER' },
    { id: 12, name: 'Household', role: 'MEMBER' },
  ],
};

describe('accountSessionService', () => {
  beforeEach(() => {
    sessionStorage.clear();
    mockClearApiSessionContext.mockClear();
    mockHttpGet.mockReset();
    mockHttpPost.mockReset();
    mockHttpPostVoid.mockReset();
  });

  afterEach(() => clearAccountSession());

  it('recovers the account and activates its first workspace', async () => {
    mockHttpGet.mockResolvedValue(account);

    await expect(accountSessionService.recover()).resolves.toEqual({ account, workspaceId: 11 });

    expect(mockHttpGet).toHaveBeenCalledWith('/api/v1/auth/session', {
      notifyUnauthorized: false,
    });
    expect(sessionStorage.getItem('pay-period-planner.auth.workspaceId')).toBe('11');
  });

  it('restores a valid workspace preference and rejects an unavailable selection', async () => {
    sessionStorage.setItem('pay-period-planner.auth.workspaceId', '12');
    mockHttpGet.mockResolvedValue(account);
    const activeSession = await accountSessionService.recover();

    expect(activeSession.workspaceId).toBe(12);
    expect(() => accountSessionService.selectWorkspace(activeSession, 99)).toThrow(
      /not available/i
    );
  });

  it('migrates the pre-product-name workspace preference', async () => {
    sessionStorage.setItem('end-to-end-app.auth.workspaceId', '12');
    mockHttpGet.mockResolvedValue(account);

    await expect(accountSessionService.recover()).resolves.toEqual({ account, workspaceId: 12 });

    expect(sessionStorage.getItem('pay-period-planner.auth.workspaceId')).toBe('12');
    expect(sessionStorage.getItem('end-to-end-app.auth.workspaceId')).toBeNull();
  });

  it('revokes the server session and clears local transport state', async () => {
    sessionStorage.setItem('pay-period-planner.auth.workspaceId', '11');
    mockHttpPostVoid.mockResolvedValue(undefined);

    await accountSessionService.signOut();

    expect(mockHttpPostVoid).toHaveBeenCalledWith('/api/v1/auth/signout');
    expect(sessionStorage.getItem('pay-period-planner.auth.workspaceId')).toBeNull();
    expect(mockClearApiSessionContext).toHaveBeenCalled();
  });
});
