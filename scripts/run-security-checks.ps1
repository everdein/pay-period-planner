Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$snykVersionPath = Join-Path $repoRoot ".snyk-cli-version"

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
if (-not (Test-Path -LiteralPath $snykVersionPath)) {
    throw "Missing pinned Snyk CLI version file: $snykVersionPath"
}

$expectedSnykVersion = (Get-Content -LiteralPath $snykVersionPath -Raw).Trim()
if ($expectedSnykVersion -notmatch "^\d+\.\d+\.\d+$") {
    throw "Pinned Snyk CLI version '$expectedSnykVersion' is not a semantic version."
}

$snykVersionOutput = @(& $snyk.Source --version)
if ($LASTEXITCODE -ne 0) {
    throw "Unable to read the installed Snyk CLI version."
}
$actualSnykVersion = @($snykVersionOutput |
    ForEach-Object { "$($_)".Trim() } |
    Where-Object { $_ -match "^\d+\.\d+\.\d+$" } |
    Select-Object -Last 1)
if ($actualSnykVersion.Count -ne 1) {
    throw "Snyk CLI did not report a semantic version."
}
if ($actualSnykVersion[0] -ne $expectedSnykVersion) {
    throw "Snyk CLI version $($actualSnykVersion[0]) does not match the repository pin $expectedSnykVersion. Install snyk@$expectedSnykVersion or replace the direct binary."
}
if ([string]::IsNullOrWhiteSpace($env:SNYK_TOKEN)) {
    throw "SNYK_TOKEN is not available in this process."
}

Write-Host "Using pinned Snyk CLI $expectedSnykVersion." -ForegroundColor DarkGray

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
