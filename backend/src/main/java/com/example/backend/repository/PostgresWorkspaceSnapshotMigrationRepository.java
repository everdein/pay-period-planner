package com.example.backend.repository;

import com.example.backend.domain.financials.FinancialAuditEvent;
import com.example.backend.domain.financials.FinancialProjectionSummary;
import com.example.backend.domain.migration.FinancialSnapshotCounts;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresWorkspaceSnapshotMigrationRepository
    implements WorkspaceSnapshotMigrationRepository {

  private final JdbcTemplate jdbcTemplate;

  public PostgresWorkspaceSnapshotMigrationRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<LegacyJsonbSnapshot> findLegacyJsonbSnapshot() {
    return jdbcTemplate
        .query(
            """
            select id, version, snapshot_json::text
            from financial_snapshot_document
            where active = true
            order by id
            limit 1
            """,
            (resultSet, rowNumber) ->
                new LegacyJsonbSnapshot(
                    resultSet.getLong("id"),
                    resultSet.getLong("version"),
                    resultSet.getString("snapshot_json")))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<DestinationWorkspace> findOwnerDestination(String email, long workspaceId) {
    return jdbcTemplate
        .query(
            """
            select app_user.id as user_id, app_user.email, workspace.id as workspace_id,
                   workspace.name as workspace_name
            from application_user app_user
            join workspace_membership membership on membership.user_id = app_user.id
            join workspace on workspace.id = membership.workspace_id
            where app_user.normalized_email = lower(btrim(?))
              and app_user.status = 'active'
              and workspace.id = ?
              and membership.role = 'owner'
            """,
            (resultSet, rowNumber) ->
                new DestinationWorkspace(
                    resultSet.getLong("user_id"),
                    resultSet.getString("email"),
                    resultSet.getLong("workspace_id"),
                    resultSet.getString("workspace_name")),
            email,
            workspaceId)
        .stream()
        .findFirst();
  }

  @Override
  public void attachSourceDocument(long snapshotId, Long sourceDocumentId) {
    if (sourceDocumentId == null) {
      return;
    }
    int updated =
        jdbcTemplate.update(
            """
            update financial_record_snapshot
            set source_document_id = ?
            where id = ?
            """,
            sourceDocumentId,
            snapshotId);
    if (updated != 1) {
      throw new IllegalStateException("The migrated snapshot could not be linked to its source");
    }
  }

  @Override
  public void saveAuditEvents(long snapshotId, List<FinancialAuditEvent> auditEvents) {
    auditEvents.forEach((event) -> saveAuditEvent(snapshotId, event));
  }

  @Override
  public SnapshotMetadata snapshotMetadata(long snapshotId) {
    return jdbcTemplate.queryForObject(
        """
        select snapshot.active,
               snapshot.version,
               (select count(*) from financial_record_monthly_bill record
                   where record.snapshot_id = snapshot.id) as monthly_bill_count,
               (select count(*) from financial_record_annual_withdrawal record
                   where record.snapshot_id = snapshot.id) as annual_withdrawal_count,
               (select count(*) from financial_record_asset_account record
                   where record.snapshot_id = snapshot.id) as asset_account_count,
               (select count(*) from financial_record_debt_account record
                   where record.snapshot_id = snapshot.id) as debt_account_count,
               (select count(*) from financial_record_income_summary_item record
                   where record.snapshot_id = snapshot.id) as income_summary_item_count,
               (select count(*) from financial_record_income_event record
                   where record.snapshot_id = snapshot.id) as income_event_count,
               (select count(*) from financial_record_important_date record
                   where record.snapshot_id = snapshot.id) as important_date_count,
               (select count(*) from financial_record_audit_event record
                   where record.snapshot_id = snapshot.id) as audit_event_count
        from financial_record_snapshot snapshot
        where snapshot.id = ?
        """,
        (resultSet, rowNumber) ->
            new SnapshotMetadata(
                resultSet.getBoolean("active"), resultSet.getLong("version"), counts(resultSet)),
        snapshotId);
  }

  @Override
  public void createMigration(MigrationRecord migration) {
    FinancialSnapshotCounts counts = migration.expectedCounts();
    jdbcTemplate.update(
        """
        insert into financial_snapshot_workspace_migration (
            id,
            source_kind,
            source_fingerprint,
            source_version,
            source_document_id,
            destination_user_id,
            workspace_id,
            migrated_snapshot_id,
            monthly_bill_count,
            annual_withdrawal_count,
            asset_account_count,
            debt_account_count,
            income_summary_item_count,
            income_event_count,
            important_date_count,
            audit_event_count,
            status,
            applied_at,
            rolled_back_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        migration.id(),
        migration.sourceKind(),
        migration.sourceFingerprint(),
        migration.sourceVersion(),
        migration.sourceDocumentId(),
        migration.destinationUserId(),
        migration.workspaceId(),
        migration.snapshotId(),
        counts.monthlyBills(),
        counts.annualWithdrawals(),
        counts.assetAccounts(),
        counts.debtAccounts(),
        counts.incomeSummaryItems(),
        counts.incomeEvents(),
        counts.importantDates(),
        counts.auditEvents(),
        migration.status(),
        Timestamp.from(migration.appliedAt()),
        timestamp(migration.rolledBackAt()));
  }

  @Override
  public Optional<MigrationRecord> findMigration(UUID migrationId) {
    return jdbcTemplate
        .query(
            """
            select migration.id,
                   migration.source_kind,
                   migration.source_fingerprint,
                   migration.source_version,
                   migration.source_document_id,
                   migration.destination_user_id,
                   app_user.email as destination_email,
                   migration.workspace_id,
                   workspace.name as workspace_name,
                   migration.migrated_snapshot_id,
                   migration.monthly_bill_count,
                   migration.annual_withdrawal_count,
                   migration.asset_account_count,
                   migration.debt_account_count,
                   migration.income_summary_item_count,
                   migration.income_event_count,
                   migration.important_date_count,
                   migration.audit_event_count,
                   migration.status,
                   migration.applied_at,
                   migration.rolled_back_at
            from financial_snapshot_workspace_migration migration
            join application_user app_user on app_user.id = migration.destination_user_id
            join workspace on workspace.id = migration.workspace_id
            where migration.id = ?
            """,
            (resultSet, rowNumber) ->
                new MigrationRecord(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("source_kind"),
                    resultSet.getString("source_fingerprint"),
                    resultSet.getLong("source_version"),
                    nullableLong(resultSet, "source_document_id"),
                    resultSet.getLong("destination_user_id"),
                    resultSet.getString("destination_email"),
                    resultSet.getLong("workspace_id"),
                    resultSet.getString("workspace_name"),
                    resultSet.getLong("migrated_snapshot_id"),
                    counts(resultSet),
                    resultSet.getString("status"),
                    resultSet.getTimestamp("applied_at").toInstant(),
                    instant(resultSet.getTimestamp("rolled_back_at"))),
            migrationId)
        .stream()
        .findFirst();
  }

  @Override
  public boolean markRolledBack(UUID migrationId, Instant rolledBackAt) {
    return jdbcTemplate.update(
            """
            update financial_snapshot_workspace_migration
            set status = 'rolled_back',
                rolled_back_at = ?
            where id = ?
              and status = 'applied'
            """,
            Timestamp.from(rolledBackAt),
            migrationId)
        == 1;
  }

  private void saveAuditEvent(long snapshotId, FinancialAuditEvent event) {
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

  private FinancialSnapshotCounts counts(java.sql.ResultSet resultSet)
      throws java.sql.SQLException {
    return new FinancialSnapshotCounts(
        resultSet.getLong("monthly_bill_count"),
        resultSet.getLong("annual_withdrawal_count"),
        resultSet.getLong("asset_account_count"),
        resultSet.getLong("debt_account_count"),
        resultSet.getLong("income_summary_item_count"),
        resultSet.getLong("income_event_count"),
        resultSet.getLong("important_date_count"),
        resultSet.getLong("audit_event_count"));
  }

  private Long nullableLong(java.sql.ResultSet resultSet, String column)
      throws java.sql.SQLException {
    long value = resultSet.getLong(column);
    return resultSet.wasNull() ? null : value;
  }

  private Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
