export type PayPeriod = {
  end: string;
  start: string;
};

export function monthlyDueDateForPeriod(
  dueDay: number,
  payPeriodStart: string,
  payPeriodEnd: string
) {
  const startDate = localDate(payPeriodStart);
  const endDate = localDate(payPeriodEnd);
  let dueDate = safeDate(startDate.getFullYear(), startDate.getMonth(), dueDay);

  if (dueDate < startDate && startDate.getMonth() !== endDate.getMonth()) {
    dueDate = safeDate(endDate.getFullYear(), endDate.getMonth(), dueDay);
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
  let dueDate = safeDate(startDate.getFullYear(), month - 1, day);

  if (dueDate < startDate && startDate.getFullYear() !== endDate.getFullYear()) {
    dueDate = safeDate(endDate.getFullYear(), month - 1, day);
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
  const year = new Date().getFullYear();
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

export function annualDateLabel(value: string) {
  return localDate(value).toLocaleDateString('en-US', {
    month: '2-digit',
    day: '2-digit',
    year: 'numeric',
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

export function todayIso() {
  return toIsoDate(new Date());
}

export function addDaysIso(value: string, days: number) {
  return toIsoDate(addDays(localDate(value), days));
}

function localDate(value: string) {
  return new Date(`${value}T00:00:00`);
}

function safeDate(year: number, monthIndex: number, day: number) {
  const daysInMonth = new Date(year, monthIndex + 1, 0).getDate();
  return new Date(year, monthIndex, Math.min(day, daysInMonth));
}

function addDays(date: Date, days: number) {
  const nextDate = new Date(date);
  nextDate.setDate(nextDate.getDate() + days);
  return nextDate;
}

function toIsoDate(date: Date) {
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 10);
}
