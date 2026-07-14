package com.example.backend.repository;

import com.example.backend.domain.financials.AnnualWithdrawal;
import com.example.backend.domain.financials.AssetAccount;
import com.example.backend.domain.financials.DebtAccount;
import com.example.backend.domain.financials.ExpenseBill;
import com.example.backend.domain.financials.FinancialAuditEvent;
import com.example.backend.domain.financials.FinancialProjectionSummary;
import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.domain.financials.ImportantDate;
import com.example.backend.domain.financials.IncomeEvent;
import com.example.backend.domain.financials.IncomeSummaryItem;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class PostgresFinancialRecordSnapshotAdapter implements WorkspaceFinancialSnapshotStore {

  private final JdbcTemplate jdbcTemplate;
  private final TransactionOperations transactionOperations;

  @Autowired
  public PostgresFinancialRecordSnapshotAdapter(
      JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
    this(jdbcTemplate, new TransactionTemplate(transactionManager));
  }

  PostgresFinancialRecordSnapshotAdapter(
      JdbcTemplate jdbcTemplate, TransactionOperations transactionOperations) {
    this.jdbcTemplate = jdbcTemplate;
    this.transactionOperations = transactionOperations;
  }

  @Override
  public Optional<FinancialSnapshot> loadActiveSnapshot(long workspaceId) {
    return loadActiveData(workspaceId).map(FinancialsData::toSnapshot);
  }

  public Optional<FinancialsData> loadActiveData(long workspaceId) {
    Optional<StoredSnapshot> storedSnapshot = activeSnapshot(workspaceId, false);

    if (storedSnapshot.isEmpty()) {
      return Optional.empty();
    }

    long snapshotId = storedSnapshot.get().id();
    return Optional.of(
        new FinancialsData(
            storedSnapshot.get().version(),
            storedSnapshot.get().payPeriodStart(),
            storedSnapshot.get().payPeriodEnd(),
            bills(snapshotId),
            annualWithdrawals(snapshotId),
            assetAccounts(snapshotId),
            debtAccounts(snapshotId),
            incomeSummaryItems(snapshotId),
            incomeEvents(snapshotId),
            importantDates(snapshotId),
            auditEvents(workspaceId)));
  }

  public void replaceActiveData(
      long workspaceId, long expectedVersion, FinancialsData replacementData) {
    if (replacementData.version() != expectedVersion + 1) {
      throw new IllegalArgumentException(
          "Replacement snapshot version must be exactly one greater than the expected version");
    }

    transactionOperations.executeWithoutResult(
        (status) -> {
          StoredSnapshot current = requireActiveSnapshotForUpdate(workspaceId);
          if (current.version() != expectedVersion) {
            throw new SnapshotVersionConflictException(expectedVersion, current.version());
          }

          int deactivated =
              jdbcTemplate.update(
                  """
                  update financial_record_snapshot
                  set active = false,
                      updated_at = now()
                  where id = ?
                    and active = true
                  """,
                  current.id());
          if (deactivated != 1) {
            throw new SnapshotVersionConflictException(expectedVersion, current.version());
          }

          long snapshotId = insertSnapshot(workspaceId, replacementData.toSnapshot());
          replacementData.auditEvents().stream()
              .filter((event) -> event.versionAfter() > expectedVersion)
              .forEach((event) -> insertAuditEvent(snapshotId, event));
        });
  }

  public void replaceActiveSnapshot(long workspaceId, FinancialSnapshot snapshot) {
    transactionOperations.executeWithoutResult(
        (status) -> {
          lockWorkspace(workspaceId);
          jdbcTemplate.update(
              """
              update financial_record_snapshot
              set active = false,
                  updated_at = now()
              where active = true
                and workspace_id = ?
              """,
              workspaceId);
          insertSnapshot(workspaceId, snapshot);
        });
  }

  @Override
  public long createInitialSnapshot(long workspaceId, FinancialSnapshot snapshot) {
    Long snapshotId =
        transactionOperations.execute(
            (status) -> {
              lockWorkspace(workspaceId);
              return insertSnapshot(workspaceId, snapshot);
            });
    if (snapshotId == null) {
      throw new IllegalStateException("The relational snapshot transaction returned no ID");
    }
    return snapshotId;
  }

  @Override
  public boolean deactivateSnapshotIfUnchanged(
      long workspaceId, long snapshotId, long expectedVersion) {
    return jdbcTemplate.update(
            """
            update financial_record_snapshot
            set active = false,
                updated_at = now()
            where id = ?
              and workspace_id = ?
              and version = ?
              and active = true
            """,
            snapshotId,
            workspaceId,
            expectedVersion)
        == 1;
  }

  public Optional<ExpenseBill> findBill(long workspaceId, long appRecordId) {
    return queryOptional(
        """
        select record.app_record_id, record.bill, record.due_day, record.amount, record.account, record.paid
        from financial_record_monthly_bill record
        join financial_record_snapshot snapshot on snapshot.id = record.snapshot_id
        where snapshot.active = true
          and snapshot.workspace_id = ?
          and record.app_record_id = ?
        order by record.id
        limit 1
        """,
        this::mapBill,
        workspaceId,
        appRecordId);
  }

  public ExpenseBill createBill(long workspaceId, ExpenseBill bill) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate(workspaceId);
          ExpenseBill created =
              bill.withId(nextAppRecordId(snapshot.id(), RecordTable.MONTHLY_BILL));
          insertBill(snapshot.id(), created);
          bumpSnapshotVersion(snapshot.id());
          return created;
        });
  }

  public Optional<ExpenseBill> updateBill(long workspaceId, long appRecordId, ExpenseBill bill) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate(workspaceId);
          ExpenseBill updated = bill.withId(appRecordId);
          int rows =
              jdbcTemplate.update(
                  """
                  update financial_record_monthly_bill
                  set bill = ?,
                      due_day = ?,
                      amount = ?,
                      account = ?,
                      paid = ?
                  where snapshot_id = ?
                    and app_record_id = ?
                  """,
                  updated.bill(),
                  updated.dueDay(),
                  updated.amount(),
                  updated.account(),
                  updated.paid(),
                  snapshot.id(),
                  appRecordId);
          if (rows == 0) {
            return Optional.empty();
          }
          bumpSnapshotVersion(snapshot.id());
          return Optional.of(updated);
        });
  }

  public boolean deleteBill(long workspaceId, long appRecordId) {
    return deleteRecord(workspaceId, RecordTable.MONTHLY_BILL, appRecordId);
  }

  public Optional<AnnualWithdrawal> findAnnualWithdrawal(long workspaceId, long appRecordId) {
    return queryOptional(
        """
        select record.app_record_id, record.bill, record.month, record.day, record.amount, record.account, record.paid
        from financial_record_annual_withdrawal record
        join financial_record_snapshot snapshot on snapshot.id = record.snapshot_id
        where snapshot.active = true
          and snapshot.workspace_id = ?
          and record.app_record_id = ?
        order by record.id
        limit 1
        """,
        this::mapAnnualWithdrawal,
        workspaceId,
        appRecordId);
  }

  public AnnualWithdrawal createAnnualWithdrawal(long workspaceId, AnnualWithdrawal withdrawal) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate(workspaceId);
          AnnualWithdrawal created =
              withdrawal.withId(nextAppRecordId(snapshot.id(), RecordTable.ANNUAL_WITHDRAWAL));
          insertAnnualWithdrawal(snapshot.id(), created);
          bumpSnapshotVersion(snapshot.id());
          return created;
        });
  }

  public Optional<AnnualWithdrawal> updateAnnualWithdrawal(
      long workspaceId, long appRecordId, AnnualWithdrawal withdrawal) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate(workspaceId);
          AnnualWithdrawal updated = withdrawal.withId(appRecordId);
          int rows =
              jdbcTemplate.update(
                  """
                  update financial_record_annual_withdrawal
                  set bill = ?,
                      month = ?,
                      day = ?,
                      amount = ?,
                      account = ?,
                      paid = ?
                  where snapshot_id = ?
                    and app_record_id = ?
                  """,
                  updated.bill(),
                  updated.month(),
                  updated.day(),
                  updated.amount(),
                  updated.account(),
                  updated.paid(),
                  snapshot.id(),
                  appRecordId);
          if (rows == 0) {
            return Optional.empty();
          }
          bumpSnapshotVersion(snapshot.id());
          return Optional.of(updated);
        });
  }

  public boolean deleteAnnualWithdrawal(long workspaceId, long appRecordId) {
    return deleteRecord(workspaceId, RecordTable.ANNUAL_WITHDRAWAL, appRecordId);
  }

  public Optional<AssetAccount> findAssetAccount(long workspaceId, long appRecordId) {
    return queryOptional(
        """
        select record.app_record_id, record.category_key, record.category_label, record.account, record.company, record.amount
        from financial_record_asset_account record
        join financial_record_snapshot snapshot on snapshot.id = record.snapshot_id
        where snapshot.active = true
          and snapshot.workspace_id = ?
          and record.app_record_id = ?
        order by record.id
        limit 1
        """,
        this::mapAssetAccount,
        workspaceId,
        appRecordId);
  }

  public AssetAccount createAssetAccount(long workspaceId, AssetAccount account) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate(workspaceId);
          AssetAccount created =
              account.withId(nextAppRecordId(snapshot.id(), RecordTable.ASSET_ACCOUNT));
          insertAssetAccount(snapshot.id(), created);
          bumpSnapshotVersion(snapshot.id());
          return created;
        });
  }

  public Optional<AssetAccount> updateAssetAccount(
      long workspaceId, long appRecordId, AssetAccount account) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate(workspaceId);
          AssetAccount updated = account.withId(appRecordId);
          int rows =
              jdbcTemplate.update(
                  """
                  update financial_record_asset_account
                  set category_key = ?,
                      category_label = ?,
                      account = ?,
                      company = ?,
                      amount = ?
                  where snapshot_id = ?
                    and app_record_id = ?
                  """,
                  updated.categoryKey(),
                  updated.categoryLabel(),
                  updated.account(),
                  updated.company(),
                  updated.amount(),
                  snapshot.id(),
                  appRecordId);
          if (rows == 0) {
            return Optional.empty();
          }
          bumpSnapshotVersion(snapshot.id());
          return Optional.of(updated);
        });
  }

  public boolean deleteAssetAccount(long workspaceId, long appRecordId) {
    return deleteRecord(workspaceId, RecordTable.ASSET_ACCOUNT, appRecordId);
  }

  public Optional<DebtAccount> findDebtAccount(long workspaceId, long appRecordId) {
    return queryOptional(
        """
        select record.app_record_id, record.account, record.company, record.amount
        from financial_record_debt_account record
        join financial_record_snapshot snapshot on snapshot.id = record.snapshot_id
        where snapshot.active = true
          and snapshot.workspace_id = ?
          and record.app_record_id = ?
        order by record.id
        limit 1
        """,
        this::mapDebtAccount,
        workspaceId,
        appRecordId);
  }

  public DebtAccount createDebtAccount(long workspaceId, DebtAccount account) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate(workspaceId);
          DebtAccount created =
              account.withId(nextAppRecordId(snapshot.id(), RecordTable.DEBT_ACCOUNT));
          insertDebtAccount(snapshot.id(), created);
          bumpSnapshotVersion(snapshot.id());
          return created;
        });
  }

  public Optional<DebtAccount> updateDebtAccount(
      long workspaceId, long appRecordId, DebtAccount account) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate(workspaceId);
          DebtAccount updated = account.withId(appRecordId);
          int rows =
              jdbcTemplate.update(
                  """
                  update financial_record_debt_account
                  set account = ?,
                      company = ?,
                      amount = ?
                  where snapshot_id = ?
                    and app_record_id = ?
                  """,
                  updated.account(),
                  updated.company(),
                  updated.amount(),
                  snapshot.id(),
                  appRecordId);
          if (rows == 0) {
            return Optional.empty();
          }
          bumpSnapshotVersion(snapshot.id());
          return Optional.of(updated);
        });
  }

  public boolean deleteDebtAccount(long workspaceId, long appRecordId) {
    return deleteRecord(workspaceId, RecordTable.DEBT_ACCOUNT, appRecordId);
  }

  public Optional<IncomeSummaryItem> findIncomeSummaryItem(long workspaceId, long appRecordId) {
    return queryOptional(
        """
        select record.app_record_id, record.category, record.interval, record.amount
        from financial_record_income_summary_item record
        join financial_record_snapshot snapshot on snapshot.id = record.snapshot_id
        where snapshot.active = true
          and snapshot.workspace_id = ?
          and record.app_record_id = ?
        order by record.id
        limit 1
        """,
        this::mapIncomeSummaryItem,
        workspaceId,
        appRecordId);
  }

  public IncomeSummaryItem createIncomeSummaryItem(long workspaceId, IncomeSummaryItem item) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate(workspaceId);
          IncomeSummaryItem created =
              item.withId(nextAppRecordId(snapshot.id(), RecordTable.INCOME_SUMMARY_ITEM));
          insertIncomeSummaryItem(snapshot.id(), created);
          bumpSnapshotVersion(snapshot.id());
          return created;
        });
  }

  public Optional<IncomeSummaryItem> updateIncomeSummaryItem(
      long workspaceId, long appRecordId, IncomeSummaryItem item) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate(workspaceId);
          IncomeSummaryItem updated = item.withId(appRecordId);
          int rows =
              jdbcTemplate.update(
                  """
                  update financial_record_income_summary_item
                  set category = ?,
                      interval = ?,
                      amount = ?
                  where snapshot_id = ?
                    and app_record_id = ?
                  """,
                  updated.category(),
                  updated.interval(),
                  updated.amount(),
                  snapshot.id(),
                  appRecordId);
          if (rows == 0) {
            return Optional.empty();
          }
          bumpSnapshotVersion(snapshot.id());
          return Optional.of(updated);
        });
  }

  public boolean deleteIncomeSummaryItem(long workspaceId, long appRecordId) {
    return deleteRecord(workspaceId, RecordTable.INCOME_SUMMARY_ITEM, appRecordId);
  }

  public Optional<IncomeEvent> findIncomeEvent(long workspaceId, long appRecordId) {
    return queryOptional(
        """
        select record.app_record_id, record.date, record.label, record.type, record.check_number
        from financial_record_income_event record
        join financial_record_snapshot snapshot on snapshot.id = record.snapshot_id
        where snapshot.active = true
          and snapshot.workspace_id = ?
          and record.app_record_id = ?
        order by record.id
        limit 1
        """,
        this::mapIncomeEvent,
        workspaceId,
        appRecordId);
  }

  public IncomeEvent createIncomeEvent(long workspaceId, IncomeEvent event) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate(workspaceId);
          IncomeEvent created =
              event.withId(nextAppRecordId(snapshot.id(), RecordTable.INCOME_EVENT));
          insertIncomeEvent(snapshot.id(), created);
          bumpSnapshotVersion(snapshot.id());
          return created;
        });
  }

  public Optional<IncomeEvent> updateIncomeEvent(
      long workspaceId, long appRecordId, IncomeEvent event) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate(workspaceId);
          IncomeEvent updated = event.withId(appRecordId);
          int rows =
              jdbcTemplate.update(
                  """
                  update financial_record_income_event
                  set date = ?,
                      label = ?,
                      type = ?,
                      check_number = ?
                  where snapshot_id = ?
                    and app_record_id = ?
                  """,
                  Date.valueOf(updated.date()),
                  updated.label(),
                  updated.type(),
                  updated.checkNumber(),
                  snapshot.id(),
                  appRecordId);
          if (rows == 0) {
            return Optional.empty();
          }
          bumpSnapshotVersion(snapshot.id());
          return Optional.of(updated);
        });
  }

  public boolean deleteIncomeEvent(long workspaceId, long appRecordId) {
    return deleteRecord(workspaceId, RecordTable.INCOME_EVENT, appRecordId);
  }

  public Optional<ImportantDate> findImportantDate(long workspaceId, long appRecordId) {
    return queryOptional(
        """
        select record.app_record_id, record.date, record.event, record.type
        from financial_record_important_date record
        join financial_record_snapshot snapshot on snapshot.id = record.snapshot_id
        where snapshot.active = true
          and snapshot.workspace_id = ?
          and record.app_record_id = ?
        order by record.id
        limit 1
        """,
        this::mapImportantDate,
        workspaceId,
        appRecordId);
  }

  public ImportantDate createImportantDate(long workspaceId, ImportantDate importantDate) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate(workspaceId);
          ImportantDate created =
              importantDate.withId(nextAppRecordId(snapshot.id(), RecordTable.IMPORTANT_DATE));
          insertImportantDate(snapshot.id(), created);
          bumpSnapshotVersion(snapshot.id());
          return created;
        });
  }

  public Optional<ImportantDate> updateImportantDate(
      long workspaceId, long appRecordId, ImportantDate importantDate) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate(workspaceId);
          ImportantDate updated = importantDate.withId(appRecordId);
          int rows =
              jdbcTemplate.update(
                  """
                  update financial_record_important_date
                  set date = ?,
                      event = ?,
                      type = ?
                  where snapshot_id = ?
                    and app_record_id = ?
                  """,
                  Date.valueOf(updated.date()),
                  updated.event(),
                  updated.type(),
                  snapshot.id(),
                  appRecordId);
          if (rows == 0) {
            return Optional.empty();
          }
          bumpSnapshotVersion(snapshot.id());
          return Optional.of(updated);
        });
  }

  public boolean deleteImportantDate(long workspaceId, long appRecordId) {
    return deleteRecord(workspaceId, RecordTable.IMPORTANT_DATE, appRecordId);
  }

  private record StoredSnapshot(
      long id,
      long version,
      java.time.LocalDate payPeriodStart,
      java.time.LocalDate payPeriodEnd) {}

  private long insertSnapshot(long workspaceId, FinancialSnapshot snapshot) {
    Long snapshotId =
        jdbcTemplate.queryForObject(
            """
            insert into financial_record_snapshot
                (workspace_id, active, version, pay_period_start, pay_period_end)
            values
                (?, true, ?, ?, ?)
            returning id
            """,
            Long.class,
            workspaceId,
            snapshot.version(),
            Date.valueOf(snapshot.payPeriodStart()),
            Date.valueOf(snapshot.payPeriodEnd()));
    if (snapshotId == null) {
      throw new IllegalStateException("PostgreSQL did not return the created snapshot ID");
    }

    writeBills(snapshotId, snapshot.bills());
    writeAnnualWithdrawals(snapshotId, snapshot.annualWithdrawals());
    writeAssetAccounts(snapshotId, snapshot.assetAccounts());
    writeDebtAccounts(snapshotId, snapshot.debtAccounts());
    writeIncomeSummaryItems(snapshotId, snapshot.incomeSummaryItems());
    writeIncomeEvents(snapshotId, snapshot.incomeEvents());
    writeImportantDates(snapshotId, snapshot.importantDates());
    return snapshotId;
  }

  private enum RecordTable {
    MONTHLY_BILL("financial_record_monthly_bill"),
    ANNUAL_WITHDRAWAL("financial_record_annual_withdrawal"),
    ASSET_ACCOUNT("financial_record_asset_account"),
    DEBT_ACCOUNT("financial_record_debt_account"),
    INCOME_SUMMARY_ITEM("financial_record_income_summary_item"),
    INCOME_EVENT("financial_record_income_event"),
    IMPORTANT_DATE("financial_record_important_date");

    private final String tableName;

    RecordTable(String tableName) {
      this.tableName = tableName;
    }
  }

  private Optional<StoredSnapshot> activeSnapshot(long workspaceId, boolean lockForUpdate) {
    String lockingClause = lockForUpdate ? " for update" : "";
    StoredSnapshot storedSnapshot =
        jdbcTemplate.query(
            """
            select id, version, pay_period_start, pay_period_end
            from financial_record_snapshot
            where active = true
              and workspace_id = ?
            order by id
            limit 1
            """
                + lockingClause,
            new Object[] {workspaceId},
            (resultSet) ->
                resultSet.next()
                    ? new StoredSnapshot(
                        resultSet.getLong("id"),
                        resultSet.getLong("version"),
                        resultSet.getDate("pay_period_start").toLocalDate(),
                        resultSet.getDate("pay_period_end").toLocalDate())
                    : null);
    return Optional.ofNullable(storedSnapshot);
  }

  private StoredSnapshot requireActiveSnapshotForUpdate(long workspaceId) {
    lockWorkspace(workspaceId);
    return activeSnapshot(workspaceId, true)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Cannot mutate financial records before a relational snapshot exists for workspace "
                        + workspaceId));
  }

  private void lockWorkspace(long workspaceId) {
    Long lockedWorkspaceId =
        jdbcTemplate.queryForObject(
            "select id from workspace where id = ? for update", Long.class, workspaceId);
    if (lockedWorkspaceId == null) {
      throw new IllegalStateException("Workspace " + workspaceId + " does not exist");
    }
  }

  private void bumpSnapshotVersion(long snapshotId) {
    jdbcTemplate.update(
        """
        update financial_record_snapshot
        set version = version + 1,
            updated_at = now()
        where id = ?
        """,
        snapshotId);
  }

  private long nextAppRecordId(long snapshotId, RecordTable table) {
    return jdbcTemplate.queryForObject(
        """
        select greatest(coalesce(max(app_record_id), 0), 0) + 1
        from
        """
            + table.tableName
            + """

        where snapshot_id = ?
        """,
        Long.class,
        snapshotId);
  }

  private boolean deleteRecord(long workspaceId, RecordTable table, long appRecordId) {
    return Boolean.TRUE.equals(
        transactionOperations.execute(
            (status) -> {
              StoredSnapshot snapshot = requireActiveSnapshotForUpdate(workspaceId);
              int rows =
                  jdbcTemplate.update(
                      "delete from "
                          + table.tableName
                          + " where snapshot_id = ? and app_record_id = ?",
                      snapshot.id(),
                      appRecordId);
              if (rows == 0) {
                return false;
              }
              bumpSnapshotVersion(snapshot.id());
              return true;
            }));
  }

  private <T> Optional<T> queryOptional(String sql, RowMapper<T> rowMapper, Object... args) {
    return jdbcTemplate.query(sql, rowMapper, args).stream().findFirst();
  }

  private List<ExpenseBill> bills(long snapshotId) {
    return jdbcTemplate.query(
        """
        select app_record_id, bill, due_day, amount, account, paid
        from financial_record_monthly_bill
        where snapshot_id = ?
        order by due_day, id
        """,
        this::mapBill,
        snapshotId);
  }

  private List<AnnualWithdrawal> annualWithdrawals(long snapshotId) {
    return jdbcTemplate.query(
        """
        select app_record_id, bill, month, day, amount, account, paid
        from financial_record_annual_withdrawal
        where snapshot_id = ?
        order by month, day, id
        """,
        this::mapAnnualWithdrawal,
        snapshotId);
  }

  private List<AssetAccount> assetAccounts(long snapshotId) {
    return jdbcTemplate.query(
        """
        select app_record_id, category_key, category_label, account, company, amount
        from financial_record_asset_account
        where snapshot_id = ?
        order by category_key, id
        """,
        this::mapAssetAccount,
        snapshotId);
  }

  private List<DebtAccount> debtAccounts(long snapshotId) {
    return jdbcTemplate.query(
        """
        select app_record_id, account, company, amount
        from financial_record_debt_account
        where snapshot_id = ?
        order by account, id
        """,
        this::mapDebtAccount,
        snapshotId);
  }

  private List<IncomeSummaryItem> incomeSummaryItems(long snapshotId) {
    return jdbcTemplate.query(
        """
        select app_record_id, category, interval, amount
        from financial_record_income_summary_item
        where snapshot_id = ?
        order by category, interval, id
        """,
        this::mapIncomeSummaryItem,
        snapshotId);
  }

  private List<IncomeEvent> incomeEvents(long snapshotId) {
    return jdbcTemplate.query(
        """
        select app_record_id, date, label, type, check_number
        from financial_record_income_event
        where snapshot_id = ?
        order by date, id
        """,
        this::mapIncomeEvent,
        snapshotId);
  }

  private List<ImportantDate> importantDates(long snapshotId) {
    return jdbcTemplate.query(
        """
        select app_record_id, date, event, type
        from financial_record_important_date
        where snapshot_id = ?
        order by date, id
        """,
        this::mapImportantDate,
        snapshotId);
  }

  private List<FinancialAuditEvent> auditEvents(long workspaceId) {
    return jdbcTemplate.query(
        """
        select event.app_event_id,
               event.occurred_at,
               event.action,
               event.resource_type,
               event.resource_id,
               event.version_before,
               event.version_after,
               event.summary,
               event.projection_pay_period_start,
               event.projection_pay_period_end,
               event.projection_monthly_bill_count,
               event.projection_annual_withdrawal_count,
               event.projection_asset_account_count,
               event.projection_debt_account_count,
               event.projection_income_summary_item_count,
               event.projection_income_event_count,
               event.projection_important_date_count,
               event.projection_total_monthly_expenses,
               event.projection_total_annual_withdrawals,
               event.projection_total_tracked_assets,
               event.projection_total_debt,
               event.projection_net_worth
        from financial_record_audit_event event
        join financial_record_snapshot snapshot on snapshot.id = event.snapshot_id
        where snapshot.workspace_id = ?
        order by event.occurred_at, event.id
        """,
        this::mapAuditEvent,
        workspaceId);
  }

  private ExpenseBill mapBill(ResultSet resultSet, int rowNumber) throws SQLException {
    return new ExpenseBill(
        resultSet.getLong("app_record_id"),
        resultSet.getString("bill"),
        resultSet.getInt("due_day"),
        resultSet.getBigDecimal("amount"),
        resultSet.getString("account"),
        resultSet.getBoolean("paid"));
  }

  private AnnualWithdrawal mapAnnualWithdrawal(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new AnnualWithdrawal(
        resultSet.getLong("app_record_id"),
        resultSet.getString("bill"),
        resultSet.getInt("month"),
        resultSet.getInt("day"),
        resultSet.getBigDecimal("amount"),
        resultSet.getString("account"),
        resultSet.getBoolean("paid"));
  }

  private AssetAccount mapAssetAccount(ResultSet resultSet, int rowNumber) throws SQLException {
    return new AssetAccount(
        resultSet.getLong("app_record_id"),
        resultSet.getString("category_key"),
        resultSet.getString("category_label"),
        resultSet.getString("account"),
        resultSet.getString("company"),
        resultSet.getBigDecimal("amount"));
  }

  private DebtAccount mapDebtAccount(ResultSet resultSet, int rowNumber) throws SQLException {
    return new DebtAccount(
        resultSet.getLong("app_record_id"),
        resultSet.getString("account"),
        resultSet.getString("company"),
        resultSet.getBigDecimal("amount"));
  }

  private IncomeSummaryItem mapIncomeSummaryItem(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new IncomeSummaryItem(
        resultSet.getLong("app_record_id"),
        resultSet.getString("category"),
        resultSet.getString("interval"),
        resultSet.getBigDecimal("amount"));
  }

  private IncomeEvent mapIncomeEvent(ResultSet resultSet, int rowNumber) throws SQLException {
    int checkNumber = resultSet.getInt("check_number");
    boolean checkNumberWasNull = resultSet.wasNull();
    return new IncomeEvent(
        resultSet.getLong("app_record_id"),
        resultSet.getDate("date").toLocalDate(),
        resultSet.getString("label"),
        resultSet.getString("type"),
        checkNumberWasNull ? null : checkNumber);
  }

  private ImportantDate mapImportantDate(ResultSet resultSet, int rowNumber) throws SQLException {
    return new ImportantDate(
        resultSet.getLong("app_record_id"),
        resultSet.getDate("date").toLocalDate(),
        resultSet.getString("event"),
        resultSet.getString("type"));
  }

  private FinancialAuditEvent mapAuditEvent(ResultSet resultSet, int rowNumber)
      throws SQLException {
    long resourceId = resultSet.getLong("resource_id");
    Long nullableResourceId = resultSet.wasNull() ? null : resourceId;
    return new FinancialAuditEvent(
        resultSet.getLong("app_event_id"),
        resultSet.getTimestamp("occurred_at").toInstant(),
        resultSet.getString("action"),
        resultSet.getString("resource_type"),
        nullableResourceId,
        resultSet.getLong("version_before"),
        resultSet.getLong("version_after"),
        resultSet.getString("summary"),
        new FinancialProjectionSummary(
            resultSet.getDate("projection_pay_period_start").toLocalDate(),
            resultSet.getDate("projection_pay_period_end").toLocalDate(),
            resultSet.getInt("projection_monthly_bill_count"),
            resultSet.getInt("projection_annual_withdrawal_count"),
            resultSet.getInt("projection_asset_account_count"),
            resultSet.getInt("projection_debt_account_count"),
            resultSet.getInt("projection_income_summary_item_count"),
            resultSet.getInt("projection_income_event_count"),
            resultSet.getInt("projection_important_date_count"),
            resultSet.getBigDecimal("projection_total_monthly_expenses"),
            resultSet.getBigDecimal("projection_total_annual_withdrawals"),
            resultSet.getBigDecimal("projection_total_tracked_assets"),
            resultSet.getBigDecimal("projection_total_debt"),
            resultSet.getBigDecimal("projection_net_worth")));
  }

  private void insertAuditEvent(long snapshotId, FinancialAuditEvent event) {
    FinancialProjectionSummary projection = event.projectionSummary();
    jdbcTemplate.update(
        """
        insert into financial_record_audit_event (
            snapshot_id,
            app_event_id,
            occurred_at,
            action,
            resource_type,
            resource_id,
            version_before,
            version_after,
            summary,
            projection_pay_period_start,
            projection_pay_period_end,
            projection_monthly_bill_count,
            projection_annual_withdrawal_count,
            projection_asset_account_count,
            projection_debt_account_count,
            projection_income_summary_item_count,
            projection_income_event_count,
            projection_important_date_count,
            projection_total_monthly_expenses,
            projection_total_annual_withdrawals,
            projection_total_tracked_assets,
            projection_total_debt,
            projection_net_worth
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        snapshotId,
        event.id(),
        Timestamp.from(event.occurredAt()),
        event.action(),
        event.resourceType(),
        event.resourceId(),
        event.versionBefore(),
        event.versionAfter(),
        event.summary(),
        Date.valueOf(projection.payPeriodStart()),
        Date.valueOf(projection.payPeriodEnd()),
        projection.monthlyBillCount(),
        projection.annualWithdrawalCount(),
        projection.assetAccountCount(),
        projection.debtAccountCount(),
        projection.incomeSummaryItemCount(),
        projection.incomeEventCount(),
        projection.importantDateCount(),
        projection.totalMonthlyExpenses(),
        projection.totalAnnualWithdrawals(),
        projection.totalTrackedAssets(),
        projection.totalDebt(),
        projection.netWorth());
  }

  private void writeBills(Long snapshotId, List<ExpenseBill> bills) {
    bills.forEach((bill) -> insertBill(snapshotId, bill));
  }

  private void insertBill(Long snapshotId, ExpenseBill bill) {
    jdbcTemplate.update(
        """
        insert into financial_record_monthly_bill
            (snapshot_id, app_record_id, bill, due_day, amount, account, paid)
        values (?, ?, ?, ?, ?, ?, ?)
        """,
        snapshotId,
        bill.id(),
        bill.bill(),
        bill.dueDay(),
        bill.amount(),
        bill.account(),
        bill.paid());
  }

  private void writeAnnualWithdrawals(Long snapshotId, List<AnnualWithdrawal> annualWithdrawals) {
    annualWithdrawals.forEach((withdrawal) -> insertAnnualWithdrawal(snapshotId, withdrawal));
  }

  private void insertAnnualWithdrawal(Long snapshotId, AnnualWithdrawal withdrawal) {
    jdbcTemplate.update(
        """
        insert into financial_record_annual_withdrawal
            (snapshot_id, app_record_id, bill, month, day, amount, account, paid)
        values (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        snapshotId,
        withdrawal.id(),
        withdrawal.bill(),
        withdrawal.month(),
        withdrawal.day(),
        withdrawal.amount(),
        withdrawal.account(),
        withdrawal.paid());
  }

  private void writeAssetAccounts(Long snapshotId, List<AssetAccount> assetAccounts) {
    assetAccounts.forEach((account) -> insertAssetAccount(snapshotId, account));
  }

  private void insertAssetAccount(Long snapshotId, AssetAccount account) {
    jdbcTemplate.update(
        """
        insert into financial_record_asset_account
            (snapshot_id, app_record_id, category_key, category_label, account, company, amount)
        values (?, ?, ?, ?, ?, ?, ?)
        """,
        snapshotId,
        account.id(),
        account.categoryKey(),
        account.categoryLabel(),
        account.account(),
        account.company(),
        account.amount());
  }

  private void writeDebtAccounts(Long snapshotId, List<DebtAccount> debtAccounts) {
    debtAccounts.forEach((account) -> insertDebtAccount(snapshotId, account));
  }

  private void insertDebtAccount(Long snapshotId, DebtAccount account) {
    jdbcTemplate.update(
        """
        insert into financial_record_debt_account
            (snapshot_id, app_record_id, account, company, amount)
        values (?, ?, ?, ?, ?)
        """,
        snapshotId,
        account.id(),
        account.account(),
        account.company(),
        account.amount());
  }

  private void writeIncomeSummaryItems(
      Long snapshotId, List<IncomeSummaryItem> incomeSummaryItems) {
    incomeSummaryItems.forEach((item) -> insertIncomeSummaryItem(snapshotId, item));
  }

  private void insertIncomeSummaryItem(Long snapshotId, IncomeSummaryItem item) {
    jdbcTemplate.update(
        """
        insert into financial_record_income_summary_item
            (snapshot_id, app_record_id, category, interval, amount)
        values (?, ?, ?, ?, ?)
        """,
        snapshotId,
        item.id(),
        item.category(),
        item.interval(),
        item.amount());
  }

  private void writeIncomeEvents(Long snapshotId, List<IncomeEvent> incomeEvents) {
    incomeEvents.forEach((event) -> insertIncomeEvent(snapshotId, event));
  }

  private void insertIncomeEvent(Long snapshotId, IncomeEvent event) {
    jdbcTemplate.update(
        """
        insert into financial_record_income_event
            (snapshot_id, app_record_id, date, label, type, check_number)
        values (?, ?, ?, ?, ?, ?)
        """,
        snapshotId,
        event.id(),
        Date.valueOf(event.date()),
        event.label(),
        event.type(),
        event.checkNumber());
  }

  private void writeImportantDates(Long snapshotId, List<ImportantDate> importantDates) {
    importantDates.forEach((importantDate) -> insertImportantDate(snapshotId, importantDate));
  }

  private void insertImportantDate(Long snapshotId, ImportantDate importantDate) {
    jdbcTemplate.update(
        """
        insert into financial_record_important_date
            (snapshot_id, app_record_id, date, event, type)
        values (?, ?, ?, ?, ?)
        """,
        snapshotId,
        importantDate.id(),
        Date.valueOf(importantDate.date()),
        importantDate.event(),
        importantDate.type());
  }
}
