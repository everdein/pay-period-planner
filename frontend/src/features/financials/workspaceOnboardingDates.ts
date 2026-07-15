import type { PayPeriodRequest } from '../../api/endpoints/financials';
import { addDaysIso, todayIso } from './financialsDatePolicy';

export function defaultPayPeriod(startDate = todayIso()): PayPeriodRequest {
  return { startDate, endDate: addDaysIso(startDate, 13) };
}
