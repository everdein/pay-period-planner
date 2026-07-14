param(
    [string]$PostgresVersion = "18",
    [string]$HostName = "localhost",
    [string]$Port = "5432",
    [string]$Database = "financial_app",
    [string]$User = "financial_app_user",
    [string]$Password = "financial_app_password"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$psqlPath = "C:\Program Files\PostgreSQL\$PostgresVersion\bin\psql.exe"
if (-not (Test-Path $psqlPath)) {
    $psql = Get-Command "psql.exe" -ErrorAction SilentlyContinue
    if ($null -eq $psql) {
        throw "Could not find psql.exe for PostgreSQL $PostgresVersion or on PATH."
    }
    $psqlPath = $psql.Source
}

$sql = @"
BEGIN TRANSACTION READ ONLY;

SELECT
    current_user AS connected_user,
    current_database() AS connected_database,
    current_setting('transaction_read_only') AS transaction_read_only,
    current_setting('server_version') AS server_version;

WITH expected(table_name) AS (
    VALUES
        ('financial_snapshot'),
        ('monthly_withdrawal'),
        ('annual_withdrawal'),
        ('asset_account'),
        ('debt_account'),
        ('income_summary_item'),
        ('income_event'),
        ('important_date'),
        ('financial_snapshot_document'),
        ('financial_record_snapshot'),
        ('financial_record_monthly_bill'),
        ('financial_record_annual_withdrawal'),
        ('financial_record_asset_account'),
        ('financial_record_debt_account'),
        ('financial_record_income_summary_item'),
        ('financial_record_income_event'),
        ('financial_record_important_date'),
        ('financial_record_audit_event'),
        ('financial_snapshot_workspace_migration'),
        ('application_user'),
        ('workspace'),
        ('workspace_membership'),
        ('application_session'),
        ('flyway_schema_history')
)
SELECT
    expected.table_name,
    to_regclass('public.' || expected.table_name) IS NOT NULL AS present
FROM expected
ORDER BY expected.table_name;

WITH expected(index_name) AS (
    VALUES
        ('uq_financial_record_monthly_bill_snapshot_app_record'),
        ('uq_financial_record_annual_withdrawal_snapshot_app_record'),
        ('uq_financial_record_asset_account_snapshot_app_record'),
        ('uq_financial_record_debt_account_snapshot_app_record'),
        ('uq_financial_record_income_summary_item_snapshot_app_record'),
        ('uq_financial_record_income_event_snapshot_app_record'),
        ('uq_financial_record_important_date_snapshot_app_record'),
        ('uq_application_user_normalized_email'),
        ('ix_workspace_created_by_user'),
        ('uq_workspace_membership_owner'),
        ('ix_workspace_membership_user'),
        ('uq_application_session_token_hash'),
        ('ix_application_session_user_active'),
        ('ix_application_session_active_expiry'),
        ('uq_financial_record_snapshot_active_workspace'),
        ('uq_financial_record_snapshot_active_unowned'),
        ('ix_financial_record_snapshot_workspace'),
        ('uq_financial_record_audit_event_snapshot_app_event'),
        ('ix_financial_record_audit_event_snapshot_occurred'),
        ('uq_financial_snapshot_workspace_migration_applied_workspace'),
        ('ix_financial_snapshot_workspace_migration_destination')
)
SELECT
    expected.index_name,
    to_regclass('public.' || expected.index_name) IS NOT NULL AS present
FROM expected
ORDER BY expected.index_name;

SELECT
    column_name,
    data_type,
    is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'financial_record_snapshot'
  AND column_name = 'workspace_id';

SELECT
    constraint_name,
    constraint_type,
    convalidated AS validated
FROM information_schema.table_constraints table_constraint
JOIN pg_catalog.pg_constraint postgres_constraint
    ON postgres_constraint.conname = table_constraint.constraint_name
    AND postgres_constraint.conrelid = 'public.financial_record_snapshot'::regclass
WHERE table_constraint.table_schema = 'public'
  AND table_constraint.table_name = 'financial_record_snapshot'
  AND table_constraint.constraint_name IN (
      'financial_record_snapshot_workspace_id_fkey',
      'ck_financial_record_snapshot_workspace_required'
  )
ORDER BY constraint_name;

SELECT
    has_database_privilege(current_user, current_database(), 'CONNECT') AS can_connect,
    has_database_privilege(current_user, current_database(), 'CREATE') AS can_create_database_objects,
    has_schema_privilege(current_user, 'public', 'USAGE') AS public_schema_usage,
    has_schema_privilege(current_user, 'public', 'CREATE') AS public_schema_create;

SELECT
    table_name,
    has_table_privilege(current_user, format('%I.%I', table_schema, table_name), 'SELECT') AS can_select,
    has_table_privilege(current_user, format('%I.%I', table_schema, table_name), 'INSERT') AS can_insert,
    has_table_privilege(current_user, format('%I.%I', table_schema, table_name), 'UPDATE') AS can_update,
    has_table_privilege(current_user, format('%I.%I', table_schema, table_name), 'DELETE') AS can_delete
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE'
ORDER BY table_name;

ROLLBACK;
"@

$previousPassword = $env:PGPASSWORD
$env:PGPASSWORD = $Password
try {
    & $psqlPath -h $HostName -p $Port -U $User -d $Database `
        -v "ON_ERROR_STOP=1" -X -c $sql

    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL inspection failed with exit code $LASTEXITCODE."
    }

    $expectedTables = @(
        "financial_snapshot",
        "monthly_withdrawal",
        "annual_withdrawal",
        "asset_account",
        "debt_account",
        "income_summary_item",
        "income_event",
        "important_date",
        "financial_snapshot_document",
        "financial_record_snapshot",
        "financial_record_monthly_bill",
        "financial_record_annual_withdrawal",
        "financial_record_asset_account",
        "financial_record_debt_account",
        "financial_record_income_summary_item",
        "financial_record_income_event",
        "financial_record_important_date",
        "financial_record_audit_event",
        "financial_snapshot_workspace_migration",
        "application_user",
        "workspace",
        "workspace_membership",
        "application_session",
        "flyway_schema_history"
    )

    $existingTables = @(
        & $psqlPath -h $HostName -p $Port -U $User -d $Database `
            -v "ON_ERROR_STOP=1" -X -A -t `
            -c "SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname = 'public' ORDER BY tablename;"
    )
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL table discovery failed with exit code $LASTEXITCODE."
    }

    foreach ($table in $expectedTables) {
        if ($existingTables -contains $table) {
            $countSql = @"
BEGIN TRANSACTION READ ONLY;
SELECT '$table' AS table_name, count(*) AS row_count FROM public.$table;
ROLLBACK;
"@
            & $psqlPath -h $HostName -p $Port -U $User -d $Database `
                -v "ON_ERROR_STOP=1" -X -c $countSql
            if ($LASTEXITCODE -ne 0) {
                throw "Row count failed for $table with exit code $LASTEXITCODE."
            }
        }
    }

    if ($existingTables -contains "flyway_schema_history") {
        $flywaySql = @"
BEGIN TRANSACTION READ ONLY;
SELECT installed_rank, version, description, type, success, installed_on
FROM public.flyway_schema_history
ORDER BY installed_rank;
ROLLBACK;
"@
        & $psqlPath -h $HostName -p $Port -U $User -d $Database `
            -v "ON_ERROR_STOP=1" -X -c $flywaySql
        if ($LASTEXITCODE -ne 0) {
            throw "Flyway history inspection failed with exit code $LASTEXITCODE."
        }
    }

    if ($existingTables -contains "financial_snapshot_document") {
        $snapshotSql = @"
BEGIN TRANSACTION READ ONLY;
SELECT
    id,
    active,
    version,
    created_at,
    updated_at,
    jsonb_typeof(snapshot_json) AS snapshot_type,
    jsonb_array_length(COALESCE(snapshot_json->'bills', '[]'::jsonb)) AS bill_count,
    jsonb_array_length(COALESCE(snapshot_json->'annualWithdrawals', '[]'::jsonb)) AS annual_withdrawal_count,
    jsonb_array_length(COALESCE(snapshot_json->'assetAccounts', '[]'::jsonb)) AS asset_count,
    jsonb_array_length(COALESCE(snapshot_json->'debtAccounts', '[]'::jsonb)) AS debt_count,
    jsonb_array_length(COALESCE(snapshot_json->'incomeEvents', '[]'::jsonb)) AS income_event_count,
    jsonb_array_length(COALESCE(snapshot_json->'importantDates', '[]'::jsonb)) AS important_date_count,
    jsonb_array_length(COALESCE(snapshot_json->'auditEvents', '[]'::jsonb)) AS audit_event_count
FROM public.financial_snapshot_document
ORDER BY active DESC, id;
ROLLBACK;
"@
        & $psqlPath -h $HostName -p $Port -U $User -d $Database `
            -v "ON_ERROR_STOP=1" -X -c $snapshotSql
        if ($LASTEXITCODE -ne 0) {
            throw "Snapshot metadata inspection failed with exit code $LASTEXITCODE."
        }
    }

    if ($existingTables -contains "financial_record_snapshot") {
        $recordSnapshotSql = @"
BEGIN TRANSACTION READ ONLY;
SELECT
    snapshot.id,
    snapshot.workspace_id,
    snapshot.source_document_id,
    snapshot.active,
    snapshot.version,
    snapshot.created_at,
    snapshot.updated_at,
    (SELECT count(*) FROM public.financial_record_monthly_bill WHERE snapshot_id = snapshot.id) AS bill_count,
    (SELECT count(*) FROM public.financial_record_annual_withdrawal WHERE snapshot_id = snapshot.id) AS annual_withdrawal_count,
    (SELECT count(*) FROM public.financial_record_asset_account WHERE snapshot_id = snapshot.id) AS asset_count,
    (SELECT count(*) FROM public.financial_record_debt_account WHERE snapshot_id = snapshot.id) AS debt_count,
    (SELECT count(*) FROM public.financial_record_income_summary_item WHERE snapshot_id = snapshot.id) AS income_summary_item_count,
    (SELECT count(*) FROM public.financial_record_income_event WHERE snapshot_id = snapshot.id) AS income_event_count,
    (SELECT count(*) FROM public.financial_record_important_date WHERE snapshot_id = snapshot.id) AS important_date_count,
    (SELECT count(*) FROM public.financial_record_audit_event WHERE snapshot_id = snapshot.id) AS audit_event_count
FROM public.financial_record_snapshot snapshot
ORDER BY snapshot.active DESC, snapshot.id;
ROLLBACK;
"@
        & $psqlPath -h $HostName -p $Port -U $User -d $Database `
            -v "ON_ERROR_STOP=1" -X -c $recordSnapshotSql
        if ($LASTEXITCODE -ne 0) {
            throw "Relational snapshot metadata inspection failed with exit code $LASTEXITCODE."
        }
    }

    if ($existingTables -contains "financial_snapshot_workspace_migration") {
        $migrationSql = @"
BEGIN TRANSACTION READ ONLY;
SELECT
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
FROM public.financial_snapshot_workspace_migration
ORDER BY applied_at DESC, id;
ROLLBACK;
"@
        & $psqlPath -h $HostName -p $Port -U $User -d $Database `
            -v "ON_ERROR_STOP=1" -X -c $migrationSql
        if ($LASTEXITCODE -ne 0) {
            throw "Workspace migration metadata inspection failed with exit code $LASTEXITCODE."
        }
    }
}
finally {
    $env:PGPASSWORD = $previousPassword
}

exit 0
