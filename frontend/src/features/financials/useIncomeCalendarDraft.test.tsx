import { act, renderHook } from '@testing-library/react';
import { type FormEvent } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { FinancialsDraft } from './financialsDraft';
import {
  createTestFinancialsDraft,
  useCanonicalDraftTestState,
} from './financialsDraftTestSupport';
import type { DraftIncomeEvent } from './financialsTypes';
import { useIncomeCalendarDraft } from './useIncomeCalendarDraft';

const preventDefault = vi.fn();
const submitEvent = { preventDefault } as unknown as FormEvent<HTMLFormElement>;

function incomeEvent(overrides: Partial<DraftIncomeEvent> = {}): DraftIncomeEvent {
  return {
    checkNumber: 1,
    checksInMonth: 0,
    date: '2026-01-09',
    id: 1,
    label: 'Paycheck',
    type: 'Paycheck',
    ...overrides,
  };
}

function useIncomeCalendarHarness(initialDraft: FinancialsDraft, todayIso: string) {
  const { dispatch, state } = useCanonicalDraftTestState(initialDraft);
  return {
    ...useIncomeCalendarDraft(state.draft, dispatch, state.resetGeneration, todayIso),
    dispatch,
    state,
  };
}

describe('useIncomeCalendarDraft', () => {
  beforeEach(() => {
    preventDefault.mockClear();
  });

  it('sorts events, counts monthly paychecks, and identifies the current paycheck', () => {
    const draft = createTestFinancialsDraft({
      incomeEvents: [
        incomeEvent({ checkNumber: 2, date: '2026-01-23', id: 2 }),
        incomeEvent(),
        incomeEvent({ checkNumber: null, date: '2026-02-01', id: 3, label: 'Bonus' }),
      ],
    });
    const { result } = renderHook(() => useIncomeCalendarHarness(draft, '2026-01-15'));

    expect(result.current.incomeEvents.map(({ id }) => id)).toEqual([1, 2, 3]);
    expect(result.current.incomeEvents[0]).toMatchObject({ checksInMonth: 2, status: 'current' });
    expect(result.current.incomeEvents[1]).toMatchObject({ checksInMonth: 2, status: 'upcoming' });
    expect(result.current.incomeEvents[2]).toMatchObject({ checksInMonth: 0, status: 'upcoming' });
    expect(result.current.currentPaycheck?.id).toBe(1);
    expect(result.current.state.revision).toBe(0);
  });

  it('dispatches a temporary event and resets the editor', () => {
    const { result } = renderHook(() =>
      useIncomeCalendarHarness(createTestFinancialsDraft(), '2026-06-22')
    );

    act(() => result.current.updateIncomeEventForm('date', '2026-07-01'));
    act(() => result.current.updateIncomeEventForm('label', ' Bonus '));
    act(() => result.current.updateIncomeEventForm('type', ' Other '));
    act(() => result.current.updateIncomeEventForm('checkNumber', ''));
    act(() => result.current.submitIncomeEvent(submitEvent));

    expect(preventDefault).toHaveBeenCalledOnce();
    expect(result.current.draftIncomeEvents).toEqual([
      {
        checkNumber: null,
        checksInMonth: 0,
        date: '2026-07-01',
        id: -1,
        label: 'Bonus',
        type: 'Other',
      },
    ]);
    expect(result.current.incomeEventForm).toEqual({
      checkNumber: '',
      date: '',
      label: '',
      type: 'Paycheck',
    });
    expect(result.current.state.revision).toBe(1);
  });

  it('updates an existing event without changing its identity', () => {
    const existingEvent = incomeEvent();
    const draft = createTestFinancialsDraft({ incomeEvents: [existingEvent] });
    const { result } = renderHook(() => useIncomeCalendarHarness(draft, '2026-06-22'));

    act(() => result.current.startIncomeEventEdit(existingEvent));
    act(() => result.current.updateIncomeEventForm('label', ' Updated Paycheck '));
    act(() => result.current.updateIncomeEventForm('checkNumber', '12'));
    act(() => result.current.submitIncomeEvent(submitEvent));

    expect(result.current.draftIncomeEvents).toEqual([
      {
        ...existingEvent,
        checkNumber: 12,
        label: 'Updated Paycheck',
      },
    ]);
    expect(result.current.editingIncomeEventId).toBeNull();
    expect(result.current.state.revision).toBe(1);
  });

  it('replaces only numbered events in the generated year', () => {
    const oneTimeEvent = incomeEvent({
      checkNumber: null,
      date: '2026-03-01',
      id: 2,
      label: 'Bonus',
    });
    const priorYearEvent = incomeEvent({ date: '2025-12-26', id: 3 });
    const draft = createTestFinancialsDraft({
      incomeEvents: [incomeEvent(), oneTimeEvent, priorYearEvent],
    });
    const { result } = renderHook(() => useIncomeCalendarHarness(draft, '2026-06-22'));

    act(() => result.current.updateRecurringPaydayForm('firstPayDate', '2026-12-04'));
    act(() => result.current.updateRecurringPaydayForm('startingCheckNumber', '20'));
    act(() => result.current.submitRecurringPaydays(submitEvent));

    expect(result.current.draftIncomeEvents).toEqual(
      expect.arrayContaining([
        oneTimeEvent,
        priorYearEvent,
        expect.objectContaining({ checkNumber: 20, date: '2026-12-04', id: -1 }),
        expect.objectContaining({ checkNumber: 21, date: '2026-12-18', id: -2 }),
      ])
    );
    expect(result.current.draftIncomeEvents).toHaveLength(4);
    expect(result.current.draftIncomeEvents).not.toContainEqual(incomeEvent());
    expect(result.current.state.revision).toBe(1);
  });

  it('clears an incompatible first payday and ignores invalid generation', () => {
    const draft = createTestFinancialsDraft({ incomeEvents: [incomeEvent()] });
    const { result } = renderHook(() => useIncomeCalendarHarness(draft, '2026-06-22'));

    act(() => result.current.updateRecurringPaydayForm('firstPayDate', '2026-01-09'));
    act(() => result.current.updateRecurringPaydayForm('year', '2027'));
    act(() => result.current.submitRecurringPaydays(submitEvent));

    expect(result.current.recurringPaydayForm).toMatchObject({
      firstPayDate: '',
      secondPayDate: '',
      year: '2027',
    });
    expect(result.current.draftIncomeEvents).toEqual([incomeEvent()]);
    expect(result.current.state.revision).toBe(0);
  });

  it('removes an event through the canonical pending-removal command', () => {
    const draft = createTestFinancialsDraft({
      incomeEvents: [incomeEvent(), incomeEvent({ id: 2 })],
    });
    const { result } = renderHook(() => useIncomeCalendarHarness(draft, '2026-06-22'));

    act(() =>
      result.current.dispatch({
        removal: { id: 1, name: 'Paycheck', type: 'income' },
        type: 'request-removal',
      })
    );
    act(() => result.current.dispatch({ type: 'confirm-removal' }));

    expect(result.current.draftIncomeEvents.map(({ id }) => id)).toEqual([2]);
    expect(result.current.state.revision).toBe(1);
  });
});
