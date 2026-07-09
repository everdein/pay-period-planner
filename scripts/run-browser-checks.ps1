param(
    [switch]$InstallBrowsers,
    [switch]$Headed,
    [string]$Project = "chromium"
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
    if ($InstallBrowsers) {
        Invoke-Step "Install Playwright Chromium browser" {
            npm --prefix frontend exec playwright install chromium
        }
    }

    $playwrightArgs = @("--project", $Project)
    if ($Headed) {
        $playwrightArgs += "--headed"
    }

    Invoke-Step "Run Playwright browser workflow smoke tests" {
        npm --prefix frontend run test:e2e -- @playwrightArgs
    }
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "Browser workflow checks passed." -ForegroundColor Green
exit 0
