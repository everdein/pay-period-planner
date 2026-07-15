package com.example.backend.api;

import com.example.backend.dto.financials.ExpenseSnapshotRequest;
import com.example.backend.dto.financials.ExpenseSnapshotResponse;
import com.example.backend.dto.financials.FinancialAuditHistoryResponse;
import com.example.backend.dto.financials.FinancialSnapshotBackup;
import com.example.backend.dto.financials.PayPeriodRequest;
import com.example.backend.service.FinancialSnapshotPresenter;
import com.example.backend.service.FinancialWorkspaceCommands;
import com.example.backend.service.FinancialWorkspaceQueries;
import com.example.backend.service.WorkspaceFinancialSnapshotInitializer;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Financials API v1
 *
 * <p>RESTful API for managing one versioned financial workspace snapshot.
 *
 * <p>GET retrieves the current aggregate, PUT performs a version-checked replacement, and POST
 * initializes an empty aggregate for a new workspace.
 *
 * <p>Versioning: Explicit /api/v1/ prefix for future compatibility and clear deprecation path when
 * v2 is introduced.
 */
@RestController
@RequestMapping("/api/v1/financials")
public class FinancialsController {

  private final FinancialWorkspaceQueries workspaceQueries;
  private final FinancialWorkspaceCommands workspaceCommands;
  private final WorkspaceFinancialSnapshotInitializer snapshotInitializer;
  private final FinancialSnapshotPresenter snapshotPresenter;

  public FinancialsController(
      FinancialWorkspaceQueries workspaceQueries,
      FinancialWorkspaceCommands workspaceCommands,
      WorkspaceFinancialSnapshotInitializer snapshotInitializer,
      FinancialSnapshotPresenter snapshotPresenter) {
    this.workspaceQueries = workspaceQueries;
    this.workspaceCommands = workspaceCommands;
    this.snapshotInitializer = snapshotInitializer;
    this.snapshotPresenter = snapshotPresenter;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ExpenseSnapshotResponse initializeSnapshot(
      @Valid @RequestBody PayPeriodRequest payPeriod) {
    return snapshotPresenter.present(snapshotInitializer.initialize(payPeriod));
  }

  @GetMapping
  public ExpenseSnapshotResponse getSnapshot() {
    return workspaceQueries.getSnapshot();
  }

  @GetMapping("/history")
  public FinancialAuditHistoryResponse getAuditHistory(
      @RequestParam(defaultValue = "50") int limit) {
    return workspaceQueries.getAuditHistory(limit);
  }

  @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<FinancialSnapshotBackup> exportSnapshot() {
    FinancialSnapshotBackup snapshotExport = workspaceQueries.exportSnapshot();
    String filename = "financial-snapshot-v" + snapshotExport.snapshot().version() + ".json";

    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename).build().toString())
        .body(snapshotExport);
  }

  @PostMapping(value = "/restore", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ExpenseSnapshotResponse restoreSnapshot(
      @RequestParam long expectedVersion, @Valid @RequestBody FinancialSnapshotBackup backup) {
    return workspaceCommands.restoreSnapshot(expectedVersion, backup);
  }

  @PutMapping
  public ExpenseSnapshotResponse saveSnapshot(@Valid @RequestBody ExpenseSnapshotRequest request) {
    return workspaceCommands.saveSnapshot(request);
  }
}
