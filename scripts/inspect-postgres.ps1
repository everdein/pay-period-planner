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

SELECT current_user AS connected_user, current_database() AS connected_database;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE'
ORDER BY table_name;

SELECT
    count(*) AS document_rows,
    count(*) FILTER (WHERE active) AS active_rows,
    max(version) AS latest_version,
    max(updated_at) AS latest_update
FROM financial_snapshot_document;

SELECT
    id,
    active,
    version,
    jsonb_typeof(snapshot_json) AS snapshot_type,
    jsonb_array_length(COALESCE(snapshot_json->'bills', '[]'::jsonb)) AS bill_count,
    jsonb_array_length(COALESCE(snapshot_json->'annualWithdrawals', '[]'::jsonb)) AS annual_withdrawal_count,
    jsonb_array_length(COALESCE(snapshot_json->'incomeSummaryItems', '[]'::jsonb)) AS income_summary_count,
    jsonb_array_length(COALESCE(snapshot_json->'incomeEvents', '[]'::jsonb)) AS income_event_count
FROM financial_snapshot_document
ORDER BY id;

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
}
finally {
    $env:PGPASSWORD = $previousPassword
}
