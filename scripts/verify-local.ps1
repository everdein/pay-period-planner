param(
    [switch]$IncludePostgres
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot

if ($IncludePostgres) {
    $missingPostgresVariables = @(
        "DATABASE_USERNAME",
        "DATABASE_PASSWORD"
    ) | Where-Object { [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_)) }

    if ($missingPostgresVariables.Count -gt 0) {
        throw (
            "PostgreSQL verification requires these environment variables: " +
            ($missingPostgresVariables -join ", ")
        )
    }
}

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

Push-Location $repoRoot
try {
    Invoke-Step "Environment check" {
        & (Join-Path $PSScriptRoot "check-environment.ps1") `
            -IncludePostgres:$IncludePostgres
    }
    Invoke-Step "Spell check" { npm run spell }
    Invoke-Step "Frontend type check" { npm --prefix frontend run type-check }
    Invoke-Step "Frontend lint" { npm --prefix frontend run lint }
    Invoke-Step "Frontend tests and coverage" {
        npm --prefix frontend run test -- --coverage
    }
    Invoke-Step "Frontend build" { npm --prefix frontend run build }

    Push-Location (Join-Path $repoRoot "backend")
    try {
        Invoke-Step "Backend formatting" {
            .\mvnw.cmd -B spotless:check sortpom:verify
        }
        Invoke-Step "Backend clean build, tests, and coverage" {
            .\mvnw.cmd -B clean verify
        }

        if ($IncludePostgres) {
            $previousIntegrationSetting = $env:RUN_POSTGRES_INTEGRATION_TESTS
            $env:RUN_POSTGRES_INTEGRATION_TESTS = "true"
            try {
                Invoke-Step "Isolated PostgreSQL snapshot store and record adapter smoke tests" {
                    .\mvnw.cmd -B test `
                        "-Dtest=PostgresFinancialsSnapshotStoreIT,PostgresFinancialRecordSnapshotAdapterIT" `
                        "-Djacoco.skip=true"
                }
            }
            finally {
                $env:RUN_POSTGRES_INTEGRATION_TESTS = $previousIntegrationSetting
            }
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "Local verification passed." -ForegroundColor Green
exit 0
