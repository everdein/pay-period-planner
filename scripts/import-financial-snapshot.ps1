param(
    [ValidateSet("csv", "xlsx")]
    [string]$Format,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AccountEmail = $env:FINANCIALS_ACCOUNT_EMAIL,
    [string]$AccountPassword = $env:FINANCIALS_ACCOUNT_PASSWORD,
    [long]$WorkspaceId = 0,
    [Parameter(Mandatory = $true)]
    [string]$InputPath,
    [switch]$ConfirmRestore
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "financial-api-session.ps1")

if (-not $ConfirmRestore) {
    throw "Import replaces the entire saved financial snapshot and increments its version. Rerun with -ConfirmRestore after verifying you have the right file and a backup."
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

$apiSession = $null
try {
    $apiSession = Connect-FinancialApiSession `
        -BaseUrl $BaseUrl `
        -AccountEmail $AccountEmail `
        -AccountPassword $AccountPassword `
        -WorkspaceId $WorkspaceId
    $csrfProof = Get-FinancialApiCsrfProof -Session $apiSession
    $headers = New-FinancialApiHeaders -Session $apiSession
    $headers[$csrfProof.HeaderName] = $csrfProof.Token

    $response = Invoke-RestMethod `
        -Method Post `
        -Uri "$financialsBaseUrl/$importPath" `
        -WebSession $apiSession.WebSession `
        -Headers $headers `
        -ContentType $contentType `
        -InFile $resolvedInputPath
}
finally {
    if ($null -ne $apiSession) {
        try {
            Disconnect-FinancialApiSession -Session $apiSession
        }
        catch {
            Write-Warning "The temporary import API session could not be revoked."
        }
    }
}

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
