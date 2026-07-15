package com.example.backend.service;

import com.example.backend.dto.financials.PayPeriodRequest;

@FunctionalInterface
public interface WorkspaceFinancialSnapshotInitializer {

  void initialize(PayPeriodRequest payPeriod);
}
