Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendPath = Join-Path $repoRoot "backend"

if ([string]::IsNullOrWhiteSpace($env:DATABASE_URL)) {
    $env:DATABASE_URL = "jdbc:postgresql://localhost:5432/financial_app"
}
if ([string]::IsNullOrWhiteSpace($env:DATABASE_USERNAME)) {
    $env:DATABASE_USERNAME = "financial_app_user"
}
if ([string]::IsNullOrWhiteSpace($env:DATABASE_PASSWORD)) {
    $env:DATABASE_PASSWORD = "financial_app_password"
}

Push-Location $backendPath
try {
    .\mvnw.cmd -Pdev spring-boot:run
}
finally {
    Pop-Location
}
