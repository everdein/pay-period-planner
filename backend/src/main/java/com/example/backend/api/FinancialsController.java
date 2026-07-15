package com.example.backend.api;

import com.example.backend.dto.financials.AnnualWithdrawalRequest;
import com.example.backend.dto.financials.AnnualWithdrawalResponse;
import com.example.backend.dto.financials.AssetAccountRecordResponse;
import com.example.backend.dto.financials.AssetAccountRequest;
import com.example.backend.dto.financials.DebtAccountRequest;
import com.example.backend.dto.financials.DebtAccountResponse;
import com.example.backend.dto.financials.ExpenseBillRequest;
import com.example.backend.dto.financials.ExpenseBillResponse;
import com.example.backend.dto.financials.ExpenseSnapshotRequest;
import com.example.backend.dto.financials.ExpenseSnapshotResponse;
import com.example.backend.dto.financials.FinancialAuditHistoryResponse;
import com.example.backend.dto.financials.FinancialSnapshotExportResponse;
import com.example.backend.dto.financials.FinancialSnapshotFileExport;
import com.example.backend.dto.financials.ImportantDateRequest;
import com.example.backend.dto.financials.ImportantDateResponse;
import com.example.backend.dto.financials.IncomeEventRequest;
import com.example.backend.dto.financials.IncomeEventResponse;
import com.example.backend.dto.financials.IncomeSummaryItemRequest;
import com.example.backend.dto.financials.IncomeSummaryItemResponse;
import com.example.backend.dto.financials.PayPeriodRequest;
import com.example.backend.service.FinancialsService;
import com.example.backend.service.WorkspaceFinancialSnapshotInitializer;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
 * <p>RESTful API for managing financial snapshots, expense bills, assets, and debts.
 *
 * <p>Endpoints: - GET /api/v1/financials → Retrieve current financial snapshot - PUT
 * /api/v1/financials → Save full financial snapshot - POST /api/v1/financials/bills → Create
 * expense bill - PUT /api/v1/financials/bills/{id} → Update expense bill - DELETE
 * /api/v1/financials/bills/{id} → Delete expense bill - PUT /api/v1/financials/pay-period → Update
 * pay period dates
 *
 * <p>Versioning: Explicit /api/v1/ prefix for future compatibility and clear deprecation path when
 * v2 is introduced.
 */
@RestController
@RequestMapping("/api/v1/financials")
public class FinancialsController {

  private static final String XLSX_MEDIA_TYPE_VALUE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  private static final MediaType CSV_MEDIA_TYPE =
      new MediaType("text", "csv", StandardCharsets.UTF_8);
  private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(XLSX_MEDIA_TYPE_VALUE);

  private final FinancialsService financialsService;
  private final WorkspaceFinancialSnapshotInitializer snapshotInitializer;

  public FinancialsController(
      FinancialsService financialsService,
      WorkspaceFinancialSnapshotInitializer snapshotInitializer) {
    this.financialsService = financialsService;
    this.snapshotInitializer = snapshotInitializer;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ExpenseSnapshotResponse initializeSnapshot(
      @Valid @RequestBody PayPeriodRequest payPeriod) {
    snapshotInitializer.initialize(payPeriod);
    return financialsService.getSnapshot();
  }

  @GetMapping
  public ExpenseSnapshotResponse getSnapshot() {
    return financialsService.getSnapshot();
  }

  @GetMapping("/history")
  public FinancialAuditHistoryResponse getAuditHistory(
      @RequestParam(defaultValue = "50") int limit) {
    return financialsService.getAuditHistory(limit);
  }

  @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<FinancialSnapshotExportResponse> exportSnapshot() {
    FinancialSnapshotExportResponse snapshotExport = financialsService.exportSnapshot();
    String filename = "financial-snapshot-v" + snapshotExport.snapshot().version() + ".json";

    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename).build().toString())
        .body(snapshotExport);
  }

  @GetMapping(value = "/export/csv", produces = "text/csv")
  public ResponseEntity<byte[]> exportSnapshotCsv() {
    return tabularExportResponse(financialsService.exportSnapshotCsv(), "csv", CSV_MEDIA_TYPE);
  }

  @GetMapping(value = "/export/xlsx", produces = XLSX_MEDIA_TYPE_VALUE)
  public ResponseEntity<byte[]> exportSnapshotXlsx() {
    return tabularExportResponse(financialsService.exportSnapshotXlsx(), "xlsx", XLSX_MEDIA_TYPE);
  }

  @PostMapping(
      value = "/import/csv",
      consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
  public ExpenseSnapshotResponse importSnapshotCsv(@RequestBody String csv) {
    return financialsService.importSnapshotCsv(csv);
  }

  @PostMapping(value = "/import/xlsx", consumes = XLSX_MEDIA_TYPE_VALUE)
  public ExpenseSnapshotResponse importSnapshotXlsx(@RequestBody byte[] workbook) {
    return financialsService.importSnapshotXlsx(workbook);
  }

  @PostMapping("/bills")
  @ResponseStatus(HttpStatus.CREATED)
  public ExpenseBillResponse addBill(@Valid @RequestBody ExpenseBillRequest request) {
    return financialsService.addBill(request);
  }

  @PutMapping("/bills/{id}")
  public ExpenseBillResponse updateBill(
      @PathVariable long id, @Valid @RequestBody ExpenseBillRequest request) {
    return financialsService.updateBill(id, request);
  }

  @DeleteMapping("/bills/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteBill(@PathVariable long id) {
    financialsService.deleteBill(id);
  }

  @PostMapping("/annual-withdrawals")
  @ResponseStatus(HttpStatus.CREATED)
  public AnnualWithdrawalResponse addAnnualWithdrawal(
      @Valid @RequestBody AnnualWithdrawalRequest request) {
    return financialsService.addAnnualWithdrawal(request);
  }

  @PutMapping("/annual-withdrawals/{id}")
  public AnnualWithdrawalResponse updateAnnualWithdrawal(
      @PathVariable long id, @Valid @RequestBody AnnualWithdrawalRequest request) {
    return financialsService.updateAnnualWithdrawal(id, request);
  }

  @DeleteMapping("/annual-withdrawals/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteAnnualWithdrawal(@PathVariable long id) {
    financialsService.deleteAnnualWithdrawal(id);
  }

  @PostMapping("/asset-accounts")
  @ResponseStatus(HttpStatus.CREATED)
  public AssetAccountRecordResponse addAssetAccount(
      @Valid @RequestBody AssetAccountRequest request) {
    return financialsService.addAssetAccount(request);
  }

  @PutMapping("/asset-accounts/{id}")
  public AssetAccountRecordResponse updateAssetAccount(
      @PathVariable long id, @Valid @RequestBody AssetAccountRequest request) {
    return financialsService.updateAssetAccount(id, request);
  }

  @DeleteMapping("/asset-accounts/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteAssetAccount(@PathVariable long id) {
    financialsService.deleteAssetAccount(id);
  }

  @PostMapping("/debt-accounts")
  @ResponseStatus(HttpStatus.CREATED)
  public DebtAccountResponse addDebtAccount(@Valid @RequestBody DebtAccountRequest request) {
    return financialsService.addDebtAccount(request);
  }

  @PutMapping("/debt-accounts/{id}")
  public DebtAccountResponse updateDebtAccount(
      @PathVariable long id, @Valid @RequestBody DebtAccountRequest request) {
    return financialsService.updateDebtAccount(id, request);
  }

  @DeleteMapping("/debt-accounts/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteDebtAccount(@PathVariable long id) {
    financialsService.deleteDebtAccount(id);
  }

  @PostMapping("/income-summary-items")
  @ResponseStatus(HttpStatus.CREATED)
  public IncomeSummaryItemResponse addIncomeSummaryItem(
      @Valid @RequestBody IncomeSummaryItemRequest request) {
    return financialsService.addIncomeSummaryItem(request);
  }

  @PutMapping("/income-summary-items/{id}")
  public IncomeSummaryItemResponse updateIncomeSummaryItem(
      @PathVariable long id, @Valid @RequestBody IncomeSummaryItemRequest request) {
    return financialsService.updateIncomeSummaryItem(id, request);
  }

  @DeleteMapping("/income-summary-items/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteIncomeSummaryItem(@PathVariable long id) {
    financialsService.deleteIncomeSummaryItem(id);
  }

  @PostMapping("/income-events")
  @ResponseStatus(HttpStatus.CREATED)
  public IncomeEventResponse addIncomeEvent(@Valid @RequestBody IncomeEventRequest request) {
    return financialsService.addIncomeEvent(request);
  }

  @PutMapping("/income-events/{id}")
  public IncomeEventResponse updateIncomeEvent(
      @PathVariable long id, @Valid @RequestBody IncomeEventRequest request) {
    return financialsService.updateIncomeEvent(id, request);
  }

  @DeleteMapping("/income-events/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteIncomeEvent(@PathVariable long id) {
    financialsService.deleteIncomeEvent(id);
  }

  @PostMapping("/important-dates")
  @ResponseStatus(HttpStatus.CREATED)
  public ImportantDateResponse addImportantDate(@Valid @RequestBody ImportantDateRequest request) {
    return financialsService.addImportantDate(request);
  }

  @PutMapping("/important-dates/{id}")
  public ImportantDateResponse updateImportantDate(
      @PathVariable long id, @Valid @RequestBody ImportantDateRequest request) {
    return financialsService.updateImportantDate(id, request);
  }

  @DeleteMapping("/important-dates/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteImportantDate(@PathVariable long id) {
    financialsService.deleteImportantDate(id);
  }

  @PutMapping("/pay-period")
  public ExpenseSnapshotResponse updatePayPeriod(@Valid @RequestBody PayPeriodRequest request) {
    return financialsService.updatePayPeriod(request);
  }

  @PutMapping
  public ExpenseSnapshotResponse saveSnapshot(@Valid @RequestBody ExpenseSnapshotRequest request) {
    return financialsService.saveSnapshot(request);
  }

  private ResponseEntity<byte[]> tabularExportResponse(
      FinancialSnapshotFileExport export, String extension, MediaType mediaType) {
    String filename = "financial-snapshot-v" + export.version() + "." + extension;
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(mediaType)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename).build().toString())
        .body(export.content());
  }
}
