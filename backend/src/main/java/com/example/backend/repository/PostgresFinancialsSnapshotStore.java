package com.example.backend.repository;

import com.example.backend.domain.financials.FinancialAuditEvent;
import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.service.CurrentWorkspace;
import com.example.backend.service.WorkspaceFinancialSnapshotNotFoundException;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresFinancialsSnapshotStore implements FinancialsSnapshotStore {

  private final PostgresFinancialRecordSnapshotAdapter snapshotAdapter;
  private final CurrentWorkspace currentWorkspace;

  public PostgresFinancialsSnapshotStore(
      PostgresFinancialRecordSnapshotAdapter snapshotAdapter, CurrentWorkspace currentWorkspace) {
    this.snapshotAdapter = snapshotAdapter;
    this.currentWorkspace = currentWorkspace;
  }

  @Override
  public FinancialSnapshot loadCurrentSnapshot() {
    long workspaceId = currentWorkspace.requireWorkspaceId();
    return snapshotAdapter
        .loadActiveSnapshot(workspaceId)
        .orElseThrow(() -> new WorkspaceFinancialSnapshotNotFoundException(workspaceId));
  }

  @Override
  public List<FinancialAuditEvent> loadAuditHistory(int limit) {
    long workspaceId = currentWorkspace.requireWorkspaceId();
    return snapshotAdapter.loadAuditEvents(workspaceId, limit);
  }

  @Override
  public void replaceSnapshot(
      long expectedVersion, FinancialSnapshot snapshot, FinancialAuditEvent auditEvent) {
    long workspaceId = currentWorkspace.requireWorkspaceId();
    snapshotAdapter.replaceActiveSnapshot(workspaceId, expectedVersion, snapshot, auditEvent);
  }
}
