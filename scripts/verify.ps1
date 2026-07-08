param(
    [switch]$IncludePostgres,
    [switch]$IncludeSecurity
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

Push-Location $repoRoot
try {
    Invoke-Step "Spell check" { npm run spell }
    Invoke-Step "Frontend type check" { npm --prefix frontend run type-check }
    Invoke-Step "Frontend lint" { npm --prefix frontend run lint }
    Invoke-Step "Frontend tests" { npm --prefix frontend run test }
    Invoke-Step "Frontend build" { npm --prefix frontend run build }

    Push-Location (Join-Path $repoRoot "backend")
    try {
        Invoke-Step "Backend formatting" { .\mvnw.cmd -B spotless:check sortpom:verify }
        Invoke-Step "Backend clean build and tests" { .\mvnw.cmd -B clean verify }

        if ($IncludePostgres) {
            $previousProfile = $env:SPRING_PROFILES_ACTIVE
            $env:SPRING_PROFILES_ACTIVE = "postgres"
            try {
                Invoke-Step "PostgreSQL profile tests" { .\mvnw.cmd -B test }
            }
            finally {
                $env:SPRING_PROFILES_ACTIVE = $previousProfile
            }
        }
    }
    finally {
        Pop-Location
    }

    if ($IncludeSecurity) {
        Invoke-Step "Root dependency audit" { npm audit --audit-level=high }
        Invoke-Step "Frontend dependency audit" { npm --prefix frontend audit --audit-level=high }

        $snyk = Get-Command "snyk" -ErrorAction SilentlyContinue
        if ($null -eq $snyk) {
            throw "Snyk CLI is not installed. Install it or rely on CI."
        }
        if ([string]::IsNullOrWhiteSpace($env:SNYK_TOKEN)) {
            throw "SNYK_TOKEN is not available in this process."
        }

        Invoke-Step "Snyk scan" {
            & $snyk.Source test --all-projects --severity-threshold=high
        }
    }
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "Verification passed." -ForegroundColor Green
