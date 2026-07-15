package com.example.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.dto.financials.ExpenseBillSnapshotRequest;
import com.example.backend.dto.financials.ExpenseSnapshotRequest;
import com.example.backend.dto.financials.ExpenseSnapshotResponse;
import com.example.backend.dto.financials.FinancialAuditEventResponse;
import com.example.backend.dto.financials.FinancialAuditHistoryResponse;
import com.example.backend.dto.financials.FinancialProjectionSummaryResponse;
import com.example.backend.dto.financials.FinancialSnapshotBackup;
import com.example.backend.dto.financials.PayPeriodRequest;
import com.example.backend.service.FinancialRequestException;
import com.example.backend.service.FinancialSnapshotPresenter;
import com.example.backend.service.FinancialSnapshotVersionConflictException;
import com.example.backend.service.FinancialWorkspaceCommands;
import com.example.backend.service.FinancialWorkspaceQueries;
import com.example.backend.service.WorkspaceFinancialSnapshotInitializer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class FinancialsControllerTests {

  @Test
  void rejectsInvalidNestedSnapshotRequestWithProblemDetails() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialWorkspaceOperations());

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
                          "bills": [{
                            "id": null,
                            "bill": "",
                            "dueDay": 32,
                            "amount": -1,
                            "account": "",
                            "paid": false
                          }],
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
  void rejectsMalformedJsonWithProblemDetails() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialWorkspaceOperations());

    mockMvc
        .perform(put("/api/v1/financials").contentType("application/json").content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Malformed request"))
        .andExpect(jsonPath("$.detail").value("Request body is malformed or cannot be parsed"));
  }

  @Test
  void rejectsUnsupportedMediaTypeWithProblemDetails() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialWorkspaceOperations());

    mockMvc
        .perform(put("/api/v1/financials").contentType("text/plain").content("not-json"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.title").value("Unsupported media type"))
        .andExpect(jsonPath("$.detail").value("Content-Type is not supported for this endpoint"));
  }

  @Test
  void mapsFinancialRequestExceptionsToProblemDetails() throws Exception {
    MockMvc mockMvc =
        mockMvc(
            new TestFinancialWorkspaceOperations() {
              @Override
              public ExpenseSnapshotResponse getSnapshot() {
                throw new FinancialRequestException("Snapshot request is invalid");
              }
            });

    mockMvc
        .perform(get("/api/v1/financials"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("400 BAD_REQUEST"))
        .andExpect(jsonPath("$.detail").value("Snapshot request is invalid"));
  }

  @Test
  void mapsFinancialVersionConflictsToProblemDetails() throws Exception {
    MockMvc mockMvc =
        mockMvc(
            new TestFinancialWorkspaceOperations() {
              @Override
              public ExpenseSnapshotResponse getSnapshot() {
                throw new FinancialSnapshotVersionConflictException(
                    "The financial snapshot changed after it was loaded. Reload before saving.",
                    new IllegalStateException("stale snapshot"));
              }
            });

    mockMvc
        .perform(get("/api/v1/financials"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("409 CONFLICT"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "The financial snapshot changed after it was loaded. Reload before saving."));
  }

  @Test
  void rejectsInvalidPathVariableTypeWithProblemDetails() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialWorkspaceOperations());

    mockMvc
        .perform(get("/api/v1/financials/history?limit=not-a-number"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(jsonPath("$.detail").value("Request path or parameter has an invalid value"))
        .andExpect(content().string(containsString("limit: expected an integer")));
  }

  @Test
  void doesNotExposeGranularMutationEndpoints() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialWorkspaceOperations());

    var retiredRequests =
        List.of(
            post("/api/v1/financials/bills"),
            put("/api/v1/financials/bills/1"),
            delete("/api/v1/financials/bills/1"),
            post("/api/v1/financials/annual-withdrawals"),
            put("/api/v1/financials/annual-withdrawals/1"),
            delete("/api/v1/financials/annual-withdrawals/1"),
            post("/api/v1/financials/asset-accounts"),
            put("/api/v1/financials/asset-accounts/1"),
            delete("/api/v1/financials/asset-accounts/1"),
            post("/api/v1/financials/debt-accounts"),
            put("/api/v1/financials/debt-accounts/1"),
            delete("/api/v1/financials/debt-accounts/1"),
            post("/api/v1/financials/income-summary-items"),
            put("/api/v1/financials/income-summary-items/1"),
            delete("/api/v1/financials/income-summary-items/1"),
            post("/api/v1/financials/income-events"),
            put("/api/v1/financials/income-events/1"),
            delete("/api/v1/financials/income-events/1"),
            post("/api/v1/financials/important-dates"),
            put("/api/v1/financials/important-dates/1"),
            delete("/api/v1/financials/important-dates/1"),
            put("/api/v1/financials/pay-period"));

    for (var request : retiredRequests) {
      mockMvc.perform(request).andExpect(status().isNotFound());
    }
  }

  @Test
  void rejectsSnapshotSaveWithoutVersion() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialWorkspaceOperations());

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
  void initializesAnEmptyWorkspaceSnapshotFromPayPeriodDates() throws Exception {
    AtomicReference<PayPeriodRequest> initializedPayPeriod = new AtomicReference<>();
    AtomicReference<FinancialSnapshot> presentedSnapshot = new AtomicReference<>();
    TestFinancialWorkspaceOperations operations = new TestFinancialWorkspaceOperations();
    FinancialSnapshot createdSnapshot =
        emptyDomainSnapshot(1, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 23));
    MockMvc mockMvc =
        mockMvc(
            operations,
            (payPeriod) -> {
              initializedPayPeriod.set(payPeriod);
              return createdSnapshot;
            },
            (snapshot) -> {
              presentedSnapshot.set(snapshot);
              return operations.getSnapshot();
            });

    mockMvc
        .perform(
            post("/api/v1/financials")
                .contentType("application/json")
                .content(
                    """
                    {
                      "startDate": "2026-07-10",
                      "endDate": "2026-07-23"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version").value(1));

    assertThat(initializedPayPeriod.get())
        .isEqualTo(new PayPeriodRequest(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 23)));
    assertThat(presentedSnapshot.get()).isSameAs(createdSnapshot);
  }

  @Test
  void rejectsSnapshotInitializationWithoutBothPayPeriodDates() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialWorkspaceOperations());

    mockMvc
        .perform(
            post("/api/v1/financials")
                .contentType("application/json")
                .content("{\"startDate\":\"2026-07-10\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(jsonPath("$.errors").isArray());
  }

  @Test
  void rejectsNullNestedSnapshotRecords() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialWorkspaceOperations());

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
    MockMvc mockMvc = mockMvc(new TestFinancialWorkspaceOperations());

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
    MockMvc mockMvc = mockMvc(new TestFinancialWorkspaceOperations());

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
  void restoresJsonBackupAgainstExpectedTargetVersion() throws Exception {
    AtomicReference<Long> expectedVersion = new AtomicReference<>();
    AtomicReference<FinancialSnapshotBackup> restoredBackup = new AtomicReference<>();
    MockMvc mockMvc =
        mockMvc(
            new TestFinancialWorkspaceOperations() {
              @Override
              public ExpenseSnapshotResponse restoreSnapshot(
                  long targetVersion, FinancialSnapshotBackup backup) {
                expectedVersion.set(targetVersion);
                restoredBackup.set(backup);
                return getSnapshot();
              }
            });

    mockMvc
        .perform(
            post("/api/v1/financials/restore")
                .param("expectedVersion", "12")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "format": "end-to-end-app.financial-snapshot.v1",
                      "exportedAt": "2026-07-11T10:15:30Z",
                      "snapshot": {
                        "version": 3,
                        "payPeriodStart": "2026-07-01",
                        "payPeriodEnd": "2026-07-14",
                        "bills": [],
                        "annualWithdrawals": [],
                        "assetCategories": [],
                        "debtAccounts": [],
                        "incomeSummaryItems": [],
                        "incomeEvents": [],
                        "importantDates": []
                      }
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));

    assertThat(expectedVersion.get()).isEqualTo(12);
    assertThat(restoredBackup.get().snapshot().version()).isEqualTo(3);
  }

  @Test
  void rejectsIncompleteJsonBackup() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialWorkspaceOperations());

    mockMvc
        .perform(
            post("/api/v1/financials/restore")
                .param("expectedVersion", "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "format": "end-to-end-app.financial-snapshot.v1",
                      "exportedAt": "2026-07-11T10:15:30Z"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(content().string(containsString("Backup snapshot is required")));
  }

  @Test
  void doesNotExposeTabularSnapshotEndpoints() throws Exception {
    MockMvc mockMvc = mockMvc(new TestFinancialWorkspaceOperations());

    var retiredRequests =
        List.of(
            get("/api/v1/financials/export/csv"),
            get("/api/v1/financials/export/xlsx"),
            post("/api/v1/financials/import/csv"),
            post("/api/v1/financials/import/xlsx"),
            post("/api/v1/financials/import/json"));

    for (var request : retiredRequests) {
      mockMvc.perform(request).andExpect(status().isNotFound());
    }
  }

  private MockMvc mockMvc(TestFinancialWorkspaceOperations operations) {
    return mockMvc(
        operations,
        (payPeriod) -> emptyDomainSnapshot(1, payPeriod.startDate(), payPeriod.endDate()),
        (snapshot) -> operations.getSnapshot());
  }

  private MockMvc mockMvc(
      TestFinancialWorkspaceOperations operations,
      WorkspaceFinancialSnapshotInitializer snapshotInitializer,
      FinancialSnapshotPresenter snapshotPresenter) {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    return MockMvcBuilders.standaloneSetup(
            new FinancialsController(
                operations, operations, snapshotInitializer, snapshotPresenter))
        .setControllerAdvice(new ApiExceptionHandler())
        .setValidator(validator)
        .build();
  }

  private static FinancialSnapshot emptyDomainSnapshot(
      long version, LocalDate startDate, LocalDate endDate) {
    return new FinancialSnapshot(
        version, startDate, endDate, List.of(), List.of(), List.of(), List.of(), List.of(),
        List.of(), List.of());
  }

  private static class TestFinancialWorkspaceOperations
      implements FinancialWorkspaceQueries, FinancialWorkspaceCommands {

    @Override
    public ExpenseSnapshotResponse getSnapshot() {
      return emptySnapshotResponse(1);
    }

    @Override
    public FinancialSnapshotBackup exportSnapshot() {
      return new FinancialSnapshotBackup(
          FinancialSnapshotBackup.FORMAT,
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

    @Override
    public ExpenseSnapshotResponse saveSnapshot(ExpenseSnapshotRequest request) {
      return getSnapshot();
    }

    @Override
    public ExpenseSnapshotResponse restoreSnapshot(
        long expectedVersion, FinancialSnapshotBackup backup) {
      return getSnapshot();
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
