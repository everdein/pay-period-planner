param(
    [string]$PostgresVersion = "18",
    [string]$HostName = "localhost",
    [string]$Port = "5432",
    [string]$AdminDatabase = "postgres",
    [string]$AdminUser = "postgres",
    [string]$AppDatabase = "financial_app",
    [string]$AppUser = "financial_app_user",
    [string]$AppPassword = "financial_app_password",
    [switch]$AdoptLegacySnapshotDocumentSchema,
    [switch]$AdoptLegacyV4Schema
)

$ErrorActionPreference = "Stop"

function ConvertFrom-SecureStringToPlainText {
    param(
        [Parameter(Mandatory = $true)]
        [System.Security.SecureString]$SecureString
    )

    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureString)

    try {
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

function Invoke-Psql {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    & $psqlPath @Arguments

    if ($LASTEXITCODE -ne 0) {
        throw "psql failed with exit code $LASTEXITCODE"
    }
}

function Invoke-PsqlScalar {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $output = & $psqlPath @Arguments

    if ($LASTEXITCODE -ne 0) {
        throw "psql failed with exit code $LASTEXITCODE"
    }

    $firstValue = $output |
        Where-Object { $null -ne $_ -and $_.Trim() -ne "" } |
        Select-Object -First 1

    if ($null -eq $firstValue) {
        return ""
    }

    return $firstValue.Trim()
}

Write-Host ""
Write-Host "Setting up local PostgreSQL for Pay Period Planner..." -ForegroundColor Cyan
Write-Host ""

$repoRoot = Split-Path -Parent $PSScriptRoot
$psqlPath = "C:\Program Files\PostgreSQL\$PostgresVersion\bin\psql.exe"

if (-not (Test-Path $psqlPath)) {
    $psqlCommand = Get-Command "psql.exe" -ErrorAction SilentlyContinue

    if ($null -eq $psqlCommand) {
        throw "Could not find psql.exe at '$psqlPath' and psql.exe is not on PATH."
    }

    $psqlPath = $psqlCommand.Source
}

$migrationScript = Join-Path $PSScriptRoot "migrate-postgres.ps1"

if (-not (Test-Path $migrationScript)) {
    throw "Missing Flyway migration script: $migrationScript"
}

if ($AdoptLegacySnapshotDocumentSchema -and $AdoptLegacyV4Schema) {
    throw "Choose only one legacy schema adoption mode."
}

Write-Host "Using psql:" -ForegroundColor Gray
Write-Host $psqlPath
Write-Host ""

$adminPasswordSecure = Read-Host "Enter PostgreSQL admin password for user '$AdminUser'" -AsSecureString
$adminPassword = ConvertFrom-SecureStringToPlainText -SecureString $adminPasswordSecure

try {
    Write-Host "Step 1: Creating or updating app database user..." -ForegroundColor Yellow

    $env:PGPASSWORD = $adminPassword

    $userSql = @"
DO `$`$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_catalog.pg_roles
        WHERE rolname = '$AppUser'
    ) THEN
        CREATE ROLE $AppUser LOGIN PASSWORD '$AppPassword';
    ELSE
        ALTER ROLE $AppUser WITH LOGIN PASSWORD '$AppPassword';
    END IF;
END
`$`$;
"@

    Invoke-Psql -Arguments @(
        "-h", $HostName,
        "-p", $Port,
        "-U", $AdminUser,
        "-d", $AdminDatabase,
        "-v", "ON_ERROR_STOP=1",
        "-c", $userSql
    )

    Write-Host ""
    Write-Host "Step 2: Creating app database if needed..." -ForegroundColor Yellow

    $databaseExists = Invoke-PsqlScalar -Arguments @(
        "-h", $HostName,
        "-p", $Port,
        "-U", $AdminUser,
        "-d", $AdminDatabase,
        "-t",
        "-A",
        "-v", "ON_ERROR_STOP=1",
        "-c", "SELECT CASE WHEN EXISTS (SELECT 1 FROM pg_database WHERE datname = '$AppDatabase') THEN '1' ELSE '0' END;"
        )

    if ($databaseExists -eq "1") {
        Write-Host "Database '$AppDatabase' already exists. Skipping create." -ForegroundColor Gray
    }
    else {
        Invoke-Psql -Arguments @(
            "-h", $HostName,
            "-p", $Port,
            "-U", $AdminUser,
            "-d", $AdminDatabase,
            "-v", "ON_ERROR_STOP=1",
            "-c", "CREATE DATABASE $AppDatabase OWNER $AppUser;"
        )
    }

    Write-Host ""
    Write-Host "Step 3: Ensuring database ownership and privileges..." -ForegroundColor Yellow

    Invoke-Psql -Arguments @(
        "-h", $HostName,
        "-p", $Port,
        "-U", $AdminUser,
        "-d", $AdminDatabase,
        "-v", "ON_ERROR_STOP=1",
        "-c", "ALTER DATABASE $AppDatabase OWNER TO $AppUser;"
    )

    Invoke-Psql -Arguments @(
        "-h", $HostName,
        "-p", $Port,
        "-U", $AdminUser,
        "-d", $AdminDatabase,
        "-v", "ON_ERROR_STOP=1",
        "-c", "GRANT ALL PRIVILEGES ON DATABASE $AppDatabase TO $AppUser;"
    )

    Write-Host ""
    Write-Host "Step 4: Checking Flyway migration state..." -ForegroundColor Yellow

    $env:PGPASSWORD = $AppPassword

    $appConnectionArguments = @(
        "-h", $HostName,
        "-p", $Port,
        "-U", $AppUser,
        "-d", $AppDatabase,
        "-t",
        "-A",
        "-v", "ON_ERROR_STOP=1"
    )
    $flywayHistoryExists = Invoke-PsqlScalar -Arguments (
        $appConnectionArguments + @(
            "-c", "SELECT CASE WHEN to_regclass('public.flyway_schema_history') IS NULL THEN '0' ELSE '1' END;"
        )
    )
    $publicApplicationTableCount = Invoke-PsqlScalar -Arguments (
        $appConnectionArguments + @(
            "-c", "SELECT count(*) FROM pg_catalog.pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history';"
        )
    )

    if (($AdoptLegacySnapshotDocumentSchema -or $AdoptLegacyV4Schema) -and $flywayHistoryExists -eq "1") {
        throw "Flyway history already exists; a legacy schema adoption mode is not applicable."
    }

    if (($AdoptLegacySnapshotDocumentSchema -or $AdoptLegacyV4Schema) -and $publicApplicationTableCount -eq "0") {
        throw "The public schema is empty; a legacy schema adoption mode is not applicable."
    }

    if ($flywayHistoryExists -eq "0" -and $publicApplicationTableCount -ne "0") {
        if (-not $AdoptLegacySnapshotDocumentSchema -and -not $AdoptLegacyV4Schema) {
            throw (
                "The public schema is non-empty but has no Flyway history. " +
                "Inspect and back up the database, then use the matching explicit legacy schema adoption mode."
            )
        }

        if ($AdoptLegacySnapshotDocumentSchema) {
            $existingLegacyTables = Invoke-PsqlScalar -Arguments (
                $appConnectionArguments + @(
                    "-c", "SELECT coalesce(string_agg(tablename, ',' ORDER BY tablename), '') FROM pg_catalog.pg_tables WHERE schemaname = 'public';"
                )
            )

            if ($existingLegacyTables -ne "financial_snapshot_document") {
                throw (
                    "Snapshot-document schema adoption refused because the table signature does not match V2. " +
                    "Tables: '$existingLegacyTables'."
                )
            }

            $snapshotDocumentIndexExists = Invoke-PsqlScalar -Arguments (
                $appConnectionArguments + @(
                    "-c", "SELECT CASE WHEN to_regclass('public.uq_financial_snapshot_document_active') IS NULL THEN '0' ELSE '1' END;"
                )
            )
            $snapshotDocumentActiveRowCount = Invoke-PsqlScalar -Arguments (
                $appConnectionArguments + @(
                    "-c", "SELECT count(*) FROM public.financial_snapshot_document WHERE active;"
                )
            )
            $snapshotDocumentColumnMismatch = Invoke-PsqlScalar -Arguments (
                $appConnectionArguments + @(
                    "-c", @"
WITH expected(column_name, data_type, is_nullable) AS (
    VALUES
        ('id', 'bigint', 'NO'),
        ('active', 'boolean', 'NO'),
        ('version', 'bigint', 'NO'),
        ('snapshot_json', 'jsonb', 'NO'),
        ('created_at', 'timestamp with time zone', 'NO'),
        ('updated_at', 'timestamp with time zone', 'NO')
)
SELECT coalesce(string_agg(expected.column_name, ',' ORDER BY expected.column_name), '')
FROM expected
LEFT JOIN information_schema.columns actual
    ON actual.table_schema = 'public'
    AND actual.table_name = 'financial_snapshot_document'
    AND actual.column_name = expected.column_name
    AND actual.data_type = expected.data_type
    AND actual.is_nullable = expected.is_nullable
WHERE actual.column_name IS NULL;
"@
                )
            )

            if (
                $snapshotDocumentColumnMismatch -ne "" -or
                [int]$snapshotDocumentActiveRowCount -gt 1
            ) {
                throw (
                    "Snapshot-document schema adoption refused because the object signature does not match V2. " +
                    "Tables: '$existingLegacyTables'. Active index present: '$snapshotDocumentIndexExists'. " +
                    "Missing or mismatched columns: '$snapshotDocumentColumnMismatch'. " +
                    "Active rows: '$snapshotDocumentActiveRowCount'."
                )
            }

            if ($snapshotDocumentIndexExists -ne "1") {
                Write-Host "Flyway V2 will restore the missing unique-active index." -ForegroundColor Gray
            }
        }

        if ($AdoptLegacyV4Schema) {
            $expectedLegacyTables = @(
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
                "financial_record_important_date"
            )
            $expectedLegacyIndexes = @(
                "uq_financial_snapshot_active",
                "ix_monthly_withdrawal_snapshot",
                "ix_annual_withdrawal_snapshot",
                "ix_asset_account_snapshot",
                "ix_debt_account_snapshot",
                "ix_income_event_snapshot",
                "ix_important_date_snapshot",
                "uq_financial_snapshot_document_active",
                "uq_financial_record_snapshot_active",
                "ix_financial_record_monthly_bill_snapshot",
                "ix_financial_record_annual_withdrawal_snapshot",
                "ix_financial_record_asset_account_snapshot",
                "ix_financial_record_debt_account_snapshot",
                "ix_financial_record_income_event_snapshot",
                "ix_financial_record_important_date_snapshot",
                "uq_financial_record_monthly_bill_snapshot_app_record",
                "uq_financial_record_annual_withdrawal_snapshot_app_record",
                "uq_financial_record_asset_account_snapshot_app_record",
                "uq_financial_record_debt_account_snapshot_app_record",
                "uq_financial_record_income_summary_item_snapshot_app_record",
                "uq_financial_record_income_event_snapshot_app_record",
                "uq_financial_record_important_date_snapshot_app_record"
            )
            $expectedTableValues = ($expectedLegacyTables | ForEach-Object { "('$_')" }) -join ","
            $expectedIndexValues = ($expectedLegacyIndexes | ForEach-Object { "('$_')" }) -join ","
            $expectedTableNames = ($expectedLegacyTables | ForEach-Object { "'$_'" }) -join ","

            $missingLegacyTables = Invoke-PsqlScalar -Arguments (
                $appConnectionArguments + @(
                    "-c", @"
WITH expected(name) AS (VALUES $expectedTableValues)
SELECT coalesce(string_agg(expected.name, ',' ORDER BY expected.name), '')
FROM expected
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_catalog.pg_class object
    JOIN pg_catalog.pg_namespace namespace ON namespace.oid = object.relnamespace
    WHERE object.relname = expected.name
      AND object.relkind IN ('r', 'p')
      AND namespace.nspname = 'public'
);
"@
                )
            )
            $missingLegacyIndexes = Invoke-PsqlScalar -Arguments (
                $appConnectionArguments + @(
                    "-c", @"
WITH expected(name) AS (VALUES $expectedIndexValues)
SELECT coalesce(string_agg(expected.name, ',' ORDER BY expected.name), '')
FROM expected
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_catalog.pg_class object
    JOIN pg_catalog.pg_namespace namespace ON namespace.oid = object.relnamespace
    WHERE object.relname = expected.name
      AND object.relkind = 'i'
      AND namespace.nspname = 'public'
);
"@
                )
            )
            $unexpectedLegacyTables = Invoke-PsqlScalar -Arguments (
                $appConnectionArguments + @(
                    "-c", @"
SELECT coalesce(string_agg(tablename, ',' ORDER BY tablename), '')
FROM pg_catalog.pg_tables
WHERE schemaname = 'public'
  AND tablename NOT IN ($expectedTableNames);
"@
                )
            )

            if ($missingLegacyTables -ne "" -or $missingLegacyIndexes -ne "" -or $unexpectedLegacyTables -ne "") {
                throw (
                    "Legacy schema adoption refused because the object signature does not match V1-V4. " +
                    "Missing tables: '$missingLegacyTables'. Missing indexes: '$missingLegacyIndexes'. " +
                    "Unexpected tables: '$unexpectedLegacyTables'."
                )
            }
        }
    }

    Write-Host "Running the Flyway-owned migration path..." -ForegroundColor Gray
    & $migrationScript `
        -DatabaseUrl "jdbc:postgresql://${HostName}:${Port}/${AppDatabase}" `
        -DatabaseUsername $AppUser `
        -DatabasePassword $AppPassword `
        -BaselineLegacySnapshotDocumentSchema:$AdoptLegacySnapshotDocumentSchema `
        -BaselineLegacyV4Schema:$AdoptLegacyV4Schema

    Write-Host ""
    Write-Host "Step 5: Verifying Flyway history and financial snapshot tables..." -ForegroundColor Yellow

    Invoke-Psql -Arguments @(
        "-h", $HostName,
        "-p", $Port,
        "-U", $AppUser,
        "-d", $AppDatabase,
        "-v", "ON_ERROR_STOP=1",
        "-c", "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
    )

    Invoke-Psql -Arguments @(
        "-h", $HostName,
        "-p", $Port,
        "-U", $AppUser,
        "-d", $AppDatabase,
        "-v", "ON_ERROR_STOP=1",
        "-c", "SELECT count(*) AS financial_record_snapshot_rows FROM financial_record_snapshot;"
    )

    Write-Host ""
    Write-Host "Local PostgreSQL setup complete." -ForegroundColor Green
    Write-Host ""
    Write-Host "You can now start the PostgreSQL-backed backend:"
    Write-Host ".\scripts\start-backend.ps1" -ForegroundColor Cyan
    Write-Host ""
}
finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}
