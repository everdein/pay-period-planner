import { currency, formatDate } from './financialsFormatters';
import type { ProjectionPeriod, ProjectionSummary } from './financialsTypes';

export function ProjectionTab({ projection }: { projection: ProjectionSummary }) {
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
            Estimate what will be left after next period bills and rent set-asides, then show what
            can go to credit card debt or Apple HYSA.
          </p>
        </div>
      </section>

      <section aria-label="Projection summary" className="summary-grid projection-summary">
        <div
          className={
            projection.nextPayPeriodCashAfterBills >= 0 ? 'metric-card good' : 'metric-card bad'
          }
        >
          <span>Cash after bills</span>
          <strong>{currency.format(projection.nextPayPeriodCashAfterBills)}</strong>
          <small>Next period after bills and rent</small>
        </div>
        <div
          className={
            projection.nextPayPeriodDebtRemaining > 0 ? 'metric-card bad' : 'metric-card good'
          }
        >
          <span>Debt left after payment</span>
          <strong>{currency.format(projection.nextPayPeriodDebtRemaining)}</strong>
          <small>After next period payment</small>
        </div>
        <div
          className={
            projection.nextPayPeriodHysaTransfer > 0 ? 'metric-card good' : 'metric-card neutral'
          }
        >
          <span>Possible HYSA transfer</span>
          <strong>{currency.format(projection.nextPayPeriodHysaTransfer)}</strong>
          <small>Only after debt is covered</small>
        </div>
      </section>

      <section
        className={
          projection.remainingDebtAfterProjectedCash > 0
            ? 'debt-progress-card bad'
            : 'debt-progress-card good'
        }
        aria-label="Projected debt payoff progress"
      >
        <div>
          <span>How much current debt the next paycheck could cover</span>
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
        <div className="good">
          <dt>Paycheck income</dt>
          <dd>{currency.format(period.paycheckIncome)}</dd>
        </div>
        <div className="caution">
          <dt>Bills due</dt>
          <dd>-{currency.format(period.monthlyWithdrawalsDue)}</dd>
        </div>
        <div className="caution">
          <dt>Annual bills due</dt>
          <dd>-{currency.format(period.annualWithdrawalsDue)}</dd>
        </div>
        <div className="caution">
          <dt>Rent set aside</dt>
          <dd>-{currency.format(period.rentContribution)}</dd>
        </div>
      </dl>

      <div className="table-wrap">
        <table>
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
            <tr className="projection-row caution">
              <td>Annual bills due</td>
              <td className="amount">-{currency.format(period.annualWithdrawalsDue)}</td>
            </tr>
            <tr className="projection-row good">
              <td>Rent paid from savings</td>
              <td className="amount">{currency.format(period.rentCoveredBySavings)}</td>
            </tr>
            <tr className="projection-row caution">
              <td>Rent set aside</td>
              <td className="amount">-{currency.format(period.rentContribution)}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
  );
}
