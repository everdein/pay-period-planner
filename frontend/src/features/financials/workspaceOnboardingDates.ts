import type { PayCadence, PayPeriodRequest } from '../../api/endpoints/financials';
import { browserPlanningTimeZone, payPeriodEndForCadence, todayIso } from './financialsDatePolicy';

export function defaultPayPeriod(
  startDate?: string,
  payCadence: PayCadence = 'BIWEEKLY',
  timeZone = browserPlanningTimeZone()
): PayPeriodRequest {
  const resolvedStartDate = startDate ?? todayIso(timeZone);
  return {
    startDate: resolvedStartDate,
    endDate: payPeriodEndForCadence(resolvedStartDate, payCadence),
    planningSettings: { payCadence, timeZone },
  };
}
