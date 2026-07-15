package com.example.backend.service;

import com.example.backend.dto.financials.ExpenseSnapshotResponse;
import com.example.backend.dto.financials.FinancialAuditHistoryResponse;
import com.example.backend.dto.financials.FinancialSnapshotBackup;

public interface FinancialWorkspaceQueries {

  ExpenseSnapshotResponse getSnapshot();

  FinancialAuditHistoryResponse getAuditHistory(int limit);

  FinancialSnapshotBackup exportSnapshot();
}
