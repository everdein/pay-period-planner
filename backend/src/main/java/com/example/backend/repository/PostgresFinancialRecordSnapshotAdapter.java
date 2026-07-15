package com.example.backend.repository;

import com.example.backend.domain.financials.AnnualWithdrawal;
import com.example.backend.domain.financials.AssetAccount;
import com.example.backend.domain.financials.DebtAccount;
import com.example.backend.domain.financials.ExpenseBill;
import com.example.backend.domain.financials.FinancialAuditEvent;
import com.example.backend.domain.financials.FinancialPlanningSettings;
import com.example.backend.domain.financials.FinancialProjectionRoles;
import com.example.backend.domain.financials.FinancialProjectionSummary;
import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.domain.financials.ImportantDate;
import com.example.backend.domain.financials.IncomeEvent;
import com.example.backend.domain.financials.IncomeSummaryItem;
import com.example.backend.domain.financials.PayCadence;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class PostgresFinancialRecordSnapshotAdapter implements WorkspaceFinancialSnapshotStore {

  private static final int WRITE_BATCH_SIZE = 100;

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
    Optional<StoredSnapshot> storedSnapshot = activeSnapshot(workspaceId, false);

    if (storedSnapshot.isEmpty()) {
      return Optional.empty();
    }

    long snapshotId = storedSnapshot.get().id();
    return Optional.of(
        new FinancialSnapshot(
            storedSnapshot.get().version(),
            storedSnapshot.get().payPeriodStart(),
            storedSnapshot.get().payPeriodEnd(),
            new FinancialPlanningSettings(
                storedSnapshot.get().payCadence(), storedSnapshot.get().planningTimeZone()),
            projectionRoles(snapshotId),
            bills(snapshotId),
            annualWithdrawals(snapshotId),
            assetAccounts(snapshotId),
            debtAccounts(snapshotId),
            incomeSummaryItems(snapshotId),
            incomeEvents(snapshotId),
            importantDates(snapshotId)));
  }

  public void replaceActiveSnapshot(
      long workspaceId,
      long expectedVersion,
      FinancialSnapshot replacementSnapshot,
      FinancialAuditEvent auditEvent) {
    if (replacementSnapshot.version() != expectedVersion + 1) {
      throw new IllegalArgumentException(
          "Replacement snapshot version must be exactly one greater than the expected version");
    }
    if (auditEvent.versionBefore() != expectedVersion
        || auditEvent.versionAfter() != replacementSnapshot.version()) {
      throw new IllegalArgumentException(
          "Audit event versions must describe the replacement snapshot transition");
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

          long appEventId = nextAuditEventId(workspaceId);
          long snapshotId = insertSnapshot(workspaceId, replacementSnapshot);
          insertAuditEvent(snapshotId, appEventId, auditEvent);
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
              if (activeSnapshot(workspaceId, false).isPresent()) {
                throw new DuplicateKeyException(
                    "The workspace already has an active financial snapshot");
              }
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

  private record StoredSnapshot(
      long id,
      long version,
      java.time.LocalDate payPeriodStart,
      java.time.LocalDate payPeriodEnd,
      PayCadence payCadence,
      String planningTimeZone) {}

  private long insertSnapshot(long workspaceId, FinancialSnapshot snapshot) {
    FinancialPlanningSettings planningSettings =
        snapshot.planningSettings() == null
            ? FinancialPlanningSettings.legacyDefaults()
            : snapshot.planningSettings();
    Long snapshotId =
        jdbcTemplate.queryForObject(
            """
            insert into financial_record_snapshot
                (workspace_id, active, version, pay_period_start, pay_period_end,
                 pay_cadence, planning_time_zone)
            values
                (?, true, ?, ?, ?, ?, ?)
            returning id
            """,
            Long.class,
            workspaceId,
            snapshot.version(),
            Date.valueOf(snapshot.payPeriodStart()),
            Date.valueOf(snapshot.payPeriodEnd()),
            planningSettings.payCadence().name(),
            planningSettings.timeZone());
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
    writeProjectionRoles(snapshotId, snapshot.projectionRoles());
    return snapshotId;
  }

  private Optional<StoredSnapshot> activeSnapshot(long workspaceId, boolean lockForUpdate) {
    String lockingClause = lockForUpdate ? " for update" : "";
    StoredSnapshot storedSnapshot =
        jdbcTemplate.query(
            """
            select id, version, pay_period_start, pay_period_end, pay_cadence, planning_time_zone
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
                        resultSet.getDate("pay_period_end").toLocalDate(),
                        PayCadence.valueOf(resultSet.getString("pay_cadence")),
                        resultSet.getString("planning_time_zone"))
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

  private FinancialProjectionRoles projectionRoles(long snapshotId) {
    Map<String, Long> roles =
        jdbcTemplate.query(
            """
            select role_key, app_record_id
            from financial_record_projection_role
            where snapshot_id = ?
            """,
            (resultSet) -> {
              Map<String, Long> values = new java.util.HashMap<>();
              while (resultSet.next()) {
                values.put(resultSet.getString("role_key"), resultSet.getLong("app_record_id"));
              }
              return values;
            },
            snapshotId);
    if (!roles
        .keySet()
        .containsAll(
            List.of(
                FinancialProjectionRoles.RENT_BILL,
                FinancialProjectionRoles.RENT_RESERVE_ASSET_ACCOUNT,
                FinancialProjectionRoles.PRIMARY_PAYCHECK_INCOME_SUMMARY_ITEM))) {
      return null;
    }
    return new FinancialProjectionRoles(
        roles.get(FinancialProjectionRoles.RENT_BILL),
        roles.get(FinancialProjectionRoles.RENT_RESERVE_ASSET_ACCOUNT),
        roles.get(FinancialProjectionRoles.PRIMARY_PAYCHECK_INCOME_SUMMARY_ITEM));
  }

  public List<FinancialAuditEvent> loadAuditEvents(long workspaceId, int limit) {
    if (limit < 1) {
      throw new IllegalArgumentException("Audit history limit must be at least 1");
    }

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
        order by event.occurred_at desc, event.id desc
        limit ?
        """,
        this::mapAuditEvent,
        workspaceId,
        limit);
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

  private long nextAuditEventId(long workspaceId) {
    Long currentMaximum =
        jdbcTemplate.queryForObject(
            """
            select coalesce(max(event.app_event_id), 0)
            from financial_record_audit_event event
            join financial_record_snapshot snapshot on snapshot.id = event.snapshot_id
            where snapshot.workspace_id = ?
            """,
            Long.class,
            workspaceId);
    return currentMaximum == null ? 1 : currentMaximum + 1;
  }

  private void insertAuditEvent(long snapshotId, long appEventId, FinancialAuditEvent event) {
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
        appEventId,
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
    batchUpdate(
        """
        insert into financial_record_monthly_bill
            (snapshot_id, app_record_id, bill, due_day, amount, account, paid)
        values (?, ?, ?, ?, ?, ?, ?)
        """,
        bills,
        (statement, bill) -> {
          statement.setLong(1, snapshotId);
          statement.setLong(2, bill.id());
          statement.setString(3, bill.bill());
          statement.setInt(4, bill.dueDay());
          statement.setBigDecimal(5, bill.amount());
          statement.setString(6, bill.account());
          statement.setBoolean(7, bill.paid());
        });
  }

  private void writeAnnualWithdrawals(Long snapshotId, List<AnnualWithdrawal> annualWithdrawals) {
    batchUpdate(
        """
        insert into financial_record_annual_withdrawal
            (snapshot_id, app_record_id, bill, month, day, amount, account, paid)
        values (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        annualWithdrawals,
        (statement, withdrawal) -> {
          statement.setLong(1, snapshotId);
          statement.setLong(2, withdrawal.id());
          statement.setString(3, withdrawal.bill());
          statement.setInt(4, withdrawal.month());
          statement.setInt(5, withdrawal.day());
          statement.setBigDecimal(6, withdrawal.amount());
          statement.setString(7, withdrawal.account());
          statement.setBoolean(8, withdrawal.paid());
        });
  }

  private void writeAssetAccounts(Long snapshotId, List<AssetAccount> assetAccounts) {
    batchUpdate(
        """
        insert into financial_record_asset_account
            (snapshot_id, app_record_id, category_key, category_label, account, company, amount)
        values (?, ?, ?, ?, ?, ?, ?)
        """,
        assetAccounts,
        (statement, account) -> {
          statement.setLong(1, snapshotId);
          statement.setLong(2, account.id());
          statement.setString(3, account.categoryKey());
          statement.setString(4, account.categoryLabel());
          statement.setString(5, account.account());
          statement.setString(6, account.company());
          statement.setBigDecimal(7, account.amount());
        });
  }

  private void writeDebtAccounts(Long snapshotId, List<DebtAccount> debtAccounts) {
    batchUpdate(
        """
        insert into financial_record_debt_account
            (snapshot_id, app_record_id, account, company, amount)
        values (?, ?, ?, ?, ?)
        """,
        debtAccounts,
        (statement, account) -> {
          statement.setLong(1, snapshotId);
          statement.setLong(2, account.id());
          statement.setString(3, account.account());
          statement.setString(4, account.company());
          statement.setBigDecimal(5, account.amount());
        });
  }

  private void writeIncomeSummaryItems(
      Long snapshotId, List<IncomeSummaryItem> incomeSummaryItems) {
    batchUpdate(
        """
        insert into financial_record_income_summary_item
            (snapshot_id, app_record_id, category, interval, amount)
        values (?, ?, ?, ?, ?)
        """,
        incomeSummaryItems,
        (statement, item) -> {
          statement.setLong(1, snapshotId);
          statement.setLong(2, item.id());
          statement.setString(3, item.category());
          statement.setString(4, item.interval());
          statement.setBigDecimal(5, item.amount());
        });
  }

  private void writeIncomeEvents(Long snapshotId, List<IncomeEvent> incomeEvents) {
    batchUpdate(
        """
        insert into financial_record_income_event
            (snapshot_id, app_record_id, date, label, type, check_number)
        values (?, ?, ?, ?, ?, ?)
        """,
        incomeEvents,
        (statement, event) -> {
          statement.setLong(1, snapshotId);
          statement.setLong(2, event.id());
          statement.setDate(3, Date.valueOf(event.date()));
          statement.setString(4, event.label());
          statement.setString(5, event.type());
          if (event.checkNumber() == null) {
            statement.setNull(6, Types.INTEGER);
          } else {
            statement.setInt(6, event.checkNumber());
          }
        });
  }

  private void writeImportantDates(Long snapshotId, List<ImportantDate> importantDates) {
    batchUpdate(
        """
        insert into financial_record_important_date
            (snapshot_id, app_record_id, date, event, type)
        values (?, ?, ?, ?, ?)
        """,
        importantDates,
        (statement, importantDate) -> {
          statement.setLong(1, snapshotId);
          statement.setLong(2, importantDate.id());
          statement.setDate(3, Date.valueOf(importantDate.date()));
          statement.setString(4, importantDate.event());
          statement.setString(5, importantDate.type());
        });
  }

  private void writeProjectionRoles(Long snapshotId, FinancialProjectionRoles roles) {
    if (roles == null) {
      return;
    }
    jdbcTemplate.batchUpdate(
        """
        insert into financial_record_projection_role (snapshot_id, role_key, app_record_id)
        values (?, ?, ?)
        """,
        List.of(
            new Object[] {snapshotId, FinancialProjectionRoles.RENT_BILL, roles.rentBillId()},
            new Object[] {
              snapshotId,
              FinancialProjectionRoles.RENT_RESERVE_ASSET_ACCOUNT,
              roles.rentReserveAssetAccountId()
            },
            new Object[] {
              snapshotId,
              FinancialProjectionRoles.PRIMARY_PAYCHECK_INCOME_SUMMARY_ITEM,
              roles.primaryPaycheckIncomeSummaryItemId()
            }));
  }

  private <T> void batchUpdate(
      String sql, List<T> records, ParameterizedPreparedStatementSetter<T> setter) {
    if (!records.isEmpty()) {
      jdbcTemplate.batchUpdate(sql, records, WRITE_BATCH_SIZE, setter);
    }
  }
}
