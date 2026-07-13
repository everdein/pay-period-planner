param(
    [string]$PostgresVersion = "18",
    [string]$HostName = "localhost",
    [string]$Port = "5432",
    [string]$AdminDatabase = "postgres",
    [string]$AdminUser = "postgres",
    [string]$AppDatabase = "financial_app",
    [string]$AppUser = "financial_app_user",
    [string]$AppPassword = "financial_app_password"
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
Write-Host "Setting up local PostgreSQL for end-to-end-app..." -ForegroundColor Cyan
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

$v1Migration = Join-Path $repoRoot "backend\src\main\resources\db\migration\V1__create_financials_schema.sql"
$v2Migration = Join-Path $repoRoot "backend\src\main\resources\db\migration\V2__create_financial_snapshot_document.sql"
$v3Migration = Join-Path $repoRoot "backend\src\main\resources\db\migration\V3__create_financial_record_snapshot_schema.sql"
$v4Migration = Join-Path $repoRoot "backend\src\main\resources\db\migration\V4__add_financial_record_app_id_constraints.sql"

if (-not (Test-Path $v1Migration)) {
    throw "Missing migration file: $v1Migration"
}

if (-not (Test-Path $v2Migration)) {
    throw "Missing migration file: $v2Migration"
}

if (-not (Test-Path $v3Migration)) {
    throw "Missing migration file: $v3Migration"
}

if (-not (Test-Path $v4Migration)) {
    throw "Missing migration file: $v4Migration"
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
    Write-Host "Step 4: Running database migrations as app user..." -ForegroundColor Yellow

    $env:PGPASSWORD = $AppPassword

    $baseSchemaExists = Invoke-PsqlScalar -Arguments @(
        "-h", $HostName,
        "-p", $Port,
        "-U", $AppUser,
        "-d", $AppDatabase,
        "-t",
        "-A",
        "-v", "ON_ERROR_STOP=1",
        "-c", "SELECT CASE WHEN to_regclass('public.financial_snapshot') IS NULL THEN '0' ELSE '1' END;"
    )

    if ($baseSchemaExists -eq "1") {
        Write-Host "Base financial schema already exists. Skipping V1 migration." -ForegroundColor Gray
    }
    else {
        Invoke-Psql -Arguments @(
            "-h", $HostName,
            "-p", $Port,
            "-U", $AppUser,
            "-d", $AppDatabase,
            "-v", "ON_ERROR_STOP=1",
            "-f", $v1Migration
        )
    }

    $snapshotDocumentExists = Invoke-PsqlScalar -Arguments @(
        "-h", $HostName,
        "-p", $Port,
        "-U", $AppUser,
        "-d", $AppDatabase,
        "-t",
        "-A",
        "-v", "ON_ERROR_STOP=1",
        "-c", "SELECT CASE WHEN to_regclass('public.financial_snapshot_document') IS NULL THEN '0' ELSE '1' END;"
    )

    if ($snapshotDocumentExists -eq "1") {
        Write-Host "financial_snapshot_document already exists. Skipping V2 migration." -ForegroundColor Gray
    }
    else {
        Invoke-Psql -Arguments @(
            "-h", $HostName,
            "-p", $Port,
            "-U", $AppUser,
            "-d", $AppDatabase,
            "-v", "ON_ERROR_STOP=1",
            "-f", $v2Migration
        )
    }

    $recordSnapshotExists = Invoke-PsqlScalar -Arguments @(
        "-h", $HostName,
        "-p", $Port,
        "-U", $AppUser,
        "-d", $AppDatabase,
        "-t",
        "-A",
        "-v", "ON_ERROR_STOP=1",
        "-c", "SELECT CASE WHEN to_regclass('public.financial_record_snapshot') IS NULL THEN '0' ELSE '1' END;"
    )

    if ($recordSnapshotExists -eq "1") {
        Write-Host "financial_record_snapshot already exists. Skipping V3 migration." -ForegroundColor Gray
    }
    else {
        Invoke-Psql -Arguments @(
            "-h", $HostName,
            "-p", $Port,
            "-U", $AppUser,
            "-d", $AppDatabase,
            "-v", "ON_ERROR_STOP=1",
            "-f", $v3Migration
        )
    }

    $recordAppIdIndexesExist = Invoke-PsqlScalar -Arguments @(
        "-h", $HostName,
        "-p", $Port,
        "-U", $AppUser,
        "-d", $AppDatabase,
        "-t",
        "-A",
        "-v", "ON_ERROR_STOP=1",
        "-c", "SELECT CASE WHEN to_regclass('public.uq_financial_record_monthly_bill_snapshot_app_record') IS NULL THEN '0' ELSE '1' END;"
    )

    if ($recordAppIdIndexesExist -eq "1") {
        Write-Host "financial_record app-record indexes already exist. Skipping V4 migration." -ForegroundColor Gray
    }
    else {
        Invoke-Psql -Arguments @(
            "-h", $HostName,
            "-p", $Port,
            "-U", $AppUser,
            "-d", $AppDatabase,
            "-v", "ON_ERROR_STOP=1",
            "-f", $v4Migration
        )
    }

    Write-Host ""
    Write-Host "Step 5: Verifying financial snapshot tables..." -ForegroundColor Yellow

    Invoke-Psql -Arguments @(
        "-h", $HostName,
        "-p", $Port,
        "-U", $AppUser,
        "-d", $AppDatabase,
        "-v", "ON_ERROR_STOP=1",
        "-c", "SELECT count(*) AS financial_snapshot_document_rows FROM financial_snapshot_document;"
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
    Write-Host ".\scripts\start-backend-postgres.ps1" -ForegroundColor Cyan
    Write-Host ""
}
finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}
