package com.example.backend.service;

import com.example.backend.dto.financials.ExpenseSnapshotRequest;
import com.example.backend.dto.financials.ExpenseSnapshotResponse;
import com.example.backend.dto.financials.FinancialSnapshotBackup;

public interface FinancialWorkspaceCommands {

  ExpenseSnapshotResponse saveSnapshot(ExpenseSnapshotRequest request);

  ExpenseSnapshotResponse restoreSnapshot(long expectedVersion, FinancialSnapshotBackup backup);
}
