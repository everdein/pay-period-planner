param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AccountEmail = $env:FINANCIALS_ACCOUNT_EMAIL,
    [string]$AccountPassword = $env:FINANCIALS_ACCOUNT_PASSWORD,
    [long]$WorkspaceId = 0,
    [Parameter(Mandatory = $true)]
    [string]$OutputPath,
    [switch]$Force,
    [switch]$AllowRepositoryPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "financial-api-session.ps1")

$repoRoot = Split-Path -Parent $PSScriptRoot
$resolvedOutputPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputPath)

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

if ([System.IO.Path]::GetExtension($resolvedOutputPath).ToLowerInvariant() -ne ".json") {
    throw "Financial snapshot backups must use a .json file extension."
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
    $headers["Accept"] = "application/json"

    Invoke-WebRequest `
        -Method Get `
        -Uri "$financialsBaseUrl/export" `
        -WebSession $apiSession.WebSession `
        -Headers $headers `
        -OutFile $resolvedOutputPath | Out-Null
}
finally {
    if ($null -ne $apiSession) {
        try {
            Disconnect-FinancialApiSession -Session $apiSession
        }
        catch {
            Write-Warning "The temporary export API session could not be revoked."
        }
    }
}

Write-Host "Exported JSON financial snapshot backup to $resolvedOutputPath." -ForegroundColor Green
