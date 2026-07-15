param(
    [switch]$InstallBrowsers,
    [string]$DatabaseHost = "localhost",
    [int]$DatabasePort = 5432,
    [string]$DatabaseName = "financial_app",
    [string]$DatabaseUsername = "financial_app_user",
    [string]$DatabasePassword = "financial_app_password",
    [string]$PostgresVersion = "18",
    [int]$BackendPort = 18081,
    [int]$FrontendPort = 3001
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot

function Invoke-Step {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [scriptblock]$Command
    )

    Write-Host ""
    Write-Host $Name -ForegroundColor Cyan
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Name failed with exit code $LASTEXITCODE."
    }
}

function Resolve-PsqlPath {
    $versionedPath = "C:\Program Files\PostgreSQL\$PostgresVersion\bin\psql.exe"
    if (Test-Path $versionedPath) {
        return $versionedPath
    }

    $psql = Get-Command "psql.exe" -ErrorAction SilentlyContinue
    if ($null -eq $psql) {
        throw "Could not find psql.exe for PostgreSQL $PostgresVersion or on PATH."
    }

    return $psql.Source
}

$testSchema = $null
$psqlPath = $null
Push-Location $repoRoot
try {
    $testSchema = "financials_portfolio_" + [Guid]::NewGuid().ToString("N")
    $psqlPath = Resolve-PsqlPath
    $env:BROWSER_TEST_SCHEMA = $testSchema
    $env:BROWSER_TEST_DATABASE_URL =
        "jdbc:postgresql://${DatabaseHost}:${DatabasePort}/${DatabaseName}?currentSchema=${testSchema}"
    $env:BROWSER_TEST_BACKEND_PORT = [string]$BackendPort
    $env:BROWSER_TEST_FRONTEND_PORT = [string]$FrontendPort
    $env:DATABASE_USERNAME = $DatabaseUsername
    $env:DATABASE_PASSWORD = $DatabasePassword
    $env:PGPASSWORD = $DatabasePassword

    Write-Host "Portfolio capture schema: $testSchema" -ForegroundColor Gray

    if ($InstallBrowsers) {
        Invoke-Step "Install Playwright Chromium browser" {
            npm --prefix frontend exec playwright install chromium
        }
    }

    Invoke-Step "Capture synthetic portfolio evidence" {
        npm --prefix frontend run capture:portfolio
    }
}
finally {
    $cleanupFailure = $null
    if ($null -ne $psqlPath -and $null -ne $testSchema) {
        Write-Host ""
        Write-Host "Remove isolated portfolio capture schema" -ForegroundColor Cyan
        & $psqlPath -h $DatabaseHost -p $DatabasePort -U $DatabaseUsername -d $DatabaseName `
            -v ON_ERROR_STOP=1 -c "drop schema if exists $testSchema cascade;"
        if ($LASTEXITCODE -ne 0) {
            $cleanupFailure = "Portfolio capture schema cleanup failed with exit code $LASTEXITCODE."
        }
    }
    Pop-Location
    if ($null -ne $cleanupFailure) {
        throw $cleanupFailure
    }
}

Write-Host ""
Write-Host "Synthetic portfolio evidence captured in docs/images/portfolio." -ForegroundColor Green
exit 0
