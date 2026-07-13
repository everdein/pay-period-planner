package com.example.backend.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.dto.financials.AssetAccountRecordResponse;
import com.example.backend.dto.financials.AssetAccountRequest;
import com.example.backend.dto.financials.ExpenseBillSnapshotRequest;
import com.example.backend.dto.financials.ExpenseSnapshotRequest;
import com.example.backend.dto.financials.ExpenseSnapshotResponse;
import com.example.backend.dto.financials.FinancialAuditEventResponse;
import com.example.backend.dto.financials.FinancialAuditHistoryResponse;
import com.example.backend.dto.financials.FinancialProjectionSummaryResponse;
import com.example.backend.dto.financials.FinancialSnapshotExportResponse;
import com.example.backend.dto.financials.FinancialSnapshotFileExport;
import com.example.backend.service.FinancialsService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.server.ResponseStatusException;

class FinancialsControllerTests {

  @Test
  void rejectsInvalidBillRequestWithProblemDetails() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialsService());

    mockMvc
        .perform(
            post("/api/v1/financials/bills")
                .contentType("application/json")
                .content(
                    """
                        {
                          "bill": "",
                          "dueDay": 32,
                          "amount": -1,
                          "account": "",
                          "paid": false
                        }
                        """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(jsonPath("$.errors").isArray());
  }

  @Test
  void rejectsBillRequestWithoutAmount() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialsService());

    mockMvc
        .perform(
            post("/api/v1/financials/bills")
                .contentType("application/json")
                .content(
                    """
                        {
                          "bill": "Internet",
                          "dueDay": 15,
                          "account": "Check",
                          "paid": false
                        }
                        """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(jsonPath("$.errors").isArray());
  }

  @Test
  void rejectsMalformedJsonWithProblemDetails() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialsService());

    mockMvc
        .perform(post("/api/v1/financials/bills").contentType("application/json").content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Malformed request"))
        .andExpect(jsonPath("$.detail").value("Request body is malformed or cannot be parsed"));
  }

  @Test
  void rejectsUnsupportedMediaTypeWithProblemDetails() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialsService());

    mockMvc
        .perform(post("/api/v1/financials/bills").contentType("text/plain").content("not-json"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.title").value("Unsupported media type"))
        .andExpect(jsonPath("$.detail").value("Content-Type is not supported for this endpoint"));
  }

  @Test
  void mapsResponseStatusExceptionsToProblemDetails() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialsService());

    mockMvc
        .perform(delete("/api/v1/financials/bills/99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Bill not found"));
  }

  @Test
  void rejectsInvalidPathVariableTypeWithProblemDetails() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialsService());

    mockMvc
        .perform(delete("/api/v1/financials/bills/not-a-number"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(jsonPath("$.detail").value("Request path or parameter has an invalid value"))
        .andExpect(content().string(containsString("id: expected a whole number")));
  }

  @Test
  void rejectsNonPositiveGranularRecordId() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialsService());

    mockMvc
        .perform(
            put("/api/v1/financials/bills/0")
                .contentType("application/json")
                .content(
                    """
                        {
                          "bill": "Internet",
                          "dueDay": 15,
                          "amount": 80,
                          "account": "Check",
                          "paid": false
                        }
                        """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Record id must be positive"));
  }

  @Test
  void createsAssetAccountThroughGranularEndpoint() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialsService());

    mockMvc
        .perform(
            post("/api/v1/financials/asset-accounts")
                .contentType("application/json")
                .content(
                    """
                        {
                          "categoryKey": "cash-savings",
                          "categoryLabel": "Cash & Savings",
                          "account": "Vacation",
                          "company": "Credit Union",
                          "amount": 900
                        }
                        """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(42))
        .andExpect(jsonPath("$.categoryKey").value("cash-savings"))
        .andExpect(jsonPath("$.account").value("Vacation"));
  }

  @Test
  void rejectsInvalidAnnualWithdrawalRequestWithProblemDetails() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialsService());

    mockMvc
        .perform(
            post("/api/v1/financials/annual-withdrawals")
                .contentType("application/json")
                .content(
                    """
                        {
                          "bill": "",
                          "month": 13,
                          "day": 32,
                          "amount": -1,
                          "account": "",
                          "paid": false
                        }
                        """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(jsonPath("$.errors").isArray());
  }

  @Test
  void mapsMissingGranularRecordToProblemDetails() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialsService());

    mockMvc
        .perform(delete("/api/v1/financials/important-dates/99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Important date not found"));
  }

  @Test
  void rejectsSnapshotSaveWithoutVersion() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialsService());

    mockMvc
        .perform(
            put("/api/v1/financials")
                .contentType("application/json")
                .content(
                    """
                        {
                          "payPeriodStart": "2026-06-12",
                          "payPeriodEnd": "2026-06-26",
                          "bills": [],
                          "annualWithdrawals": [],
                          "assetCategories": [],
                          "debtAccounts": [],
                          "incomeSummaryItems": [],
                          "incomeEvents": [],
                          "importantDates": []
                        }
                        """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(jsonPath("$.errors").isArray());
  }

  @Test
  void rejectsNullNestedSnapshotRecords() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialsService());

    mockMvc
        .perform(
            put("/api/v1/financials")
                .contentType("application/json")
                .content(
                    """
                        {
                          "version": 1,
                          "payPeriodStart": "2026-06-12",
                          "payPeriodEnd": "2026-06-26",
                          "bills": [null],
                          "annualWithdrawals": [],
                          "assetCategories": [],
                          "debtAccounts": [],
                          "incomeSummaryItems": [],
                          "incomeEvents": [],
                          "importantDates": []
                        }
                        """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(content().string(containsString("Bill record is required")));
  }

  @Test
  void exportsSnapshotBackupAsNoStoreAttachment() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialsService());

    mockMvc
        .perform(get("/api/v1/financials/export"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION, containsString("financial-snapshot-v7.json")))
        .andExpect(jsonPath("$.format").value("end-to-end-app.financial-snapshot.v1"))
        .andExpect(jsonPath("$.exportedAt").value("2026-07-11T10:15:30Z"))
        .andExpect(jsonPath("$.snapshot.version").value(7))
        .andExpect(jsonPath("$.snapshot.bills[0].bill").value("Rent"))
        .andExpect(jsonPath("$.snapshot.bills[0].dueLabel").doesNotExist());
  }

  @Test
  void returnsAuditHistory() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialsService());

    mockMvc
        .perform(get("/api/v1/financials/history").param("limit", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.events[0].id").value(11))
        .andExpect(jsonPath("$.events[0].action").value("CREATE"))
        .andExpect(jsonPath("$.events[0].resourceType").value("monthly-bill"))
        .andExpect(jsonPath("$.events[0].resourceId").value(42))
        .andExpect(jsonPath("$.events[0].versionBefore").value(7))
        .andExpect(jsonPath("$.events[0].versionAfter").value(8))
        .andExpect(jsonPath("$.events[0].summary").value("Created monthly bill"))
        .andExpect(jsonPath("$.events[0].projectionSummary.totalMonthlyExpenses").value(1500))
        .andExpect(jsonPath("$.events[0].projectionSummary.netWorth").value(14500));
  }

  @Test
  void exportsSnapshotAsCsvAttachment() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialsService());

    mockMvc
        .perform(get("/api/v1/financials/export/csv"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/csv"))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION, containsString("financial-snapshot-v7.csv")))
        .andExpect(content().string(containsString("recordType,version,id")))
        .andExpect(content().string(containsString("bill,,1,,,Rent,1")));
  }

  @Test
  void exportsSnapshotAsXlsxAttachment() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialsService());

    mockMvc
        .perform(get("/api/v1/financials/export/xlsx"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .contentTypeCompatibleWith(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION, containsString("financial-snapshot-v7.xlsx")))
        .andExpect(content().bytes(new byte[] {(byte) 'P', (byte) 'K'}));
  }

  @Test
  void importsSnapshotCsv() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialsService());

    mockMvc
        .perform(
            post("/api/v1/financials/import/csv")
                .contentType("text/csv")
                .content("recordType,version,id,payPeriodStart,payPeriodEnd\n"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(8));
  }

  private MockMvc mockMvc(FinancialsService financialsService) {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    return MockMvcBuilders.standaloneSetup(new FinancialsController(financialsService))
        .setControllerAdvice(new ApiExceptionHandler())
        .setValidator(validator)
        .build();
  }

  private static class TestFinancialsService extends FinancialsService {

    TestFinancialsService() {
      super(null);
    }

    @Override
    public void deleteBill(long id) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found");
    }

    @Override
    public AssetAccountRecordResponse addAssetAccount(AssetAccountRequest request) {
      return new AssetAccountRecordResponse(
          42,
          request.categoryKey(),
          request.categoryLabel(),
          request.account(),
          request.company(),
          request.amount());
    }

    @Override
    public void deleteImportantDate(long id) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Important date not found");
    }

    @Override
    public FinancialSnapshotExportResponse exportSnapshot() {
      return new FinancialSnapshotExportResponse(
          "end-to-end-app.financial-snapshot.v1",
          Instant.parse("2026-07-11T10:15:30Z"),
          new ExpenseSnapshotRequest(
              7L,
              LocalDate.of(2026, 7, 1),
              LocalDate.of(2026, 7, 14),
              List.of(
                  new ExpenseBillSnapshotRequest(
                      1L, "Rent", 1, new BigDecimal("2600.00"), "Check", false)),
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              List.of()));
    }

    @Override
    public FinancialSnapshotFileExport exportSnapshotCsv() {
      return new FinancialSnapshotFileExport(
          7,
          "recordType,version,id,payPeriodStart,payPeriodEnd,bill,dueDay\nbill,,1,,,Rent,1\n"
              .getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public FinancialSnapshotFileExport exportSnapshotXlsx() {
      return new FinancialSnapshotFileExport(7, new byte[] {(byte) 'P', (byte) 'K'});
    }

    @Override
    public ExpenseSnapshotResponse importSnapshotCsv(String csv) {
      return emptySnapshotResponse(8);
    }

    @Override
    public ExpenseSnapshotResponse importSnapshotXlsx(byte[] workbook) {
      return emptySnapshotResponse(8);
    }

    @Override
    public FinancialAuditHistoryResponse getAuditHistory(int limit) {
      return new FinancialAuditHistoryResponse(
          List.of(
              new FinancialAuditEventResponse(
                  11,
                  Instant.parse("2026-07-12T10:15:30Z"),
                  "CREATE",
                  "monthly-bill",
                  42L,
                  7,
                  8,
                  "Created monthly bill",
                  new FinancialProjectionSummaryResponse(
                      LocalDate.of(2026, 7, 1),
                      LocalDate.of(2026, 7, 14),
                      3,
                      1,
                      2,
                      1,
                      2,
                      2,
                      1,
                      new BigDecimal("1500.00"),
                      new BigDecimal("99.00"),
                      new BigDecimal("15000.00"),
                      new BigDecimal("500.00"),
                      new BigDecimal("14500.00")))));
    }

    private ExpenseSnapshotResponse emptySnapshotResponse(long version) {
      return new ExpenseSnapshotResponse(
          version,
          LocalDate.of(2026, 7, 1),
          LocalDate.of(2026, 7, 14),
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          List.of());
    }
  }
}
