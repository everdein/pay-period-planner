import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AppErrorBoundary } from './AppErrorBoundary';

function BrokenView(): never {
  throw new Error('sensitive render details');
}

describe('AppErrorBoundary', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('contains render failures and reports only sanitized metadata', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    render(
      <AppErrorBoundary>
        <BrokenView />
      </AppErrorBoundary>
    );

    expect(screen.getByRole('alert')).toHaveTextContent('Pay Period Planner could not continue');
    expect(screen.getByText(/Reference:/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reload Application' })).toBeInTheDocument();

    const reportCall = consoleError.mock.calls.find(
      ([message]) => message === 'Frontend error captured'
    );
    expect(reportCall?.[1]).toMatchObject({
      errorType: 'Error',
      kind: 'render-error',
    });
    expect(JSON.stringify(reportCall?.[1])).not.toContain('sensitive render details');
  });
});
