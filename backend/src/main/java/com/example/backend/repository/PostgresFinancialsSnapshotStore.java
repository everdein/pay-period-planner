package com.example.backend.repository;

import com.example.backend.service.AuthenticatedWorkspaceResolver;
import com.example.backend.service.WorkspaceFinancialSnapshotNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresFinancialsSnapshotStore implements FinancialsSnapshotStore {

  private final PostgresFinancialRecordSnapshotAdapter snapshotAdapter;
  private final AuthenticatedWorkspaceResolver workspaceResolver;
  private final HttpServletRequest request;

  public PostgresFinancialsSnapshotStore(
      PostgresFinancialRecordSnapshotAdapter snapshotAdapter,
      AuthenticatedWorkspaceResolver workspaceResolver,
      HttpServletRequest request) {
    this.snapshotAdapter = snapshotAdapter;
    this.workspaceResolver = workspaceResolver;
    this.request = request;
  }

  @Override
  public FinancialsData load() {
    long workspaceId = workspaceResolver.requireWorkspaceId(request);
    return snapshotAdapter
        .loadActiveData(workspaceId)
        .orElseThrow(() -> new WorkspaceFinancialSnapshotNotFoundException(workspaceId));
  }

  @Override
  public void save(FinancialsData data) {
    long workspaceId = workspaceResolver.requireWorkspaceId(request);
    snapshotAdapter.replaceActiveData(workspaceId, data.version() - 1, data);
  }
}
