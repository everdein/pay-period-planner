import type { FinancialProjectionRoles } from '../../api/endpoints/financials';
import { nextPayPeriod } from './financialsDatePolicy';
import { toDraftAnnualWithdrawal, toDraftBill } from './financialsDraft';
import type {
  DraftAnnualWithdrawal,
  DraftAssetCategory,
  DraftBill,
  ProjectionPeriod,
  ProjectionSummary,
} from './financialsTypes';

type ProjectionInput = {
  annualWithdrawals: DraftAnnualWithdrawal[];
  annualWithdrawalsInPayPeriod: DraftAnnualWithdrawal[];
  assetCategories: DraftAssetCategory[];
  paycheckIncome: number;
  payPeriodEnd: string;
  payPeriodStart: string;
  projectionRoles: FinancialProjectionRoles;
  sortedBills: DraftBill[];
  totalDebt: number;
};

export function buildProjectionSummary({
  annualWithdrawals,
  annualWithdrawalsInPayPeriod,
  assetCategories,
  paycheckIncome,
  payPeriodEnd,
  payPeriodStart,
  projectionRoles,
  sortedBills,
  totalDebt,
}: ProjectionInput): ProjectionSummary {
  const rentBill = sortedBills.find(({ id }) => id === projectionRoles.rentBillId);
  const rentSavingsBalance =
    assetCategories
      .flatMap((category) => category.accounts)
      .find(({ id }) => id === projectionRoles.rentReserveAssetAccountId)?.amount ?? 0;
  const currentPeriod = buildProjectionPeriod(
    'Current Pay Period',
    payPeriodStart,
    payPeriodEnd,
    sortedBills,
    annualWithdrawalsInPayPeriod,
    paycheckIncome,
    rentBill,
    rentSavingsBalance
  );
  const nextPeriodDates = nextPayPeriod(payPeriodStart, payPeriodEnd);
  const nextBills = sortedBills.map((bill) =>
    toDraftBill(bill, nextPeriodDates.start, nextPeriodDates.end)
  );
  const nextAnnualWithdrawals = annualWithdrawals.map((withdrawal) =>
    toDraftAnnualWithdrawal(withdrawal, nextPeriodDates.start, nextPeriodDates.end)
  );
  const nextPeriod = buildProjectionPeriod(
    'Next Pay Period',
    nextPeriodDates.start,
    nextPeriodDates.end,
    nextBills,
    nextAnnualWithdrawals.filter((withdrawal) => withdrawal.inPayPeriod),
    paycheckIncome,
    rentBill,
    currentPeriod.endingRentSavings
  );
  const periods = [currentPeriod.period, nextPeriod.period];
  const nextPayPeriodCashAfterBills = nextPeriod.period.projectedBeforeDebt;
  const nextPayPeriodDebtPayment = Math.min(Math.max(nextPayPeriodCashAfterBills, 0), totalDebt);
  const nextPayPeriodDebtRemaining = Math.max(totalDebt - nextPayPeriodDebtPayment, 0);
  const nextPayPeriodSavingsTransfer = Math.max(nextPayPeriodCashAfterBills - totalDebt, 0);

  return {
    currentDebt: totalDebt,
    debtCoveredByProjectedCash: nextPayPeriodDebtPayment,
    debtCoveragePercent: totalDebt === 0 ? 100 : (nextPayPeriodDebtPayment / totalDebt) * 100,
    nextPayPeriodCashAfterBills,
    nextPayPeriodDebtPayment,
    nextPayPeriodDebtRemaining,
    nextPayPeriodSavingsTransfer,
    projectedAfterDebt: nextPayPeriodCashAfterBills - totalDebt,
    projectedBeforeDebt: nextPayPeriodCashAfterBills,
    remainingDebtAfterProjectedCash: nextPayPeriodDebtRemaining,
    periods,
  };
}

export function buildProjectionPeriod(
  title: string,
  payPeriodStart: string,
  payPeriodEnd: string,
  bills: DraftBill[],
  annualWithdrawals: DraftAnnualWithdrawal[],
  paycheckIncome: number,
  rentBill: DraftBill | undefined,
  startingRentSavings: number
): { endingRentSavings: number; period: ProjectionPeriod } {
  const rentBillAmount = rentBill?.amount ?? 0;
  const rentDueInPeriod = bills.some((bill) => bill.id === rentBill?.id && bill.inPayPeriod)
    ? rentBillAmount
    : 0;
  const rentCoveredBySavings = Math.min(startingRentSavings, rentDueInPeriod);
  const rentSavingsAfterDue = Math.max(startingRentSavings - rentDueInPeriod, 0);
  const rentRemainingNeed = Math.max(rentBillAmount - rentSavingsAfterDue, 0);
  const rentContribution = Math.min(rentBillAmount / 2, rentRemainingNeed);
  const projectedBills = bills.filter((bill) => bill.inPayPeriod && bill.id !== rentBill?.id);
  const monthlyWithdrawalsDue = projectedBills.reduce((total, bill) => total + bill.amount, 0);
  const annualWithdrawalsDue = annualWithdrawals.reduce(
    (total, withdrawal) => total + withdrawal.amount,
    0
  );
  const projectedBeforeDebt =
    paycheckIncome - monthlyWithdrawalsDue - annualWithdrawalsDue - rentContribution;

  return {
    endingRentSavings: rentSavingsAfterDue + rentContribution,
    period: {
      annualWithdrawalsDue,
      monthlyWithdrawalsDue,
      paycheckIncome,
      payPeriodEnd,
      payPeriodStart,
      projectedBeforeDebt,
      rentBillAmount,
      rentContribution,
      rentCoveredBySavings,
      rentRemainingNeed,
      rentSavingsBalance: startingRentSavings,
      title,
      withdrawalLines: projectedBills.map((bill) => ({
        amount: bill.amount,
        label: bill.bill,
      })),
    },
  };
}
