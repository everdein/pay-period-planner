import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

vi.mock('./app/hooks', () => ({
  useAppDispatch: () => vi.fn(),
  useAppSelector: vi.fn(() => ({ data: null, status: 'idle', error: null })),
}));

import App from './App';

describe('App', () => {
  it('renders the app title and buttons', () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: /end-to-end app/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /get/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /post/i })).toBeInTheDocument();
  });
});
