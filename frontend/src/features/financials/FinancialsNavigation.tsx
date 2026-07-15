import { navigationSections } from './financialsNavigationModel';
import type { FinancialTab } from './financialsTypes';

export function FinancialsNavigation({
  activeTab,
  onChange,
}: {
  activeTab: FinancialTab;
  onChange: (tab: FinancialTab) => void;
}) {
  return (
    <>
      <aside aria-label="Financial sections" className="sidebar">
        {navigationSections.map((section) => (
          <div className="sidebar-section" key={section.label}>
            <p>{section.label}</p>
            <nav aria-label={`${section.label} sections`}>
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

      <label className="mobile-section-picker">
        <span>Financial section</span>
        <select
          onChange={(event) => onChange(event.target.value as FinancialTab)}
          value={activeTab}
        >
          {navigationSections.map((section) => (
            <optgroup key={section.label} label={section.label}>
              {section.items.map(([tab, label]) => (
                <option key={tab} value={tab}>
                  {label}
                </option>
              ))}
            </optgroup>
          ))}
        </select>
      </label>
    </>
  );
}
