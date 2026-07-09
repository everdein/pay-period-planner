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
        ('flyway_schema_history')
)
SELECT
    expected.table_name,
    to_regclass('public.' || expected.table_name) IS NOT NULL AS present
FROM expected
ORDER BY expected.table_name;

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
    jsonb_array_length(COALESCE(snapshot_json->'importantDates', '[]'::jsonb)) AS important_date_count
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
}
finally {
    $env:PGPASSWORD = $previousPassword
}

exit 0
