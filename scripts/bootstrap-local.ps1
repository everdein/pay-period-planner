param(
    [switch]$IncludePostgres
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot

& (Join-Path $PSScriptRoot "check-environment.ps1") -IncludePostgres:$IncludePostgres
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Push-Location $repoRoot
try {
    Write-Host ""
    Write-Host "Installing root dependencies..." -ForegroundColor Cyan
    npm ci
    if ($LASTEXITCODE -ne 0) {
        throw "Root npm install failed with exit code $LASTEXITCODE."
    }

    Write-Host ""
    Write-Host "Installing frontend dependencies..." -ForegroundColor Cyan
    npm --prefix frontend ci
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend npm install failed with exit code $LASTEXITCODE."
    }

    if ($IncludePostgres) {
        Write-Host ""
        & (Join-Path $PSScriptRoot "setup-local-postgres.ps1")
        if ($LASTEXITCODE -ne 0) {
            throw "PostgreSQL setup failed with exit code $LASTEXITCODE."
        }
    }
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "Local bootstrap complete." -ForegroundColor Green
