package com.example.backend.service;

import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.dto.financials.ExpenseSnapshotResponse;

@FunctionalInterface
public interface FinancialSnapshotPresenter {

  ExpenseSnapshotResponse present(FinancialSnapshot snapshot);
}
