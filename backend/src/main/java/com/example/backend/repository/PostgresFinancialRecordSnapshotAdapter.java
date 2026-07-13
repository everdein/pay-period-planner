package com.example.backend.repository;

import com.example.backend.domain.financials.AnnualWithdrawal;
import com.example.backend.domain.financials.AssetAccount;
import com.example.backend.domain.financials.DebtAccount;
import com.example.backend.domain.financials.ExpenseBill;
import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.domain.financials.ImportantDate;
import com.example.backend.domain.financials.IncomeEvent;
import com.example.backend.domain.financials.IncomeSummaryItem;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
@Profile("postgres")
public class PostgresFinancialRecordSnapshotAdapter {

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

  public Optional<FinancialSnapshot> loadActiveSnapshot() {
    Optional<StoredSnapshot> storedSnapshot = activeSnapshot(false);

    if (storedSnapshot.isEmpty()) {
      return Optional.empty();
    }

    long snapshotId = storedSnapshot.get().id();
    return Optional.of(
        new FinancialSnapshot(
            storedSnapshot.get().version(),
            storedSnapshot.get().payPeriodStart(),
            storedSnapshot.get().payPeriodEnd(),
            bills(snapshotId),
            annualWithdrawals(snapshotId),
            assetAccounts(snapshotId),
            debtAccounts(snapshotId),
            incomeSummaryItems(snapshotId),
            incomeEvents(snapshotId),
            importantDates(snapshotId)));
  }

  public void replaceActiveSnapshot(FinancialSnapshot snapshot) {
    transactionOperations.executeWithoutResult(
        (status) -> {
          jdbcTemplate.update(
              """
              update financial_record_snapshot
              set active = false,
                  updated_at = now()
              where active = true
              """);

          Long snapshotId =
              jdbcTemplate.queryForObject(
                  """
                  insert into financial_record_snapshot
                      (active, version, pay_period_start, pay_period_end)
                  values
                      (true, ?, ?, ?)
                  returning id
                  """,
                  Long.class,
                  snapshot.version(),
                  Date.valueOf(snapshot.payPeriodStart()),
                  Date.valueOf(snapshot.payPeriodEnd()));

          writeBills(snapshotId, snapshot.bills());
          writeAnnualWithdrawals(snapshotId, snapshot.annualWithdrawals());
          writeAssetAccounts(snapshotId, snapshot.assetAccounts());
          writeDebtAccounts(snapshotId, snapshot.debtAccounts());
          writeIncomeSummaryItems(snapshotId, snapshot.incomeSummaryItems());
          writeIncomeEvents(snapshotId, snapshot.incomeEvents());
          writeImportantDates(snapshotId, snapshot.importantDates());
        });
  }

  public Optional<ExpenseBill> findBill(long appRecordId) {
    return queryOptional(
        """
        select record.app_record_id, record.bill, record.due_day, record.amount, record.account, record.paid
        from financial_record_monthly_bill record
        join financial_record_snapshot snapshot on snapshot.id = record.snapshot_id
        where snapshot.active = true
          and record.app_record_id = ?
        order by record.id
        limit 1
        """,
        this::mapBill,
        appRecordId);
  }

  public ExpenseBill createBill(ExpenseBill bill) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate();
          ExpenseBill created =
              bill.withId(nextAppRecordId(snapshot.id(), RecordTable.MONTHLY_BILL));
          insertBill(snapshot.id(), created);
          bumpSnapshotVersion(snapshot.id());
          return created;
        });
  }

  public Optional<ExpenseBill> updateBill(long appRecordId, ExpenseBill bill) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate();
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

  public boolean deleteBill(long appRecordId) {
    return deleteRecord(RecordTable.MONTHLY_BILL, appRecordId);
  }

  public Optional<AnnualWithdrawal> findAnnualWithdrawal(long appRecordId) {
    return queryOptional(
        """
        select record.app_record_id, record.bill, record.month, record.day, record.amount, record.account, record.paid
        from financial_record_annual_withdrawal record
        join financial_record_snapshot snapshot on snapshot.id = record.snapshot_id
        where snapshot.active = true
          and record.app_record_id = ?
        order by record.id
        limit 1
        """,
        this::mapAnnualWithdrawal,
        appRecordId);
  }

  public AnnualWithdrawal createAnnualWithdrawal(AnnualWithdrawal withdrawal) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate();
          AnnualWithdrawal created =
              withdrawal.withId(nextAppRecordId(snapshot.id(), RecordTable.ANNUAL_WITHDRAWAL));
          insertAnnualWithdrawal(snapshot.id(), created);
          bumpSnapshotVersion(snapshot.id());
          return created;
        });
  }

  public Optional<AnnualWithdrawal> updateAnnualWithdrawal(
      long appRecordId, AnnualWithdrawal withdrawal) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate();
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

  public boolean deleteAnnualWithdrawal(long appRecordId) {
    return deleteRecord(RecordTable.ANNUAL_WITHDRAWAL, appRecordId);
  }

  public Optional<AssetAccount> findAssetAccount(long appRecordId) {
    return queryOptional(
        """
        select record.app_record_id, record.category_key, record.category_label, record.account, record.company, record.amount
        from financial_record_asset_account record
        join financial_record_snapshot snapshot on snapshot.id = record.snapshot_id
        where snapshot.active = true
          and record.app_record_id = ?
        order by record.id
        limit 1
        """,
        this::mapAssetAccount,
        appRecordId);
  }

  public AssetAccount createAssetAccount(AssetAccount account) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate();
          AssetAccount created =
              account.withId(nextAppRecordId(snapshot.id(), RecordTable.ASSET_ACCOUNT));
          insertAssetAccount(snapshot.id(), created);
          bumpSnapshotVersion(snapshot.id());
          return created;
        });
  }

  public Optional<AssetAccount> updateAssetAccount(long appRecordId, AssetAccount account) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate();
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

  public boolean deleteAssetAccount(long appRecordId) {
    return deleteRecord(RecordTable.ASSET_ACCOUNT, appRecordId);
  }

  public Optional<DebtAccount> findDebtAccount(long appRecordId) {
    return queryOptional(
        """
        select record.app_record_id, record.account, record.company, record.amount
        from financial_record_debt_account record
        join financial_record_snapshot snapshot on snapshot.id = record.snapshot_id
        where snapshot.active = true
          and record.app_record_id = ?
        order by record.id
        limit 1
        """,
        this::mapDebtAccount,
        appRecordId);
  }

  public DebtAccount createDebtAccount(DebtAccount account) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate();
          DebtAccount created =
              account.withId(nextAppRecordId(snapshot.id(), RecordTable.DEBT_ACCOUNT));
          insertDebtAccount(snapshot.id(), created);
          bumpSnapshotVersion(snapshot.id());
          return created;
        });
  }

  public Optional<DebtAccount> updateDebtAccount(long appRecordId, DebtAccount account) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate();
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

  public boolean deleteDebtAccount(long appRecordId) {
    return deleteRecord(RecordTable.DEBT_ACCOUNT, appRecordId);
  }

  public Optional<IncomeSummaryItem> findIncomeSummaryItem(long appRecordId) {
    return queryOptional(
        """
        select record.app_record_id, record.category, record.interval, record.amount
        from financial_record_income_summary_item record
        join financial_record_snapshot snapshot on snapshot.id = record.snapshot_id
        where snapshot.active = true
          and record.app_record_id = ?
        order by record.id
        limit 1
        """,
        this::mapIncomeSummaryItem,
        appRecordId);
  }

  public IncomeSummaryItem createIncomeSummaryItem(IncomeSummaryItem item) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate();
          IncomeSummaryItem created =
              item.withId(nextAppRecordId(snapshot.id(), RecordTable.INCOME_SUMMARY_ITEM));
          insertIncomeSummaryItem(snapshot.id(), created);
          bumpSnapshotVersion(snapshot.id());
          return created;
        });
  }

  public Optional<IncomeSummaryItem> updateIncomeSummaryItem(
      long appRecordId, IncomeSummaryItem item) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate();
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

  public boolean deleteIncomeSummaryItem(long appRecordId) {
    return deleteRecord(RecordTable.INCOME_SUMMARY_ITEM, appRecordId);
  }

  public Optional<IncomeEvent> findIncomeEvent(long appRecordId) {
    return queryOptional(
        """
        select record.app_record_id, record.date, record.label, record.type, record.check_number
        from financial_record_income_event record
        join financial_record_snapshot snapshot on snapshot.id = record.snapshot_id
        where snapshot.active = true
          and record.app_record_id = ?
        order by record.id
        limit 1
        """,
        this::mapIncomeEvent,
        appRecordId);
  }

  public IncomeEvent createIncomeEvent(IncomeEvent event) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate();
          IncomeEvent created =
              event.withId(nextAppRecordId(snapshot.id(), RecordTable.INCOME_EVENT));
          insertIncomeEvent(snapshot.id(), created);
          bumpSnapshotVersion(snapshot.id());
          return created;
        });
  }

  public Optional<IncomeEvent> updateIncomeEvent(long appRecordId, IncomeEvent event) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate();
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

  public boolean deleteIncomeEvent(long appRecordId) {
    return deleteRecord(RecordTable.INCOME_EVENT, appRecordId);
  }

  public Optional<ImportantDate> findImportantDate(long appRecordId) {
    return queryOptional(
        """
        select record.app_record_id, record.date, record.event, record.type
        from financial_record_important_date record
        join financial_record_snapshot snapshot on snapshot.id = record.snapshot_id
        where snapshot.active = true
          and record.app_record_id = ?
        order by record.id
        limit 1
        """,
        this::mapImportantDate,
        appRecordId);
  }

  public ImportantDate createImportantDate(ImportantDate importantDate) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate();
          ImportantDate created =
              importantDate.withId(nextAppRecordId(snapshot.id(), RecordTable.IMPORTANT_DATE));
          insertImportantDate(snapshot.id(), created);
          bumpSnapshotVersion(snapshot.id());
          return created;
        });
  }

  public Optional<ImportantDate> updateImportantDate(
      long appRecordId, ImportantDate importantDate) {
    return transactionOperations.execute(
        (status) -> {
          StoredSnapshot snapshot = requireActiveSnapshotForUpdate();
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

  public boolean deleteImportantDate(long appRecordId) {
    return deleteRecord(RecordTable.IMPORTANT_DATE, appRecordId);
  }

  private record StoredSnapshot(
      long id,
      long version,
      java.time.LocalDate payPeriodStart,
      java.time.LocalDate payPeriodEnd) {}

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

  private Optional<StoredSnapshot> activeSnapshot(boolean lockForUpdate) {
    String lockingClause = lockForUpdate ? " for update" : "";
    StoredSnapshot storedSnapshot =
        jdbcTemplate.query(
            """
            select id, version, pay_period_start, pay_period_end
            from financial_record_snapshot
            where active = true
            order by id
            limit 1
            """
                + lockingClause,
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

  private StoredSnapshot requireActiveSnapshotForUpdate() {
    return activeSnapshot(true)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Cannot mutate financial records before a relational snapshot exists"));
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

  private boolean deleteRecord(RecordTable table, long appRecordId) {
    return Boolean.TRUE.equals(
        transactionOperations.execute(
            (status) -> {
              StoredSnapshot snapshot = requireActiveSnapshotForUpdate();
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
