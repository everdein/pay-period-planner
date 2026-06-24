Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendPath = Join-Path $repoRoot "backend"

$env:SPRING_PROFILES_ACTIVE = "postgres"
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/financial_app"
$env:DATABASE_USERNAME = "financial_app_user"
$env:DATABASE_PASSWORD = "financial_app_password"

Push-Location $backendPath
try {
  .\mvnw.cmd -Pdev spring-boot:run
} finally {
  Pop-Location
}
