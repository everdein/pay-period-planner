import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { WorkspaceOnboarding } from './WorkspaceOnboarding';
import { defaultPayPeriod } from './workspaceOnboardingDates';

describe('WorkspaceOnboarding', () => {
  it('defaults to a fourteen-day pay period', () => {
    expect(defaultPayPeriod('2026-07-10')).toEqual({
      startDate: '2026-07-10',
      endDate: '2026-07-23',
    });
  });

  it('submits the selected pay period without adding sample values', async () => {
    const onInitialize = vi.fn().mockResolvedValue(undefined);
    render(
      <WorkspaceOnboarding
        error={null}
        initializing={false}
        onInitialize={onInitialize}
        workspaceName="Personal"
      />
    );

    fireEvent.change(screen.getByLabelText(/pay period starts/i), {
      target: { value: '2026-08-01' },
    });
    fireEvent.change(screen.getByLabelText(/pay period ends/i), {
      target: { value: '2026-08-14' },
    });
    fireEvent.click(screen.getByRole('button', { name: /create financial snapshot/i }));

    await waitFor(() =>
      expect(onInitialize).toHaveBeenCalledWith({
        startDate: '2026-08-01',
        endDate: '2026-08-14',
      })
    );
    expect(screen.getByText(/no sample or personal values/i)).toBeInTheDocument();
  });

  it('keeps server errors in the setup context', () => {
    render(
      <WorkspaceOnboarding
        error="Unable to create the snapshot."
        initializing={false}
        onInitialize={vi.fn()}
        workspaceName="Personal"
      />
    );

    expect(screen.getByRole('alert')).toHaveTextContent('Unable to create the snapshot.');
  });
});
