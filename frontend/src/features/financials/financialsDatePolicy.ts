import type { PayCadence } from '../../api/endpoints/financials';

export type PayPeriod = {
  end: string;
  start: string;
};

export const PAY_CADENCE_OPTIONS: { label: string; value: PayCadence }[] = [
  { label: 'Weekly', value: 'WEEKLY' },
  { label: 'Every two weeks', value: 'BIWEEKLY' },
  { label: 'Twice monthly', value: 'SEMIMONTHLY' },
  { label: 'Monthly', value: 'MONTHLY' },
];

export function payCadenceLabel(cadence: PayCadence) {
  return PAY_CADENCE_OPTIONS.find(({ value }) => value === cadence)?.label ?? cadence;
}

export function payPeriodsPerYear(cadence: PayCadence) {
  return {
    WEEKLY: 52,
    BIWEEKLY: 26,
    SEMIMONTHLY: 24,
    MONTHLY: 12,
  }[cadence];
}

export function monthlyDueDateForPeriod(
  dueDay: number,
  payPeriodStart: string,
  payPeriodEnd: string
) {
  const startDate = localDate(payPeriodStart);
  const endDate = localDate(payPeriodEnd);
  let dueDate = safeDate(startDate.getUTCFullYear(), startDate.getUTCMonth(), dueDay);

  if (dueDate < startDate && startDate.getUTCMonth() !== endDate.getUTCMonth()) {
    dueDate = safeDate(endDate.getUTCFullYear(), endDate.getUTCMonth(), dueDay);
  }

  return toIsoDate(dueDate);
}

export function annualDueDateForPeriod(
  month: number,
  day: number,
  payPeriodStart: string,
  payPeriodEnd: string
) {
  const startDate = localDate(payPeriodStart);
  const endDate = localDate(payPeriodEnd);
  let dueDate = safeDate(startDate.getUTCFullYear(), month - 1, day);

  if (dueDate < startDate && startDate.getUTCFullYear() !== endDate.getUTCFullYear()) {
    dueDate = safeDate(endDate.getUTCFullYear(), month - 1, day);
  }

  return toIsoDate(dueDate);
}

export function nextPayPeriod(payPeriodStart: string, payPeriodEnd: string): PayPeriod {
  if (!payPeriodStart || !payPeriodEnd) {
    return { end: payPeriodEnd, start: payPeriodStart };
  }

  const start = localDate(payPeriodStart);
  const end = localDate(payPeriodEnd);
  const periodDays = Math.round((end.getTime() - start.getTime()) / 86_400_000) + 1;
  const nextStart = addDays(end, 1);
  const nextEnd = addDays(nextStart, periodDays - 1);

  return { end: toIsoDate(nextEnd), start: toIsoDate(nextStart) };
}

export function annualInputDate(month: number, day: number) {
  const year = todayIso().slice(0, 4);
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

export function annualDateLabel(value: string) {
  return localDate(value).toLocaleDateString('en-US', {
    month: '2-digit',
    day: '2-digit',
    year: 'numeric',
    timeZone: 'UTC',
  });
}

export function parseAnnualDate(value: string) {
  if (value.includes('-')) {
    const [, month = 1, day = 1] = value.split('-').map(Number);
    return { day, month };
  }

  const [month = 1, day = 1] = value.split('/').map(Number);
  return { day, month };
}

export function todayIso(timeZone = browserPlanningTimeZone()) {
  const parts = new Intl.DateTimeFormat('en-US', {
    day: '2-digit',
    month: '2-digit',
    timeZone,
    year: 'numeric',
  }).formatToParts(new Date());
  const value = Object.fromEntries(parts.map(({ type, value: partValue }) => [type, partValue]));
  return `${value.year}-${value.month}-${value.day}`;
}

export function addDaysIso(value: string, days: number) {
  return toIsoDate(addDays(localDate(value), days));
}

export function dateInMonthIso(year: number, monthIndex: number, day: number) {
  return toIsoDate(safeDate(year, monthIndex, day));
}

export function payPeriodEndForCadence(startDate: string, cadence: PayCadence) {
  if (cadence === 'WEEKLY') {
    return addDaysIso(startDate, 6);
  }
  if (cadence === 'BIWEEKLY') {
    return addDaysIso(startDate, 13);
  }

  const start = localDate(startDate);
  const year = start.getUTCFullYear();
  const month = start.getUTCMonth();
  if (cadence === 'SEMIMONTHLY' && start.getUTCDate() <= 15) {
    return dateInMonthIso(year, month, 15);
  }
  return dateInMonthIso(year, month, 31);
}

export function browserPlanningTimeZone() {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

export function supportedPlanningTimeZones() {
  const intlWithSupportedValues = Intl as typeof Intl & {
    supportedValuesOf?: (key: 'timeZone') => string[];
  };
  const browserTimeZone = browserPlanningTimeZone();
  const timeZones = intlWithSupportedValues.supportedValuesOf?.('timeZone') ?? [];
  return [...new Set(['UTC', browserTimeZone, ...timeZones])].sort((left, right) =>
    left.localeCompare(right)
  );
}

function localDate(value: string) {
  return new Date(`${value}T00:00:00Z`);
}

function safeDate(year: number, monthIndex: number, day: number) {
  const daysInMonth = new Date(Date.UTC(year, monthIndex + 1, 0)).getUTCDate();
  return new Date(Date.UTC(year, monthIndex, Math.min(day, daysInMonth)));
}

function addDays(date: Date, days: number) {
  const nextDate = new Date(date);
  nextDate.setUTCDate(nextDate.getUTCDate() + days);
  return nextDate;
}

function toIsoDate(date: Date) {
  return date.toISOString().slice(0, 10);
}
