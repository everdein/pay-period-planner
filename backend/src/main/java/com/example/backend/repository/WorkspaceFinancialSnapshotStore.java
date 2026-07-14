package com.example.backend.repository;

import com.example.backend.domain.financials.FinancialSnapshot;
import java.util.Optional;

public interface WorkspaceFinancialSnapshotStore {

  Optional<FinancialSnapshot> loadActiveSnapshot(long workspaceId);

  long createInitialSnapshot(long workspaceId, FinancialSnapshot snapshot);

  boolean deactivateSnapshotIfUnchanged(long workspaceId, long snapshotId, long expectedVersion);
}
