param(
    [ValidateSet("csv", "xlsx")]
    [string]$Format,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$ApiUsername = $env:FINANCIALS_API_USERNAME,
    [string]$ApiPassword = $env:FINANCIALS_API_PASSWORD,
    [Parameter(Mandatory = $true)]
    [string]$InputPath,
    [switch]$ConfirmRestore
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

if (-not $ConfirmRestore) {
    throw "Import replaces the entire saved financial snapshot and increments its version. Rerun with -ConfirmRestore after verifying you have the right file and a backup."
}

if ([string]::IsNullOrWhiteSpace($ApiUsername)) {
    $ApiUsername = "financial_app"
}

if ([string]::IsNullOrEmpty($ApiPassword)) {
    $ApiPassword = "financial_app_local_password"
}

$resolvedInputPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($InputPath)
if (-not (Test-Path $resolvedInputPath)) {
    throw "Input file does not exist: $resolvedInputPath"
}

if ([string]::IsNullOrWhiteSpace($Format)) {
    $extension = [System.IO.Path]::GetExtension($resolvedInputPath).TrimStart(".").ToLowerInvariant()
    if ($extension -notin @("csv", "xlsx")) {
        throw "Could not infer import format from extension. Use -Format csv or -Format xlsx."
    }
    $Format = $extension
}

$financialsBaseUrl = $BaseUrl.TrimEnd("/") + "/api/v1/financials"
$importPath = switch ($Format) {
    "csv" { "import/csv" }
    "xlsx" { "import/xlsx" }
}
$contentType = switch ($Format) {
    "csv" { "text/csv; charset=utf-8" }
    "xlsx" { "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" }
}

$response = Invoke-RestMethod `
    -Method Post `
    -Uri "$financialsBaseUrl/$importPath" `
    -Headers @{ Authorization = New-BasicAuthorizationHeader -Username $ApiUsername -Password $ApiPassword } `
    -ContentType $contentType `
    -InFile $resolvedInputPath

Write-Host "Imported $Format financial snapshot." -ForegroundColor Green
Write-Host ("New version: {0}" -f $response.version)
Write-Host (
    "Record counts: bills={0}, annualWithdrawals={1}, assetCategories={2}, debtAccounts={3}, incomeSummaryItems={4}, incomeEvents={5}, importantDates={6}" -f
        $response.bills.Count,
        $response.annualWithdrawals.Count,
        $response.assetCategories.Count,
        $response.debtAccounts.Count,
        $response.incomeSummaryItems.Count,
        $response.incomeEvents.Count,
        $response.importantDates.Count
)
