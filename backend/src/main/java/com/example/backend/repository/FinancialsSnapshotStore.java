package com.example.backend.repository;

import com.example.backend.domain.financials.FinancialAuditEvent;
import com.example.backend.domain.financials.FinancialSnapshot;
import java.util.List;

public interface FinancialsSnapshotStore {

  FinancialSnapshot loadCurrentSnapshot();

  List<FinancialAuditEvent> loadAuditHistory(int limit);

  void replaceSnapshot(
      long expectedVersion, FinancialSnapshot snapshot, FinancialAuditEvent auditEvent);
}
