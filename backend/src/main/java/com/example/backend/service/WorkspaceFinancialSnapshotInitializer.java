package com.example.backend.service;

import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.dto.financials.PayPeriodRequest;

@FunctionalInterface
public interface WorkspaceFinancialSnapshotInitializer {

  FinancialSnapshot initialize(PayPeriodRequest payPeriod);
}
