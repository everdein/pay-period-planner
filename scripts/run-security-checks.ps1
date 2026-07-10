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

$snyk = Get-Command "snyk" -ErrorAction SilentlyContinue
if ($null -eq $snyk) {
    throw "Snyk CLI is not installed. Install it or use the authenticated CI scan."
}
if ([string]::IsNullOrWhiteSpace($env:SNYK_TOKEN)) {
    throw "SNYK_TOKEN is not available in this process."
}

Push-Location $repoRoot
try {
    Invoke-Step "Root dependency audit" {
        npm audit --audit-level=high
    }
    Invoke-Step "Frontend dependency audit" {
        npm --prefix frontend audit --audit-level=high
    }
    Invoke-Step "Authenticated Snyk scan" {
        & $snyk.Source test --all-projects --severity-threshold=high
    }
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "Security checks passed." -ForegroundColor Green
exit 0
