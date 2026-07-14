param(
    [switch]$IncludePostgres,
    [switch]$IncludeSecurity
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Write-Warning "scripts\verify.ps1 is retained for compatibility; prefer scripts\verify-local.ps1."

if ($IncludePostgres) {
    Write-Warning "-IncludePostgres is retained for compatibility but is no longer required; PostgreSQL integration tests are part of the default verification gate."
}

& (Join-Path $PSScriptRoot "verify-local.ps1")
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

if ($IncludeSecurity) {
    & (Join-Path $PSScriptRoot "run-security-checks.ps1")
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

exit 0
