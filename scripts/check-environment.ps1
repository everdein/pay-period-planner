param(
    [switch]$IncludePostgres
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$failures = [System.Collections.Generic.List[string]]::new()

function Test-Command {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string[]]$VersionArguments
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        $failures.Add("$Name is not available on PATH.")
        return
    }

    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $version = & $command.Source @VersionArguments 2>&1 | Select-Object -First 1
    }
    finally {
        $ErrorActionPreference = $previousErrorAction
    }
    Write-Host ("{0,-12} {1}" -f $Name, $version)
}

Write-Host "Checking end-to-end-app development tools..." -ForegroundColor Cyan
Test-Command -Name "git" -VersionArguments @("--version")
Test-Command -Name "java" -VersionArguments @("-version")
Test-Command -Name "javac" -VersionArguments @("-version")
Test-Command -Name "node" -VersionArguments @("--version")
Test-Command -Name "npm" -VersionArguments @("--version")

if ($IncludePostgres) {
    $psql = Get-Command "psql.exe" -ErrorAction SilentlyContinue
    if ($null -eq $psql) {
        $psql = Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\psql.exe" `
            -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending |
            Select-Object -First 1
    }

    if ($null -eq $psql) {
        $failures.Add("PostgreSQL psql.exe is not installed or available on PATH.")
    }
    else {
        $psqlVersion = & $psql.FullName --version 2>&1 | Select-Object -First 1
        Write-Host ("{0,-12} {1}" -f "psql.exe", $psqlVersion)
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$mavenWrapper = Join-Path $repoRoot "backend\mvnw.cmd"
if (-not (Test-Path $mavenWrapper)) {
    $failures.Add("Missing Maven wrapper: $mavenWrapper")
}
else {
    Push-Location (Split-Path -Parent $mavenWrapper)
    try {
        $mavenVersion = & $mavenWrapper -v 2>&1 | Select-Object -First 1
        Write-Host ("{0,-12} {1}" -f "mvnw.cmd", $mavenVersion)
    }
    finally {
        Pop-Location
    }
}

if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $failures.Add("JAVA_HOME is not set.")
}
else {
    Write-Host ("{0,-12} {1}" -f "JAVA_HOME", $env:JAVA_HOME)
}

$apiUsername = if ([string]::IsNullOrWhiteSpace($env:FINANCIALS_API_USERNAME)) {
    "financial_app (default)"
}
else {
    $env:FINANCIALS_API_USERNAME
}
$apiPasswordState = if ([string]::IsNullOrWhiteSpace($env:FINANCIALS_API_PASSWORD)) {
    "using local default"
}
else {
    "set (value hidden)"
}
$apiAllowedOrigins = if ([string]::IsNullOrWhiteSpace($env:FINANCIALS_ALLOWED_ORIGINS)) {
    "none (same-origin/proxy only)"
}
else {
    $env:FINANCIALS_ALLOWED_ORIGINS
}
$apiMaxRequestBytes = if ([string]::IsNullOrWhiteSpace($env:FINANCIALS_MAX_REQUEST_BYTES)) {
    "1048576 (default)"
}
else {
    $env:FINANCIALS_MAX_REQUEST_BYTES
}

Write-Host ("{0,-12} {1}" -f "API user", $apiUsername)
Write-Host ("{0,-12} {1}" -f "API password", $apiPasswordState)
Write-Host ("{0,-12} {1}" -f "API origins", $apiAllowedOrigins)
Write-Host ("{0,-12} {1}" -f "API max body", $apiMaxRequestBytes)

if ($IncludePostgres) {
    $databaseUrl = if ([string]::IsNullOrWhiteSpace($env:DATABASE_URL)) {
        "jdbc:postgresql://localhost:5432/financial_app (default)"
    }
    else {
        $env:DATABASE_URL
    }
    $databaseUser = if ([string]::IsNullOrWhiteSpace($env:DATABASE_USERNAME)) {
        "financial_app_user (default)"
    }
    else {
        $env:DATABASE_USERNAME
    }
    $passwordState = if ([string]::IsNullOrWhiteSpace($env:DATABASE_PASSWORD)) {
        "using local default"
    }
    else {
        "set (value hidden)"
    }

    Write-Host ("{0,-12} {1}" -f "DATABASE_URL", $databaseUrl)
    Write-Host ("{0,-12} {1}" -f "DB username", $databaseUser)
    Write-Host ("{0,-12} {1}" -f "DB password", $passwordState)
}

if ($failures.Count -gt 0) {
    Write-Host ""
    $failures | ForEach-Object { Write-Host "ERROR: $_" -ForegroundColor Red }
    exit 1
}

Write-Host ""
Write-Host "Environment check passed." -ForegroundColor Green
exit 0
