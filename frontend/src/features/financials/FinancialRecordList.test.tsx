import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { FinancialRecordList, FinancialRecordListItem } from './FinancialRecordList';

describe('FinancialRecordList', () => {
  it('keeps the record value, metadata, state, and actions in one semantic list item', () => {
    render(
      <FinancialRecordList
        description="Synthetic records used for responsive verification."
        emptyDescription="Add a synthetic record to begin."
        emptyTitle="No synthetic records yet."
        footer={
          <>
            Total: <strong>$149.99</strong>
          </>
        }
        headingId="synthetic-records-heading"
        itemCount={1}
        summary="$149.99 total"
        summaryLabel="Synthetic record summary"
        title="Synthetic records"
      >
        <FinancialRecordListItem
          actions={<button type="button">Edit</button>}
          badge="Current"
          metadata={['Checking', 'Due 12/31/2026']}
          primary="Annual membership"
          state={<span>Open</span>}
          tone="positive"
          value={<strong>$149.99</strong>}
        />
      </FinancialRecordList>
    );

    const list = screen.getByRole('list');
    const item = within(list).getByRole('listitem');

    expect(screen.getByRole('heading', { name: 'Synthetic records' })).toBeVisible();
    expect(item).toHaveClass('positive');
    expect(item).toHaveTextContent('Annual membershipCurrent$149.99CheckingDue 12/31/2026OpenEdit');
    expect(within(item).getByRole('button', { name: 'Edit' })).toBeEnabled();
  });

  it('renders a useful empty state without an empty list', () => {
    render(
      <FinancialRecordList
        description="Synthetic records used for responsive verification."
        emptyDescription="Add a synthetic record to begin."
        emptyTitle="No synthetic records yet."
        headingId="empty-synthetic-records-heading"
        itemCount={0}
        summaryLabel="Empty synthetic record summary"
        title="Synthetic records"
      >
        {null}
      </FinancialRecordList>
    );

    expect(screen.queryByRole('list')).not.toBeInTheDocument();
    expect(screen.getByText('No synthetic records yet.')).toBeVisible();
    expect(screen.getByText('Add a synthetic record to begin.')).toBeVisible();
  });
});
