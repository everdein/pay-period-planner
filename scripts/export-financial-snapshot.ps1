param(
    [ValidateSet("json", "csv", "xlsx")]
    [string]$Format = "json",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$ApiUsername = $env:FINANCIALS_API_USERNAME,
    [string]$ApiPassword = $env:FINANCIALS_API_PASSWORD,
    [Parameter(Mandatory = $true)]
    [string]$OutputPath,
    [switch]$Force,
    [switch]$AllowRepositoryPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function New-BasicAuthorizationHeader {
    param(
        [string]$Username,
        [string]$Password
    )

    $credentials = "{0}:{1}" -f $Username, $Password
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($credentials)
    return "Basic " + [System.Convert]::ToBase64String($bytes)
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$resolvedOutputPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputPath)

if ([string]::IsNullOrWhiteSpace($ApiUsername)) {
    $ApiUsername = "financial_app"
}

if ([string]::IsNullOrEmpty($ApiPassword)) {
    $ApiPassword = "financial_app_local_password"
}

if (
    -not $AllowRepositoryPath -and
    $resolvedOutputPath.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)
) {
    throw "Refusing to write a financial export inside the repository. Choose a path outside $repoRoot, or rerun with -AllowRepositoryPath only for synthetic/mock data."
}

$outputParent = Split-Path -Parent $resolvedOutputPath
if (-not [string]::IsNullOrWhiteSpace($outputParent) -and -not (Test-Path $outputParent)) {
    throw "Output directory does not exist: $outputParent"
}

if ((Test-Path $resolvedOutputPath) -and -not $Force) {
    throw "Output file already exists: $resolvedOutputPath. Rerun with -Force to overwrite it."
}

$financialsBaseUrl = $BaseUrl.TrimEnd("/") + "/api/v1/financials"
$exportPath = switch ($Format) {
    "json" { "export" }
    "csv" { "export/csv" }
    "xlsx" { "export/xlsx" }
}
$accept = switch ($Format) {
    "json" { "application/json" }
    "csv" { "text/csv" }
    "xlsx" { "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" }
}

Invoke-WebRequest `
    -Method Get `
    -Uri "$financialsBaseUrl/$exportPath" `
    -Headers @{
        Accept = $accept
        Authorization = New-BasicAuthorizationHeader -Username $ApiUsername -Password $ApiPassword
    } `
    -OutFile $resolvedOutputPath | Out-Null

Write-Host "Exported $Format financial snapshot to $resolvedOutputPath." -ForegroundColor Green
