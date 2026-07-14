param(
    [string]$DatabaseUrl,
    [string]$DatabaseUsername,
    [string]$DatabasePassword,
    [string]$DatabaseSchema = "public",
    [switch]$BaselineLegacySnapshotDocumentSchema,
    [switch]$BaselineLegacyV4Schema
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($DatabaseUrl)) {
    $DatabaseUrl = if ([string]::IsNullOrWhiteSpace($env:DATABASE_URL)) {
        "jdbc:postgresql://localhost:5432/financial_app"
    }
    else {
        $env:DATABASE_URL
    }
}

if ([string]::IsNullOrWhiteSpace($DatabaseUsername)) {
    $DatabaseUsername = if ([string]::IsNullOrWhiteSpace($env:DATABASE_USERNAME)) {
        "financial_app_user"
    }
    else {
        $env:DATABASE_USERNAME
    }
}

if ([string]::IsNullOrWhiteSpace($DatabasePassword)) {
    $DatabasePassword = if ([string]::IsNullOrWhiteSpace($env:DATABASE_PASSWORD)) {
        "financial_app_password"
    }
    else {
        $env:DATABASE_PASSWORD
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $repoRoot "backend"
$mavenWrapper = Join-Path $backendRoot "mvnw.cmd"

if (-not (Test-Path $mavenWrapper)) {
    throw "Maven wrapper not found at '$mavenWrapper'."
}

if ($BaselineLegacySnapshotDocumentSchema -and $BaselineLegacyV4Schema) {
    throw "Choose only one legacy schema baseline mode."
}

$previousFlywayUrl = $env:FLYWAY_URL
$previousFlywayUser = $env:FLYWAY_USER
$previousFlywayPassword = $env:FLYWAY_PASSWORD
$previousFlywayDefaultSchema = $env:FLYWAY_DEFAULT_SCHEMA
$previousFlywaySchemas = $env:FLYWAY_SCHEMAS

try {
    $env:FLYWAY_URL = $DatabaseUrl
    $env:FLYWAY_USER = $DatabaseUsername
    $env:FLYWAY_PASSWORD = $DatabasePassword
    $env:FLYWAY_DEFAULT_SCHEMA = $DatabaseSchema
    $env:FLYWAY_SCHEMAS = $DatabaseSchema

    Push-Location $backendRoot
    try {
        if ($BaselineLegacySnapshotDocumentSchema -or $BaselineLegacyV4Schema) {
            $baselineVersion = if ($BaselineLegacySnapshotDocumentSchema) { "0" } else { "4" }
            $baselineDescription = if ($BaselineLegacySnapshotDocumentSchema) {
                "Legacy snapshot document schema"
            }
            else {
                "Legacy V1-V4 schema"
            }

            Write-Host "Baselining the explicitly approved legacy schema at version $baselineVersion..." -ForegroundColor Yellow
            & $mavenWrapper -B --no-transfer-progress `
                "-Dflyway.baselineVersion=$baselineVersion" `
                "-Dflyway.baselineDescription=$baselineDescription" `
                flyway:baseline

            if ($LASTEXITCODE -ne 0) {
                throw "Flyway baseline failed with exit code $LASTEXITCODE."
            }
        }

        Write-Host "Running Flyway migrations and validation..." -ForegroundColor Yellow
        & $mavenWrapper -B --no-transfer-progress flyway:migrate flyway:validate

        if ($LASTEXITCODE -ne 0) {
            throw "Flyway migration failed with exit code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    $env:FLYWAY_URL = $previousFlywayUrl
    $env:FLYWAY_USER = $previousFlywayUser
    $env:FLYWAY_PASSWORD = $previousFlywayPassword
    $env:FLYWAY_DEFAULT_SCHEMA = $previousFlywayDefaultSchema
    $env:FLYWAY_SCHEMAS = $previousFlywaySchemas
}

Write-Host "Flyway migration complete." -ForegroundColor Green
