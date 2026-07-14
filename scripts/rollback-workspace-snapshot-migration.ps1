param(
    [Parameter(Mandatory = $true)]
    [Guid]$MigrationId,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$ApiUsername = $env:FINANCIALS_API_USERNAME,
    [string]$ApiPassword = $env:FINANCIALS_API_PASSWORD,
    [switch]$ConfirmRollback
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

function Assert-SafeBaseUrl {
    param([string]$Url)

    $uri = [System.Uri]$Url
    $isLoopback = $uri.IsLoopback -or $uri.Host -in @("localhost", "127.0.0.1", "::1")
    if ($uri.Scheme -ne "https" -and -not ($uri.Scheme -eq "http" -and $isLoopback)) {
        throw "Migration credentials require HTTPS unless BaseUrl is a loopback address."
    }
}

if (-not $ConfirmRollback) {
    throw "Rollback deactivates the migrated workspace snapshot. Rerun with -ConfirmRollback after confirming the migration ID and retained backup."
}

if ([string]::IsNullOrWhiteSpace($ApiUsername)) {
    $ApiUsername = "financial_app"
}

if ([string]::IsNullOrEmpty($ApiPassword)) {
    $ApiPassword = "financial_app_local_password"
}

Assert-SafeBaseUrl -Url $BaseUrl

$authorization = New-BasicAuthorizationHeader -Username $ApiUsername -Password $ApiPassword
$migrationUrl = $BaseUrl.TrimEnd("/") + "/api/v1/admin/workspace-migrations/$MigrationId"
$before = Invoke-RestMethod `
    -Method Get `
    -Uri $migrationUrl `
    -Headers @{ Authorization = $authorization }

if (-not $before.rollbackEligible) {
    throw "Migration $MigrationId is not rollback-eligible. Its snapshot may have changed or already been deactivated."
}

$rolledBack = Invoke-RestMethod `
    -Method Post `
    -Uri "$migrationUrl/rollback" `
    -Headers @{
        Authorization = $authorization
        "X-Confirm-Financial-Migration" = "ROLLBACK"
    }

$verification = Invoke-RestMethod `
    -Method Get `
    -Uri $migrationUrl `
    -Headers @{ Authorization = $authorization }

if ($rolledBack.status -ne "rolled_back" -or $verification.status -ne "rolled_back") {
    throw "Rollback status verification failed for migration $MigrationId."
}
if ($verification.snapshotActive -or -not $verification.metadataMatches) {
    throw "Rollback metadata verification failed for migration $MigrationId."
}

Write-Host "Workspace snapshot migration rolled back and verified." -ForegroundColor Green
Write-Host ("Migration ID: {0}" -f $verification.id)
Write-Host ("Workspace ID: {0}; migrated snapshot ID: {1}" -f $verification.workspaceId, $verification.snapshotId)
Write-Host "The migrated snapshot is inactive; its records and migration history were retained."
Write-Host "The source store and external backup were not modified."
