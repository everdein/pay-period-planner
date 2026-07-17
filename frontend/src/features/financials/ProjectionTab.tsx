import type {
  FinancialPlanningSettings,
  FinancialProjectionRoles,
  PayCadence,
} from '../../api/endpoints/financials';
import { PAY_CADENCE_OPTIONS, supportedPlanningTimeZones } from './financialsDatePolicy';
import { currency, formatDate } from './financialsFormatters';
import { debtOutcomeTone, expenseTone, valueTone } from './financialsMetricTones';
import type {
  DraftAssetAccount,
  DraftBill,
  DraftIncomeSummaryItem,
  ProjectionPeriod,
  ProjectionSummary,
} from './financialsTypes';
import { ScrollableTableRegion } from './ScrollableTableRegion';

type ProjectionSettings = {
  assetAccounts: Array<DraftAssetAccount & { categoryLabel: string }>;
  bills: DraftBill[];
  incomeSummaryItems: DraftIncomeSummaryItem[];
  planningSettings: FinancialPlanningSettings | null;
  roles: FinancialProjectionRoles | null;
  updatePlanningSetting: (setting: keyof FinancialPlanningSettings, value: string) => void;
  updateProjectionRole: (role: keyof FinancialProjectionRoles, recordId: number) => void;
};

const planningTimeZones = supportedPlanningTimeZones();

export function ProjectionTab({
  projection,
  settings,
}: {
  projection: ProjectionSummary;
  settings: ProjectionSettings;
}) {
  const currentPeriod = projection.periods[0];
  const nextPeriod = projection.periods[1] ?? projection.periods[0];

  if (!nextPeriod) {
    return null;
  }

  return (
    <>
      <section className="section-header">
        <div>
          <h2>Next Paycheck Projection</h2>
          <p>
            Estimate cash after next-period bills and housing set-asides, then compare a possible
            debt payment with a possible savings transfer. This household planning estimate is not
            accounting, transaction reconciliation, or financial advice.
          </p>
        </div>
      </section>

      {settings.planningSettings && (
        <section aria-labelledby="planning-settings-heading" className="projection-role-settings">
          <h3 id="planning-settings-heading">Planning Schedule</h3>
          <div className="projection-role-grid">
            <label>
              Pay cadence
              <select
                onChange={(event) =>
                  settings.updatePlanningSetting('payCadence', event.target.value as PayCadence)
                }
                value={settings.planningSettings.payCadence}
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
                onChange={(event) => settings.updatePlanningSetting('timeZone', event.target.value)}
                value={settings.planningSettings.timeZone}
              >
                {planningTimeZones.map((timeZone) => (
                  <option key={timeZone} value={timeZone}>
                    {timeZone}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </section>
      )}

      {settings.roles && (
        <section aria-labelledby="projection-inputs-heading" className="projection-role-settings">
          <h3 id="projection-inputs-heading">Projection Inputs</h3>
          <div className="projection-role-grid">
            <label>
              Housing payment
              <select
                onChange={(event) =>
                  settings.updateProjectionRole('rentBillId', Number(event.target.value))
                }
                value={settings.roles.rentBillId}
              >
                {settings.bills.map((bill) => (
                  <option key={bill.id} value={bill.id}>
                    {bill.bill}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Housing reserve
              <select
                onChange={(event) =>
                  settings.updateProjectionRole(
                    'rentReserveAssetAccountId',
                    Number(event.target.value)
                  )
                }
                value={settings.roles.rentReserveAssetAccountId}
              >
                {settings.assetAccounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.account} ({account.categoryLabel})
                  </option>
                ))}
              </select>
            </label>
            <label>
              Primary paycheck
              <select
                onChange={(event) =>
                  settings.updateProjectionRole(
                    'primaryPaycheckIncomeSummaryItemId',
                    Number(event.target.value)
                  )
                }
                value={settings.roles.primaryPaycheckIncomeSummaryItemId}
              >
                {settings.incomeSummaryItems.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.category} / {item.interval}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </section>
      )}

      <section aria-label="Projection summary" className="summary-grid projection-summary">
        <div className={`metric-card ${valueTone(projection.nextPayPeriodCashAfterBills)}`}>
          <span>Cash after bills</span>
          <strong>{currency.format(projection.nextPayPeriodCashAfterBills)}</strong>
          <small>Next period after bills and housing</small>
        </div>
        <div
          className={`metric-card ${debtOutcomeTone(
            projection.currentDebt,
            projection.nextPayPeriodDebtRemaining
          )}`}
        >
          <span>Debt left after plan</span>
          <strong>{currency.format(projection.nextPayPeriodDebtRemaining)}</strong>
          <small>After the possible debt payment</small>
        </div>
        <div className={`metric-card ${valueTone(projection.nextPayPeriodSavingsTransfer)}`}>
          <span>Possible savings transfer</span>
          <strong>{currency.format(projection.nextPayPeriodSavingsTransfer)}</strong>
          <small>After the planned debt balance reaches zero</small>
        </div>
      </section>

      <section
        className={`debt-progress-card ${debtOutcomeTone(
          projection.currentDebt,
          projection.remainingDebtAfterProjectedCash
        )}`}
        aria-label="Projected debt payoff progress"
      >
        <div>
          <span>How much current debt next-period cash could cover</span>
          <strong>
            {currency.format(projection.nextPayPeriodDebtPayment)} of{' '}
            {currency.format(projection.currentDebt)}
          </strong>
        </div>
        <div className="debt-progress-track">
          <div
            className="debt-progress-fill"
            style={{ width: `${Math.min(projection.debtCoveragePercent, 100)}%` }}
          />
        </div>
      </section>

      <section className="projection-focus">
        <ProjectionPeriodCard period={nextPeriod} />
        {currentPeriod && <CurrentPeriodContext period={currentPeriod} />}
      </section>
    </>
  );
}

function CurrentPeriodContext({ period }: { period: ProjectionPeriod }) {
  return (
    <aside className="current-period-context">
      <h3>Current Period Context</h3>
      <p>
        {formatDate(period.payPeriodStart)} to {formatDate(period.payPeriodEnd)}
      </p>
      <dl>
        <div>
          <dt>Estimated left before debt</dt>
          <dd>{currency.format(period.projectedBeforeDebt)}</dd>
        </div>
      </dl>
      <small>
        This is shown for context only. The main projection focuses on the next paycheck.
      </small>
    </aside>
  );
}

function ProjectionPeriodCard({ period }: { period: ProjectionPeriod }) {
  return (
    <article className="projection-period-card">
      <header>
        <div>
          <h3>{period.title}</h3>
          <p>
            {formatDate(period.payPeriodStart)} to {formatDate(period.payPeriodEnd)}
          </p>
        </div>
        <strong>{currency.format(period.projectedBeforeDebt)}</strong>
      </header>

      <dl className="projection-metrics">
        <div className={valueTone(period.paycheckIncome)}>
          <dt>Paycheck income</dt>
          <dd>{currency.format(period.paycheckIncome)}</dd>
        </div>
        <div className={expenseTone(period.monthlyWithdrawalsDue)}>
          <dt>Bills due</dt>
          <dd>-{currency.format(period.monthlyWithdrawalsDue)}</dd>
        </div>
        <div className={expenseTone(period.annualWithdrawalsDue)}>
          <dt>Annual bills due</dt>
          <dd>-{currency.format(period.annualWithdrawalsDue)}</dd>
        </div>
        <div className={expenseTone(period.rentContribution)}>
          <dt>Housing reserve set-aside</dt>
          <dd>-{currency.format(period.rentContribution)}</dd>
        </div>
      </dl>

      <ScrollableTableRegion label="Current paycheck period details">
        <table className="projection-detail-table">
          <colgroup>
            <col className="name-column" />
            <col className="amount-column" />
          </colgroup>
          <caption>What is included in this paycheck period</caption>
          <thead>
            <tr>
              <th>Item</th>
              <th>Amount</th>
            </tr>
          </thead>
          <tbody>
            {period.withdrawalLines.length > 0 ? (
              period.withdrawalLines.map((line) => (
                <tr className="projection-row caution" key={line.label}>
                  <td>{line.label}</td>
                  <td className="amount">-{currency.format(line.amount)}</td>
                </tr>
              ))
            ) : (
              <tr className="projection-row neutral">
                <td colSpan={2}>No bills due in this period.</td>
              </tr>
            )}
            <tr className={`projection-row ${expenseTone(period.annualWithdrawalsDue)}`}>
              <td>Annual bills due</td>
              <td className="amount">-{currency.format(period.annualWithdrawalsDue)}</td>
            </tr>
            <tr className={`projection-row ${valueTone(period.rentCoveredBySavings)}`}>
              <td>Housing paid from reserve</td>
              <td className="amount">{currency.format(period.rentCoveredBySavings)}</td>
            </tr>
            <tr className={`projection-row ${expenseTone(period.rentContribution)}`}>
              <td>Housing reserve set-aside</td>
              <td className="amount">-{currency.format(period.rentContribution)}</td>
            </tr>
          </tbody>
        </table>
      </ScrollableTableRegion>
    </article>
  );
}
