import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { WorkflowNotice } from './WorkflowNotice';

describe('WorkflowNotice', () => {
  it('announces successful work politely', () => {
    render(<WorkflowNotice title="Changes saved." tone="success" />);

    expect(screen.getByRole('status')).toHaveTextContent('Changes saved.');
  });

  it('announces a conflict with its recovery action', () => {
    render(
      <WorkflowNotice
        actions={<button type="button">Reload Latest</button>}
        message="The draft was not saved."
        requestId="request-conflict"
        title="A newer snapshot is available"
        tone="conflict"
      />
    );

    expect(screen.getByRole('alert')).toHaveTextContent('The draft was not saved.');
    expect(screen.getByText('Request reference: request-conflict')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Reload Latest' })).toBeVisible();
  });
});
