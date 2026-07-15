import { type FormEvent, useState } from 'react';

import type { PayPeriodRequest } from '../../api/endpoints/financials';
import { defaultPayPeriod } from './workspaceOnboardingDates';

export function WorkspaceOnboarding({
  error,
  initializing,
  onInitialize,
  workspaceName,
}: {
  error: string | null;
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
        <h2 id="workspace-onboarding-heading">Start your financial workspace</h2>
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
                setPayPeriod((current) => ({ ...current, startDate: event.target.value }))
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
        </div>
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <button disabled={initializing} type="submit">
          {initializing ? 'Creating Workspace...' : 'Create Financial Snapshot'}
        </button>
      </form>
    </section>
  );
}
