import { act, renderHook } from '@testing-library/react';
import { type FormEvent } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { FinancialsDraft } from './financialsDraft';
import {
  createTestFinancialsDraft,
  useCanonicalDraftTestState,
} from './financialsDraftTestSupport';
import type { DraftImportantDate } from './financialsTypes';
import { useImportantDatesDraft } from './useImportantDatesDraft';

const preventDefault = vi.fn();
const submitEvent = { preventDefault } as unknown as FormEvent<HTMLFormElement>;

function importantDate(overrides: Partial<DraftImportantDate> = {}): DraftImportantDate {
  return {
    date: '2026-12-25',
    event: 'Christmas',
    id: 1,
    type: 'Holiday',
    ...overrides,
  };
}

function useImportantDatesHarness(initialDraft: FinancialsDraft, todayIso: string) {
  const { dispatch, state } = useCanonicalDraftTestState(initialDraft);
  return {
    ...useImportantDatesDraft(state.draft, dispatch, state.resetGeneration, todayIso),
    dispatch,
    state,
  };
}

describe('useImportantDatesDraft', () => {
  beforeEach(() => {
    preventDefault.mockClear();
  });

  it('sorts dates, assigns statuses, and identifies the next date', () => {
    const draft = createTestFinancialsDraft({
      importantDates: [
        importantDate({ date: '2026-12-25', id: 3 }),
        importantDate({ date: '2026-01-01', event: 'New Year', id: 1 }),
        importantDate({ date: '2026-07-04', event: 'Independence Day', id: 2 }),
      ],
    });
    const { result } = renderHook(() => useImportantDatesHarness(draft, '2026-06-22'));

    expect(result.current.importantDates.map(({ id, status }) => ({ id, status }))).toEqual([
      { id: 1, status: 'passed' },
      { id: 2, status: 'next' },
      { id: 3, status: 'upcoming' },
    ]);
    expect(result.current.nextImportantDate?.id).toBe(2);
    expect(result.current.state.revision).toBe(0);
  });

  it('dispatches a temporary date and resets the editor', () => {
    const { result } = renderHook(() =>
      useImportantDatesHarness(createTestFinancialsDraft(), '2026-06-22')
    );

    act(() => result.current.updateImportantDateForm('event', ' Birthday '));
    act(() => result.current.updateImportantDateForm('date', '2026-08-10'));
    act(() => result.current.updateImportantDateForm('type', ' Personal '));
    act(() => result.current.submitImportantDate(submitEvent));

    expect(preventDefault).toHaveBeenCalledOnce();
    expect(result.current.draftImportantDates).toEqual([
      { date: '2026-08-10', event: 'Birthday', id: -1, type: 'Personal' },
    ]);
    expect(result.current.importantDateForm).toEqual({ date: '', event: '', type: 'Holiday' });
    expect(result.current.state.revision).toBe(1);
  });

  it('updates an existing date without changing its identity', () => {
    const existingDate = importantDate();
    const draft = createTestFinancialsDraft({ importantDates: [existingDate] });
    const { result } = renderHook(() => useImportantDatesHarness(draft, '2026-06-22'));

    act(() => result.current.startImportantDateEdit(existingDate));
    act(() => result.current.updateImportantDateForm('event', ' Winter Holiday '));
    act(() => result.current.updateImportantDateForm('type', ' Company Day Off '));
    act(() => result.current.submitImportantDate(submitEvent));

    expect(result.current.draftImportantDates).toEqual([
      {
        ...existingDate,
        event: 'Winter Holiday',
        type: 'Company Day Off',
      },
    ]);
    expect(result.current.editingImportantDateId).toBeNull();
    expect(result.current.state.revision).toBe(1);
  });

  it('cancels an edit without changing the canonical draft', () => {
    const existingDate = importantDate();
    const draft = createTestFinancialsDraft({ importantDates: [existingDate] });
    const { result } = renderHook(() => useImportantDatesHarness(draft, '2026-06-22'));

    act(() => result.current.startImportantDateEdit(existingDate));
    act(() => result.current.updateImportantDateForm('event', 'Changed'));
    act(() => result.current.cancelImportantDateEdit());

    expect(result.current.draftImportantDates).toEqual([existingDate]);
    expect(result.current.editingImportantDateId).toBeNull();
    expect(result.current.importantDateForm).toEqual({ date: '', event: '', type: 'Holiday' });
    expect(result.current.state.revision).toBe(0);
  });

  it('removes a date through the canonical pending-removal command', () => {
    const draft = createTestFinancialsDraft({
      importantDates: [importantDate(), importantDate({ id: 2 })],
    });
    const { result } = renderHook(() => useImportantDatesHarness(draft, '2026-06-22'));

    act(() =>
      result.current.dispatch({
        removal: { id: 1, name: 'Christmas', type: 'important-date' },
        type: 'request-removal',
      })
    );
    act(() => result.current.dispatch({ type: 'confirm-removal' }));

    expect(result.current.draftImportantDates.map(({ id }) => id)).toEqual([2]);
    expect(result.current.state.revision).toBe(1);
  });
});
