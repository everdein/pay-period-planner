param(
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
    throw "Restore replaces the entire saved financial snapshot and increments its version. Rerun with -ConfirmRestore after verifying the JSON backup and preserving the current state separately."
}

$resolvedInputPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($InputPath)
if (-not (Test-Path $resolvedInputPath)) {
    throw "Input file does not exist: $resolvedInputPath"
}

if ([System.IO.Path]::GetExtension($resolvedInputPath).ToLowerInvariant() -ne ".json") {
    throw "Only JSON financial snapshot backups can be restored."
}

try {
    $backup = Get-Content -LiteralPath $resolvedInputPath -Raw | ConvertFrom-Json
}
catch {
    throw "Input file is not valid JSON."
}

$requiredProperties = @("format", "exportedAt", "snapshot")
foreach ($property in $requiredProperties) {
    if ($backup.PSObject.Properties.Name -notcontains $property) {
        throw "Input file is not a complete financial snapshot backup."
    }
}

if ($backup.format -ne "end-to-end-app.financial-snapshot.v1") {
    throw "Input file uses an unsupported financial snapshot backup format."
}

$financialsBaseUrl = $BaseUrl.TrimEnd("/") + "/api/v1/financials"

$apiSession = $null
try {
    $apiSession = Connect-FinancialApiSession `
        -BaseUrl $BaseUrl `
        -AccountEmail $AccountEmail `
        -AccountPassword $AccountPassword `
        -WorkspaceId $WorkspaceId
    $headers = New-FinancialApiHeaders -Session $apiSession

    $currentSnapshot = Invoke-RestMethod `
        -Method Get `
        -Uri $financialsBaseUrl `
        -WebSession $apiSession.WebSession `
        -Headers $headers
    $expectedVersion = [long]$currentSnapshot.version

    $csrfProof = Get-FinancialApiCsrfProof -Session $apiSession
    $headers[$csrfProof.HeaderName] = $csrfProof.Token

    $response = Invoke-RestMethod `
        -Method Post `
        -Uri "$financialsBaseUrl/restore?expectedVersion=$expectedVersion" `
        -WebSession $apiSession.WebSession `
        -Headers $headers `
        -ContentType "application/json" `
        -InFile $resolvedInputPath
}
finally {
    if ($null -ne $apiSession) {
        try {
            Disconnect-FinancialApiSession -Session $apiSession
        }
        catch {
            Write-Warning "The temporary restore API session could not be revoked."
        }
    }
}

Write-Host "Restored JSON financial snapshot backup." -ForegroundColor Green
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
