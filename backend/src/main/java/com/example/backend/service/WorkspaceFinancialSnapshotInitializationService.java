package com.example.backend.service;

import com.example.backend.domain.financials.FinancialPlanningSettings;
import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.dto.financials.PayPeriodRequest;
import com.example.backend.repository.WorkspaceFinancialSnapshotStore;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceFinancialSnapshotInitializationService
    implements WorkspaceFinancialSnapshotInitializer {

  private final WorkspaceFinancialSnapshotStore snapshotStore;
  private final CurrentWorkspace currentWorkspace;
  private final FinancialSnapshotNormalizer normalizer;

  @Autowired
  public WorkspaceFinancialSnapshotInitializationService(
      WorkspaceFinancialSnapshotStore snapshotStore,
      CurrentWorkspace currentWorkspace,
      FinancialSnapshotNormalizer normalizer) {
    this.snapshotStore = snapshotStore;
    this.currentWorkspace = currentWorkspace;
    this.normalizer = normalizer;
  }

  WorkspaceFinancialSnapshotInitializationService(
      WorkspaceFinancialSnapshotStore snapshotStore, CurrentWorkspace currentWorkspace) {
    this(snapshotStore, currentWorkspace, new FinancialSnapshotNormalizer());
  }

  @Override
  @Transactional
  public FinancialSnapshot initialize(PayPeriodRequest payPeriod) {
    if (payPeriod.startDate() == null || payPeriod.endDate() == null) {
      throw new FinancialRequestException("Pay period start and end dates are required");
    }
    if (payPeriod.endDate().isBefore(payPeriod.startDate())) {
      throw new FinancialRequestException("Pay period end date must be on or after start date");
    }

    long workspaceId = currentWorkspace.requireWorkspaceId();
    if (snapshotStore.loadActiveSnapshot(workspaceId).isPresent()) {
      throw conflict();
    }

    FinancialSnapshot initialSnapshot =
        normalizer.normalize(
            new FinancialSnapshot(
                1,
                payPeriod.startDate(),
                payPeriod.endDate(),
                planningSettings(payPeriod),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    try {
      snapshotStore.createInitialSnapshot(workspaceId, initialSnapshot);
    } catch (DuplicateKeyException exception) {
      throw conflict();
    }
    return initialSnapshot;
  }

  private FinancialPlanningSettings planningSettings(PayPeriodRequest payPeriod) {
    if (payPeriod.planningSettings() == null) {
      return null;
    }

    try {
      return FinancialPlanningSettings.from(
          payPeriod.planningSettings().payCadence(), payPeriod.planningSettings().timeZone());
    } catch (IllegalArgumentException exception) {
      throw new FinancialRequestException(exception.getMessage());
    }
  }

  private WorkspaceFinancialSnapshotConflictException conflict() {
    return new WorkspaceFinancialSnapshotConflictException(
        "This workspace already has an active financial snapshot");
  }
}
