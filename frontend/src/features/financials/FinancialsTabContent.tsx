import { AnnualWithdrawalsTab } from './AnnualWithdrawalsTab';
import { AssetTable } from './AssetTable';
import { DebtTab } from './DebtTab';
import type { FinancialTab } from './financialsTypes';
import { ImportantDatesTab } from './ImportantDatesTab';
import { IncomeCalendarTab } from './IncomeCalendarTab';
import { IncomeSummaryTab } from './IncomeSummaryTab';
import { MonthlyWithdrawalsTab } from './MonthlyWithdrawalsTab';
import { Overview } from './OverviewTab';
import { ProjectionTab } from './ProjectionTab';
import type { FinancialsDraftWorkspace } from './useFinancialsDraftWorkspace';

const assetCategoryKeys: Partial<Record<FinancialTab, string>> = {
  'cash-savings': 'cash-savings',
  'insurance-benefits': 'insurance-benefits',
  investments: 'investments',
  retirement: 'retirement',
};

export function FinancialsTabContent({
  activeTab,
  workspace,
}: {
  activeTab: FinancialTab;
  workspace: FinancialsDraftWorkspace;
}) {
  switch (activeTab) {
    case 'overview':
      return <Overview {...workspace.overview} />;
    case 'projection':
      return <ProjectionTab projection={workspace.projection} />;
    case 'monthly-withdrawals':
      return <MonthlyWithdrawalsTab {...workspace.monthlyWithdrawals} />;
    case 'annual-withdrawals':
      return <AnnualWithdrawalsTab {...workspace.annualWithdrawals} />;
    case 'income-summary':
      return <IncomeSummaryTab {...workspace.incomeSummary} />;
    case 'income-calendar':
      return <IncomeCalendarTab {...workspace.incomeCalendar} />;
    case 'debt':
      return <DebtTab {...workspace.debtAccounts} />;
    case 'important-dates':
      return <ImportantDatesTab {...workspace.importantDates} />;
    default:
      return renderAssetTab(activeTab, workspace);
  }
}

function renderAssetTab(activeTab: FinancialTab, workspace: FinancialsDraftWorkspace) {
  const categoryKey = assetCategoryKeys[activeTab];
  const category = workspace.assetAccounts.assetCategories.find(
    (candidate) => candidate.key === categoryKey
  );

  if (!category) {
    return null;
  }

  const {
    assetForm,
    cancelAssetEdit,
    editingAsset,
    requestRemoveAsset,
    startAssetEdit,
    submitAsset,
    updateAssetForm,
  } = workspace.assetAccounts;

  return (
    <AssetTable
      assetForm={assetForm}
      cancelAssetEdit={cancelAssetEdit}
      category={category}
      editingAsset={editingAsset}
      requestRemoveAsset={requestRemoveAsset}
      startAssetEdit={startAssetEdit}
      submitAsset={submitAsset}
      updateAssetForm={updateAssetForm}
    />
  );
}
