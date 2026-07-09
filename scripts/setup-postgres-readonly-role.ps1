param(
    [string]$PostgresVersion = "18",
    [string]$HostName = "localhost",
    [string]$Port = "5432",
    [string]$AdminDatabase = "postgres",
    [string]$AdminUser = "postgres",
    [string]$AppDatabase = "financial_app",
    [string]$AppUser = "financial_app_user",
    [string]$ReadOnlyUser = "financial_app_reader",
    [switch]$SkipPasswordUpdate
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Assert-SimplePostgresIdentifier {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    if ($Value -notmatch "^[a-z_][a-z0-9_]*$") {
        throw "$Name must be a simple lower-case PostgreSQL identifier. Received '$Value'."
    }
}

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

function ConvertTo-PostgresStringLiteral {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    $Value.Replace("'", "''")
}

function Invoke-Psql {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    & $psqlPath @Arguments

    if ($LASTEXITCODE -ne 0) {
        throw "psql failed with exit code $LASTEXITCODE."
    }
}

function Invoke-PsqlScalar {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $output = & $psqlPath @Arguments

    if ($LASTEXITCODE -ne 0) {
        throw "psql failed with exit code $LASTEXITCODE."
    }

    $firstValue = $output |
        Where-Object { $null -ne $_ -and $_.Trim() -ne "" } |
        Select-Object -First 1

    if ($null -eq $firstValue) {
        return ""
    }

    return $firstValue.Trim()
}

Assert-SimplePostgresIdentifier -Name "AppDatabase" -Value $AppDatabase
Assert-SimplePostgresIdentifier -Name "AppUser" -Value $AppUser
Assert-SimplePostgresIdentifier -Name "ReadOnlyUser" -Value $ReadOnlyUser

$psqlPath = "C:\Program Files\PostgreSQL\$PostgresVersion\bin\psql.exe"
if (-not (Test-Path $psqlPath)) {
    $psql = Get-Command "psql.exe" -ErrorAction SilentlyContinue
    if ($null -eq $psql) {
        throw "Could not find psql.exe for PostgreSQL $PostgresVersion or on PATH."
    }
    $psqlPath = $psql.Source
}

Write-Host ""
Write-Host "Setting up read-only PostgreSQL role for end-to-end-app..." -ForegroundColor Cyan
Write-Host ""
Write-Host "Using psql:" -ForegroundColor Gray
Write-Host $psqlPath
Write-Host ""

$adminPasswordSecure = Read-Host "Enter PostgreSQL admin password for user '$AdminUser'" -AsSecureString
$adminPassword = ConvertFrom-SecureStringToPlainText -SecureString $adminPasswordSecure

$readerPassword = $null
if (-not $SkipPasswordUpdate) {
    $readerPasswordSecure = Read-Host "Enter password to set for read-only user '$ReadOnlyUser'" -AsSecureString
    $readerPassword = ConvertFrom-SecureStringToPlainText -SecureString $readerPasswordSecure
}
else {
    $readerPasswordSecure = Read-Host "Enter existing password for read-only user '$ReadOnlyUser' to verify access" -AsSecureString
    $readerPassword = ConvertFrom-SecureStringToPlainText -SecureString $readerPasswordSecure
}

$previousPassword = $env:PGPASSWORD
try {
    $env:PGPASSWORD = $adminPassword

    Write-Host "Step 1: Creating or validating read-only login..." -ForegroundColor Yellow

    if ($SkipPasswordUpdate) {
        $roleSql = @"
DO `$`$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_catalog.pg_roles
        WHERE rolname = '$ReadOnlyUser'
    ) THEN
        RAISE EXCEPTION 'Role $ReadOnlyUser does not exist. Rerun without -SkipPasswordUpdate to create it.';
    END IF;
END
`$`$;
"@
    }
    else {
        $readerPasswordLiteral = ConvertTo-PostgresStringLiteral -Value $readerPassword
        $roleSql = @"
DO `$`$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_catalog.pg_roles
        WHERE rolname = '$ReadOnlyUser'
    ) THEN
        CREATE ROLE $ReadOnlyUser LOGIN PASSWORD '$readerPasswordLiteral';
    ELSE
        ALTER ROLE $ReadOnlyUser WITH LOGIN PASSWORD '$readerPasswordLiteral';
    END IF;
END
`$`$;
"@
    }

    Invoke-Psql -Arguments @(
        "-h", $HostName,
        "-p", $Port,
        "-U", $AdminUser,
        "-d", $AdminDatabase,
        "-v", "ON_ERROR_STOP=1",
        "-X",
        "-c", $roleSql
    )

    Write-Host ""
    Write-Host "Step 2: Granting read-only database privileges..." -ForegroundColor Yellow

    $databaseGrantSql = @"
GRANT CONNECT ON DATABASE $AppDatabase TO $ReadOnlyUser;
ALTER ROLE $ReadOnlyUser IN DATABASE $AppDatabase SET default_transaction_read_only = on;
"@

    Invoke-Psql -Arguments @(
        "-h", $HostName,
        "-p", $Port,
        "-U", $AdminUser,
        "-d", $AdminDatabase,
        "-v", "ON_ERROR_STOP=1",
        "-X",
        "-c", $databaseGrantSql
    )

    $schemaGrantSql = @"
GRANT USAGE ON SCHEMA public TO $ReadOnlyUser;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO $ReadOnlyUser;
ALTER DEFAULT PRIVILEGES FOR ROLE $AppUser IN SCHEMA public
GRANT SELECT ON TABLES TO $ReadOnlyUser;
REVOKE CREATE ON SCHEMA public FROM $ReadOnlyUser;
"@

    Invoke-Psql -Arguments @(
        "-h", $HostName,
        "-p", $Port,
        "-U", $AdminUser,
        "-d", $AppDatabase,
        "-v", "ON_ERROR_STOP=1",
        "-X",
        "-c", $schemaGrantSql
    )

    Write-Host ""
    Write-Host "Step 3: Verifying role as read-only user..." -ForegroundColor Yellow

    $env:PGPASSWORD = $readerPassword

    $verificationSql = @"
BEGIN TRANSACTION READ ONLY;

SELECT
    current_user AS connected_user,
    current_database() AS connected_database,
    current_setting('transaction_read_only') AS transaction_read_only,
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

    Invoke-Psql -Arguments @(
        "-h", $HostName,
        "-p", $Port,
        "-U", $ReadOnlyUser,
        "-d", $AppDatabase,
        "-v", "ON_ERROR_STOP=1",
        "-X",
        "-c", $verificationSql
    )

    $writePrivilegeCount = Invoke-PsqlScalar -Arguments @(
        "-h", $HostName,
        "-p", $Port,
        "-U", $ReadOnlyUser,
        "-d", $AppDatabase,
        "-t",
        "-A",
        "-v", "ON_ERROR_STOP=1",
        "-X",
        "-c", "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND (has_table_privilege(current_user, format('%I.%I', table_schema, table_name), 'INSERT') OR has_table_privilege(current_user, format('%I.%I', table_schema, table_name), 'UPDATE') OR has_table_privilege(current_user, format('%I.%I', table_schema, table_name), 'DELETE'));"
    )

    if ($writePrivilegeCount -ne "0") {
        throw "Read-only role '$ReadOnlyUser' still has table write privileges."
    }

    $createPrivilegeCount = Invoke-PsqlScalar -Arguments @(
        "-h", $HostName,
        "-p", $Port,
        "-U", $ReadOnlyUser,
        "-d", $AppDatabase,
        "-t",
        "-A",
        "-v", "ON_ERROR_STOP=1",
        "-X",
        "-c", "SELECT CASE WHEN has_database_privilege(current_user, current_database(), 'CREATE') OR has_schema_privilege(current_user, 'public', 'CREATE') THEN '1' ELSE '0' END;"
    )

    if ($createPrivilegeCount -ne "0") {
        throw "Read-only role '$ReadOnlyUser' can still create database or public schema objects."
    }

    Write-Host ""
    Write-Host "Read-only PostgreSQL role setup complete." -ForegroundColor Green
    Write-Host "Use '$ReadOnlyUser' for MCP servers, reporting, and inspection tools." -ForegroundColor Cyan
    Write-Host "Do not use this role for the Spring Boot application runtime." -ForegroundColor Cyan
    Write-Host ""
}
finally {
    if ($null -eq $previousPassword) {
        Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    }
    else {
        $env:PGPASSWORD = $previousPassword
    }
}

exit 0
