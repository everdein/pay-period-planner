package com.example.backend.service;

import com.example.backend.dto.financials.ExpenseSnapshotResponse;
import com.example.backend.dto.financials.FinancialAuditHistoryResponse;
import com.example.backend.dto.financials.FinancialSnapshotBackup;
import com.example.backend.repository.FinancialsRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FinancialWorkspaceQueryService implements FinancialWorkspaceQueries {

  private final FinancialsRepository financialsRepository;
  private final FinancialSnapshotPresenter snapshotPresenter;
  private final FinancialSnapshotResponseMapper responseMapper;
  private final FinancialSnapshotRequestMapper requestMapper;
  private final Clock clock;

  @Autowired
  public FinancialWorkspaceQueryService(
      FinancialsRepository financialsRepository,
      FinancialSnapshotPresenter snapshotPresenter,
      FinancialSnapshotResponseMapper responseMapper,
      FinancialSnapshotRequestMapper requestMapper) {
    this(
        financialsRepository,
        snapshotPresenter,
        responseMapper,
        requestMapper,
        Clock.systemDefaultZone());
  }

  FinancialWorkspaceQueryService(
      FinancialsRepository financialsRepository,
      FinancialSnapshotPresenter snapshotPresenter,
      FinancialSnapshotResponseMapper responseMapper,
      FinancialSnapshotRequestMapper requestMapper,
      Clock clock) {
    this.financialsRepository = financialsRepository;
    this.snapshotPresenter = snapshotPresenter;
    this.responseMapper = responseMapper;
    this.requestMapper = requestMapper;
    this.clock = clock;
  }

  @Override
  public ExpenseSnapshotResponse getSnapshot() {
    return snapshotPresenter.present(financialsRepository.currentSnapshot());
  }

  @Override
  public FinancialAuditHistoryResponse getAuditHistory(int limit) {
    if (limit < 1 || limit > 100) {
      throw new FinancialRequestException("Audit history limit must be between 1 and 100");
    }

    return responseMapper.toAuditHistory(financialsRepository.auditEvents(limit));
  }

  @Override
  public FinancialSnapshotBackup exportSnapshot() {
    return new FinancialSnapshotBackup(
        FinancialSnapshotBackup.FORMAT,
        Instant.now(clock),
        requestMapper.toSnapshotRequest(financialsRepository.currentSnapshot()));
  }
}
