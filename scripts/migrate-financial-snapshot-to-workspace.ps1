param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("json-file", "jsonb-document")]
    [string]$Source,
    [string]$InputPath,
    [Parameter(Mandatory = $true)]
    [string]$BackupPath,
    [Parameter(Mandatory = $true)]
    [string]$DestinationEmail,
    [Parameter(Mandatory = $true)]
    [long]$WorkspaceId,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$ApiUsername = $env:FINANCIALS_API_USERNAME,
    [string]$ApiPassword = $env:FINANCIALS_API_PASSWORD,
    [switch]$ConfirmMigration,
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

function Assert-SafeBaseUrl {
    param([string]$Url)

    $uri = [System.Uri]$Url
    $isLoopback = $uri.IsLoopback -or $uri.Host -in @("localhost", "127.0.0.1", "::1")
    if ($uri.Scheme -ne "https" -and -not ($uri.Scheme -eq "http" -and $isLoopback)) {
        throw "Migration credentials require HTTPS unless BaseUrl is a loopback address."
    }
}

function Test-RepositoryPath {
    param(
        [string]$Candidate,
        [string]$RepositoryRoot
    )

    $rootWithSeparator = $RepositoryRoot.TrimEnd("\", "/") + [System.IO.Path]::DirectorySeparatorChar
    return $Candidate.Equals($RepositoryRoot, [System.StringComparison]::OrdinalIgnoreCase) -or
        $Candidate.StartsWith($rootWithSeparator, [System.StringComparison]::OrdinalIgnoreCase)
}

if (-not $ConfirmMigration) {
    throw "Migration writes a workspace-owned relational snapshot. Rerun with -ConfirmMigration after confirming the destination and backup path."
}

if ([string]::IsNullOrWhiteSpace($DestinationEmail)) {
    throw "DestinationEmail is required."
}

if ($WorkspaceId -lt 1) {
    throw "WorkspaceId must be a positive number."
}

if ([string]::IsNullOrWhiteSpace($ApiUsername)) {
    $ApiUsername = "financial_app"
}

if ([string]::IsNullOrEmpty($ApiPassword)) {
    $ApiPassword = "financial_app_local_password"
}

Assert-SafeBaseUrl -Url $BaseUrl

$repoRoot = Split-Path -Parent $PSScriptRoot
$resolvedBackupPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($BackupPath)
$backupParent = Split-Path -Parent $resolvedBackupPath

if (-not $AllowRepositoryPath -and (Test-RepositoryPath -Candidate $resolvedBackupPath -RepositoryRoot $repoRoot)) {
    throw "Refusing to store a financial backup inside the repository. Choose a protected path outside $repoRoot, or use -AllowRepositoryPath only for synthetic data."
}

if ([string]::IsNullOrWhiteSpace($backupParent) -or -not (Test-Path -LiteralPath $backupParent -PathType Container)) {
    throw "Backup directory does not exist: $backupParent"
}

if ((Test-Path -LiteralPath $resolvedBackupPath) -and -not $Force) {
    throw "Backup file already exists: $resolvedBackupPath. Rerun with -Force only when replacing it is intentional."
}

$resolvedInputPath = $null
if ($Source -eq "json-file") {
    if ([string]::IsNullOrWhiteSpace($InputPath)) {
        throw "InputPath is required when Source is json-file."
    }
    $resolvedInputPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($InputPath)
    if (-not (Test-Path -LiteralPath $resolvedInputPath -PathType Leaf)) {
        throw "JSON source file does not exist: $resolvedInputPath"
    }
    if ($resolvedInputPath.Equals($resolvedBackupPath, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "InputPath and BackupPath must be different files."
    }
}

$authorization = New-BasicAuthorizationHeader -Username $ApiUsername -Password $ApiPassword
$migrationBaseUrl = $BaseUrl.TrimEnd("/") + "/api/v1/admin/workspace-migrations"
$temporaryBackupPath = Join-Path $backupParent ((Split-Path -Leaf $resolvedBackupPath) + "." + [System.Guid]::NewGuid().ToString("N") + ".tmp")

try {
    if ($Source -eq "json-file") {
        Copy-Item -LiteralPath $resolvedInputPath -Destination $temporaryBackupPath
    }
    else {
        Invoke-WebRequest `
            -Method Get `
            -Uri "$migrationBaseUrl/legacy-jsonb-backup" `
            -Headers @{ Authorization = $authorization } `
            -OutFile $temporaryBackupPath | Out-Null
    }

    Move-Item -LiteralPath $temporaryBackupPath -Destination $resolvedBackupPath -Force:$Force
}
finally {
    if (Test-Path -LiteralPath $temporaryBackupPath) {
        Remove-Item -LiteralPath $temporaryBackupPath -Force
    }
}

$fingerprint = (Get-FileHash -LiteralPath $resolvedBackupPath -Algorithm SHA256).Hash.ToLowerInvariant()
$encodedEmail = [System.Uri]::EscapeDataString($DestinationEmail.Trim())
$query = "expectedFingerprint={0}&destinationEmail={1}&workspaceId={2}" -f $fingerprint, $encodedEmail, $WorkspaceId
$headers = @{
    Authorization = $authorization
    "X-Confirm-Financial-Migration" = "APPLY"
}

if ($Source -eq "json-file") {
    $migration = Invoke-RestMethod `
        -Method Post `
        -Uri "$migrationBaseUrl/apply/json-file`?$query" `
        -Headers $headers `
        -ContentType "application/json" `
        -InFile $resolvedBackupPath
}
else {
    $migration = Invoke-RestMethod `
        -Method Post `
        -Uri "$migrationBaseUrl/apply/jsonb-document`?$query" `
        -Headers $headers
}

$verification = Invoke-RestMethod `
    -Method Get `
    -Uri "$migrationBaseUrl/$($migration.id)" `
    -Headers @{ Authorization = $authorization }

if ($verification.status -ne "applied") {
    throw "Migration verification did not report applied status. Migration ID: $($migration.id)"
}
if (-not $verification.metadataMatches -or -not $verification.snapshotActive) {
    throw "Migration metadata verification failed. Migration ID: $($migration.id)"
}
if ($verification.sourceFingerprint -ne $fingerprint) {
    throw "Migration fingerprint verification failed. Migration ID: $($migration.id)"
}

Write-Host "Workspace snapshot migration completed and verified." -ForegroundColor Green
Write-Host ("Migration ID: {0}" -f $verification.id)
Write-Host ("Source: {0}; version: {1}; SHA-256: {2}" -f $verification.sourceKind, $verification.sourceVersion, $verification.sourceFingerprint)
Write-Host ("Destination: {0}; workspace ID: {1}" -f $verification.destinationEmail, $verification.workspaceId)
Write-Host (
    "Record counts: bills={0}, annualWithdrawals={1}, assetAccounts={2}, debtAccounts={3}, incomeSummaryItems={4}, incomeEvents={5}, importantDates={6}, auditEvents={7}" -f
        $verification.currentCounts.monthlyBills,
        $verification.currentCounts.annualWithdrawals,
        $verification.currentCounts.assetAccounts,
        $verification.currentCounts.debtAccounts,
        $verification.currentCounts.incomeSummaryItems,
        $verification.currentCounts.incomeEvents,
        $verification.currentCounts.importantDates,
        $verification.currentCounts.auditEvents
)
Write-Host ("Backup retained at: {0}" -f $resolvedBackupPath)
