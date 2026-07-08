Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$failures = [System.Collections.Generic.List[string]]::new()

function Test-Command {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string[]]$VersionArguments
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        $failures.Add("$Name is not available on PATH.")
        return
    }

    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $version = & $command.Source @VersionArguments 2>&1 | Select-Object -First 1
    }
    finally {
        $ErrorActionPreference = $previousErrorAction
    }
    Write-Host ("{0,-12} {1}" -f $Name, $version)
}

Write-Host "Checking end-to-end-app development tools..." -ForegroundColor Cyan
Test-Command -Name "git" -VersionArguments @("--version")
Test-Command -Name "java" -VersionArguments @("-version")
Test-Command -Name "javac" -VersionArguments @("-version")
Test-Command -Name "node" -VersionArguments @("--version")
Test-Command -Name "npm" -VersionArguments @("--version")

$repoRoot = Split-Path -Parent $PSScriptRoot
$mavenWrapper = Join-Path $repoRoot "backend\mvnw.cmd"
if (-not (Test-Path $mavenWrapper)) {
    $failures.Add("Missing Maven wrapper: $mavenWrapper")
}
else {
    Push-Location (Split-Path -Parent $mavenWrapper)
    try {
        $mavenVersion = & $mavenWrapper -v 2>&1 | Select-Object -First 1
        Write-Host ("{0,-12} {1}" -f "mvnw.cmd", $mavenVersion)
    }
    finally {
        Pop-Location
    }
}

if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $failures.Add("JAVA_HOME is not set.")
}
else {
    Write-Host ("{0,-12} {1}" -f "JAVA_HOME", $env:JAVA_HOME)
}

if ($failures.Count -gt 0) {
    Write-Host ""
    $failures | ForEach-Object { Write-Host "ERROR: $_" -ForegroundColor Red }
    exit 1
}

Write-Host ""
Write-Host "Environment check passed." -ForegroundColor Green
