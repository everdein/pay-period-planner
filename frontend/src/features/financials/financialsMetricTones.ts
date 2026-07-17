export type FinancialMetricTone = 'bad' | 'caution' | 'good' | 'neutral';

export function valueTone(value: number): FinancialMetricTone {
  if (value > 0) {
    return 'good';
  }
  if (value < 0) {
    return 'bad';
  }
  return 'neutral';
}

export function expenseTone(value: number): FinancialMetricTone {
  return value > 0 ? 'caution' : 'neutral';
}

export function debtTone(value: number): FinancialMetricTone {
  return value > 0 ? 'bad' : 'neutral';
}

export function debtOutcomeTone(currentDebt: number, remainingDebt: number): FinancialMetricTone {
  if (currentDebt <= 0) {
    return 'neutral';
  }
  return remainingDebt > 0 ? 'bad' : 'good';
}
