package com.example.backend.service;

import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.dto.financials.PayPeriodRequest;
import com.example.backend.repository.WorkspaceFinancialSnapshotStore;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceFinancialSnapshotInitializationService
    implements WorkspaceFinancialSnapshotInitializer {

  private final WorkspaceFinancialSnapshotStore snapshotStore;
  private final AuthenticatedWorkspaceResolver workspaceResolver;
  private final HttpServletRequest request;

  public WorkspaceFinancialSnapshotInitializationService(
      WorkspaceFinancialSnapshotStore snapshotStore,
      AuthenticatedWorkspaceResolver workspaceResolver,
      HttpServletRequest request) {
    this.snapshotStore = snapshotStore;
    this.workspaceResolver = workspaceResolver;
    this.request = request;
  }

  @Override
  @Transactional
  public void initialize(PayPeriodRequest payPeriod) {
    if (payPeriod.startDate() == null || payPeriod.endDate() == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Pay period start and end dates are required");
    }
    if (payPeriod.endDate().isBefore(payPeriod.startDate())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Pay period end date must be on or after start date");
    }

    long workspaceId = workspaceResolver.requireWorkspaceId(request);
    if (snapshotStore.loadActiveSnapshot(workspaceId).isPresent()) {
      throw conflict();
    }

    FinancialSnapshot initialSnapshot =
        new FinancialSnapshot(
            1,
            payPeriod.startDate(),
            payPeriod.endDate(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    try {
      snapshotStore.createInitialSnapshot(workspaceId, initialSnapshot);
    } catch (DuplicateKeyException exception) {
      throw conflict();
    }
  }

  private WorkspaceFinancialSnapshotConflictException conflict() {
    return new WorkspaceFinancialSnapshotConflictException(
        "This workspace already has an active financial snapshot");
  }
}
