import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { FinancialsNavigation } from './FinancialsNavigation';
import { navigationSections } from './financialsNavigationModel';

describe('FinancialsNavigation', () => {
  it('keeps the desktop navigation and grouped mobile menu aligned', () => {
    const onChange = vi.fn();
    render(<FinancialsNavigation activeTab="overview" onChange={onChange} />);

    const navigationItemCount = navigationSections.reduce(
      (count, section) => count + section.items.length,
      0
    );

    expect(screen.getAllByRole('button')).toHaveLength(navigationItemCount);
    expect(screen.getByRole('button', { name: 'Overview' })).toHaveAttribute(
      'aria-current',
      'page'
    );
    expect(screen.getByRole('combobox', { name: 'Financial section' })).toHaveValue('overview');
  });

  it('changes sections from both navigation controls', () => {
    const onChange = vi.fn();
    render(<FinancialsNavigation activeTab="overview" onChange={onChange} />);

    fireEvent.click(screen.getByRole('button', { name: 'Debt' }));
    fireEvent.change(screen.getByRole('combobox', { name: 'Financial section' }), {
      target: { value: 'important-dates' },
    });

    expect(onChange).toHaveBeenNthCalledWith(1, 'debt');
    expect(onChange).toHaveBeenNthCalledWith(2, 'important-dates');
  });
});
