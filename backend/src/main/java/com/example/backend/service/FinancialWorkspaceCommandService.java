package com.example.backend.service;

import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.dto.financials.ExpenseSnapshotRequest;
import com.example.backend.dto.financials.ExpenseSnapshotResponse;
import com.example.backend.dto.financials.FinancialSnapshotBackup;
import com.example.backend.repository.FinancialsRepository;
import com.example.backend.repository.SnapshotVersionConflictException;
import org.springframework.stereotype.Service;

@Service
public class FinancialWorkspaceCommandService implements FinancialWorkspaceCommands {

  private final FinancialsRepository financialsRepository;
  private final FinancialSnapshotRequestMapper requestMapper;
  private final FinancialWorkspaceQueries workspaceQueries;

  public FinancialWorkspaceCommandService(
      FinancialsRepository financialsRepository,
      FinancialSnapshotRequestMapper requestMapper,
      FinancialWorkspaceQueries workspaceQueries) {
    this.financialsRepository = financialsRepository;
    this.requestMapper = requestMapper;
    this.workspaceQueries = workspaceQueries;
  }

  @Override
  public ExpenseSnapshotResponse saveSnapshot(ExpenseSnapshotRequest request) {
    long expectedVersion = requestMapper.validateSnapshotVersion(request.version());
    FinancialSnapshot replacementSnapshot = requestMapper.toDomainSnapshot(request);
    try {
      financialsRepository.replaceSnapshot(expectedVersion, replacementSnapshot);
    } catch (SnapshotVersionConflictException exception) {
      throw new FinancialSnapshotVersionConflictException(
          "The financial snapshot changed after it was loaded. Reload before saving.", exception);
    }
    return workspaceQueries.getSnapshot();
  }

  @Override
  public ExpenseSnapshotResponse restoreSnapshot(
      long expectedVersion, FinancialSnapshotBackup backup) {
    if (!FinancialSnapshotBackup.FORMAT.equals(backup.format())) {
      throw new FinancialRequestException("The financial snapshot backup format is not supported");
    }

    return saveSnapshot(
        requestMapper.withVersion(
            backup.snapshot(), requestMapper.validateSnapshotVersion(expectedVersion)));
  }
}
