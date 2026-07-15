import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import type { FinancialsFailure } from './financialsSlice';
import { FinancialsWorkflowFeedback } from './FinancialsWorkflowFeedback';

type FeedbackProps = Parameters<typeof FinancialsWorkflowFeedback>[0];

describe('FinancialsWorkflowFeedback', () => {
  it('offers next actions after onboarding and announces dirty work', () => {
    const onNavigate = vi.fn();
    renderFeedback({ isDirty: true, onboardingComplete: true, onNavigate });

    expect(screen.getByText('Workspace ready')).toBeVisible();
    expect(screen.getByRole('status')).toHaveTextContent('You have unsaved changes.');

    fireEvent.click(screen.getByRole('button', { name: 'Add Income' }));
    fireEvent.click(screen.getByRole('button', { name: 'Add Monthly Withdrawal' }));

    expect(onNavigate).toHaveBeenNthCalledWith(1, 'income-summary');
    expect(onNavigate).toHaveBeenNthCalledWith(2, 'monthly-withdrawals');
  });

  it('announces a successful save only after the submitted draft is clean', () => {
    const { rerender } = renderFeedback({ isDirty: true, saveNotice: 'Changes saved.' });

    expect(screen.queryByText('Changes saved.')).not.toBeInTheDocument();

    rerender(<FinancialsWorkflowFeedback {...feedbackProps({ saveNotice: 'Changes saved.' })} />);
    expect(screen.getByRole('status')).toHaveTextContent('Changes saved.');
  });

  it('keeps conflict recovery with the stale draft', () => {
    const onReloadLatestSnapshot = vi.fn();
    renderFeedback({
      error: saveFailure('conflict'),
      isDirty: true,
      onReloadLatestSnapshot,
    });

    const alert = screen.getByRole('alert');
    expect(within(alert).getByText('A newer snapshot is available')).toBeVisible();
    expect(within(alert).getByText('Request reference: request-save')).toBeVisible();
    expect(screen.queryByText('You have unsaved changes.')).not.toBeInTheDocument();

    fireEvent.click(within(alert).getByRole('button', { name: 'Discard Draft and Reload' }));
    expect(onReloadLatestSnapshot).toHaveBeenCalledOnce();
  });

  it('routes save and export retry and dismissal actions independently', () => {
    const onDismissExportError = vi.fn();
    const onDismissSaveError = vi.fn();
    const onRetryExport = vi.fn().mockResolvedValue(undefined);
    const onRetrySave = vi.fn().mockResolvedValue(undefined);
    renderFeedback({
      error: saveFailure('error'),
      exportError: { message: 'Export unavailable.', requestId: 'request-export' },
      onDismissExportError,
      onDismissSaveError,
      onRetryExport,
      onRetrySave,
    });

    const alerts = screen.getAllByRole('alert');
    const saveAlert = alerts.find((alert) => within(alert).queryByText('Changes not saved'));
    const exportAlert = alerts.find((alert) => within(alert).queryByText('Backup not exported'));
    expect(saveAlert).toBeDefined();
    expect(exportAlert).toBeDefined();

    fireEvent.click(within(saveAlert!).getByRole('button', { name: 'Try Save Again' }));
    fireEvent.click(within(saveAlert!).getByRole('button', { name: 'Dismiss' }));
    fireEvent.click(within(exportAlert!).getByRole('button', { name: 'Try Export Again' }));
    fireEvent.click(within(exportAlert!).getByRole('button', { name: 'Dismiss' }));

    expect(onRetrySave).toHaveBeenCalledOnce();
    expect(onDismissSaveError).toHaveBeenCalledOnce();
    expect(onRetryExport).toHaveBeenCalledOnce();
    expect(onDismissExportError).toHaveBeenCalledOnce();
  });
});

function renderFeedback(overrides: Partial<FeedbackProps> = {}) {
  return render(<FinancialsWorkflowFeedback {...feedbackProps(overrides)} />);
}

function feedbackProps(overrides: Partial<FeedbackProps> = {}): FeedbackProps {
  return {
    error: null,
    exportError: null,
    isDirty: false,
    onboardingComplete: false,
    onDismissExportError: vi.fn(),
    onDismissSaveError: vi.fn(),
    onNavigate: vi.fn(),
    onReloadLatestSnapshot: vi.fn(),
    onRetryExport: vi.fn().mockResolvedValue(undefined),
    onRetrySave: vi.fn().mockResolvedValue(undefined),
    saveNotice: null,
    ...overrides,
  };
}

function saveFailure(kind: 'conflict' | 'error'): FinancialsFailure {
  return {
    kind,
    message: kind === 'conflict' ? 'Snapshot version conflict' : 'Save unavailable.',
    operation: 'save',
    requestId: 'request-save',
  };
}
