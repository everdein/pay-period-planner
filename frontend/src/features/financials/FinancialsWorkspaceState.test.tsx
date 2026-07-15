import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import type { FinancialsFailure } from './financialsSlice';
import { FinancialsWorkspaceState } from './FinancialsWorkspaceState';

const loadFailure: FinancialsFailure = {
  kind: 'error',
  message: 'Unable to load this workspace.',
  operation: 'load',
  requestId: 'request-load',
};

describe('FinancialsWorkspaceState', () => {
  it('routes a missing snapshot into onboarding without presenting the expected 404', () => {
    renderWorkspaceState({
      error: { ...loadFailure, kind: 'not-found' },
      snapshotMissing: true,
    });

    expect(screen.getByRole('heading', { name: 'Start your planning workspace' })).toBeVisible();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('announces workspace loading before a snapshot is available', () => {
    renderWorkspaceState({ loading: true });

    expect(screen.getByRole('heading', { name: 'Loading workspace' })).toBeVisible();
    expect(screen.getByText('Household')).toBeVisible();
  });

  it('keeps load failures and retry actions together', () => {
    const onRetry = vi.fn();
    renderWorkspaceState({ error: loadFailure, onRetry });

    expect(screen.getByRole('heading', { name: 'Financials unavailable' })).toBeVisible();
    expect(screen.getByRole('alert')).toHaveTextContent('Unable to load this workspace.');
    expect(screen.getByText('Request reference: request-load')).toBeVisible();

    fireEvent.click(screen.getByRole('button', { name: 'Try Again' }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('renders the financial workspace whenever a snapshot is available', () => {
    renderWorkspaceState({ hasSnapshot: true, loading: true });

    expect(screen.getByText('Financial workspace content')).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Loading workspace' })).not.toBeInTheDocument();
  });
});

function renderWorkspaceState(
  overrides: Partial<Parameters<typeof FinancialsWorkspaceState>[0]> = {}
) {
  const props: Parameters<typeof FinancialsWorkspaceState>[0] = {
    children: <p>Financial workspace content</p>,
    error: null,
    hasSnapshot: false,
    initializing: false,
    loading: false,
    onInitialize: vi.fn().mockResolvedValue(undefined),
    onRetry: vi.fn(),
    snapshotMissing: false,
    workspaceName: 'Household',
    ...overrides,
  };

  return render(<FinancialsWorkspaceState {...props} />);
}
