import { type FormEvent, useState } from 'react';

import type { PayPeriodRequest } from '../../api/endpoints/financials';
import {
  PAY_CADENCE_OPTIONS,
  payPeriodEndForCadence,
  supportedPlanningTimeZones,
} from './financialsDatePolicy';
import { defaultPayPeriod } from './workspaceOnboardingDates';

const planningTimeZones = supportedPlanningTimeZones();

export function WorkspaceOnboarding({
  error,
  initializing,
  onInitialize,
  workspaceName,
}: {
  error: { message: string; requestId?: string } | null;
  initializing: boolean;
  onInitialize: (payPeriod: PayPeriodRequest) => Promise<void>;
  workspaceName: string;
}) {
  const [payPeriod, setPayPeriod] = useState<PayPeriodRequest>(() => defaultPayPeriod());

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void onInitialize(payPeriod);
  }

  return (
    <section className="workspace-onboarding" aria-labelledby="workspace-onboarding-heading">
      <div className="onboarding-copy">
        <p className="eyebrow">{workspaceName}</p>
        <h2 id="workspace-onboarding-heading">Start your planning workspace</h2>
        <p>Set the current pay period to create an empty snapshot.</p>
        <p className="onboarding-note">No sample or personal values will be added.</p>
      </div>

      <form className="onboarding-form" onSubmit={submit}>
        <div className="onboarding-dates">
          <label>
            Pay period starts
            <input
              disabled={initializing}
              onChange={(event) =>
                setPayPeriod((current) => ({
                  ...current,
                  endDate: payPeriodEndForCadence(
                    event.target.value,
                    current.planningSettings.payCadence
                  ),
                  startDate: event.target.value,
                }))
              }
              required
              type="date"
              value={payPeriod.startDate}
            />
          </label>
          <label>
            Pay period ends
            <input
              disabled={initializing}
              min={payPeriod.startDate}
              onChange={(event) =>
                setPayPeriod((current) => ({ ...current, endDate: event.target.value }))
              }
              required
              type="date"
              value={payPeriod.endDate}
            />
          </label>
          <label>
            Pay cadence
            <select
              disabled={initializing}
              onChange={(event) => {
                const payCadence = event.target
                  .value as PayPeriodRequest['planningSettings']['payCadence'];
                setPayPeriod((current) => ({
                  ...current,
                  endDate: payPeriodEndForCadence(current.startDate, payCadence),
                  planningSettings: { ...current.planningSettings, payCadence },
                }));
              }}
              value={payPeriod.planningSettings.payCadence}
            >
              {PAY_CADENCE_OPTIONS.map(({ label, value }) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label>
            Planning time zone
            <select
              disabled={initializing}
              onChange={(event) =>
                setPayPeriod((current) => ({
                  ...current,
                  planningSettings: {
                    ...current.planningSettings,
                    timeZone: event.target.value,
                  },
                }))
              }
              value={payPeriod.planningSettings.timeZone}
            >
              {planningTimeZones.map((timeZone) => (
                <option key={timeZone} value={timeZone}>
                  {timeZone}
                </option>
              ))}
            </select>
          </label>
        </div>
        {error && (
          <p className="error" role="alert">
            {error.message}
            {error.requestId && <small>Request reference: {error.requestId}</small>}
          </p>
        )}
        <button disabled={initializing} type="submit">
          {initializing ? 'Creating Workspace...' : 'Create Financial Snapshot'}
        </button>
      </form>
    </section>
  );
}
