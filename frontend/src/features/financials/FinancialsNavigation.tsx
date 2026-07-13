import type { FinancialTab } from './financialsTypes';

const navigationSections: Array<{
  items: Array<[FinancialTab, string]>;
  label: string;
}> = [
  {
    label: 'Snapshot',
    items: [
      ['overview', 'Overview'],
      ['projection', 'Projection'],
    ],
  },
  {
    label: 'Cash Flow',
    items: [
      ['monthly-withdrawals', 'Monthly Withdrawals'],
      ['annual-withdrawals', 'Annual Withdrawals'],
      ['income-summary', 'Income Summary'],
      ['income-calendar', 'Income Calendar'],
    ],
  },
  {
    label: 'Balance Sheet',
    items: [
      ['retirement', 'Retirement'],
      ['investments', 'Investments'],
      ['cash-savings', 'Cash & Savings'],
      ['insurance-benefits', 'Insurance / Benefits'],
      ['debt', 'Debt'],
    ],
  },
  {
    label: 'Calendar',
    items: [['important-dates', 'Important Dates']],
  },
];

export function FinancialsNavigation({
  activeTab,
  onChange,
}: {
  activeTab: FinancialTab;
  onChange: (tab: FinancialTab) => void;
}) {
  return (
    <aside aria-label="Financial sections" className="sidebar">
      {navigationSections.map((section) => (
        <div className="sidebar-section" key={section.label}>
          <p>{section.label}</p>
          <nav>
            {section.items.map(([tab, label]) => (
              <button
                aria-current={activeTab === tab ? 'page' : undefined}
                className={activeTab === tab ? 'active' : undefined}
                key={tab}
                onClick={() => onChange(tab)}
                type="button"
              >
                {label}
              </button>
            ))}
          </nav>
        </div>
      ))}
    </aside>
  );
}
