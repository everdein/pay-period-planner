Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Assert-SafeFinancialApiBaseUrl {
    param([Parameter(Mandatory = $true)][string]$BaseUrl)

    $uri = [System.Uri]$BaseUrl
    $isLoopback = $uri.IsLoopback -or $uri.Host -in @("localhost", "127.0.0.1", "::1")
    if ($uri.Scheme -ne "https" -and -not ($uri.Scheme -eq "http" -and $isLoopback)) {
        throw "Account credentials require HTTPS unless BaseUrl is a loopback address."
    }
}

function Get-FinancialApiCsrfProof {
    param([Parameter(Mandatory = $true)]$Session)

    $response = Invoke-RestMethod `
        -Method Get `
        -Uri ($Session.BaseUrl + "/api/v1/auth/csrf") `
        -WebSession $Session.WebSession

    if ([string]::IsNullOrWhiteSpace($response.headerName) -or
        [string]::IsNullOrWhiteSpace($response.token)) {
        throw "The financial API did not return a usable CSRF proof."
    }

    return [pscustomobject]@{
        HeaderName = [string]$response.headerName
        Token = [string]$response.token
    }
}

function Connect-FinancialApiSession {
    param(
        [Parameter(Mandatory = $true)][string]$BaseUrl,
        [Parameter(Mandatory = $true)][string]$AccountEmail,
        [Parameter(Mandatory = $true)][string]$AccountPassword,
        [long]$WorkspaceId = 0
    )

    if ([string]::IsNullOrWhiteSpace($AccountEmail)) {
        throw "AccountEmail is required. Set FINANCIALS_ACCOUNT_EMAIL or pass -AccountEmail."
    }
    if ([string]::IsNullOrEmpty($AccountPassword)) {
        throw "AccountPassword is required. Set FINANCIALS_ACCOUNT_PASSWORD or pass -AccountPassword."
    }
    if ($WorkspaceId -lt 0) {
        throw "WorkspaceId cannot be negative."
    }

    Assert-SafeFinancialApiBaseUrl -BaseUrl $BaseUrl
    $normalizedBaseUrl = $BaseUrl.TrimEnd("/")
    $webSession = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $session = [pscustomobject]@{
        BaseUrl = $normalizedBaseUrl
        WebSession = $webSession
        WorkspaceId = $null
    }
    $csrfProof = Get-FinancialApiCsrfProof -Session $session
    $headers = @{}
    $headers[$csrfProof.HeaderName] = $csrfProof.Token
    $body = @{
        email = $AccountEmail
        password = $AccountPassword
    } | ConvertTo-Json

    $account = Invoke-RestMethod `
        -Method Post `
        -Uri ($normalizedBaseUrl + "/api/v1/auth/signin") `
        -WebSession $webSession `
        -Headers $headers `
        -ContentType "application/json" `
        -Body $body

    $workspaces = @($account.workspaces)
    if ($WorkspaceId -gt 0) {
        $workspace = $workspaces | Where-Object { [long]$_.workspaceId -eq $WorkspaceId }
        if ($null -eq $workspace) {
            try {
                Disconnect-FinancialApiSession -Session $session
            }
            catch {
                Write-Warning "The rejected API session could not be revoked."
            }
            throw "WorkspaceId $WorkspaceId is not available to the signed-in account."
        }
        $session.WorkspaceId = $WorkspaceId
    }
    elseif ($workspaces.Count -gt 1) {
        try {
            Disconnect-FinancialApiSession -Session $session
        }
        catch {
            Write-Warning "The unselected API session could not be revoked."
        }
        throw "The signed-in account has multiple workspaces. Pass -WorkspaceId explicitly."
    }

    return $session
}

function New-FinancialApiHeaders {
    param([Parameter(Mandatory = $true)]$Session)

    $headers = @{}
    if ($null -ne $Session.WorkspaceId) {
        $headers["X-Workspace-ID"] = [string]$Session.WorkspaceId
    }
    return $headers
}

function Disconnect-FinancialApiSession {
    param([Parameter(Mandatory = $true)]$Session)

    $csrfProof = Get-FinancialApiCsrfProof -Session $Session
    $headers = @{}
    $headers[$csrfProof.HeaderName] = $csrfProof.Token
    Invoke-RestMethod `
        -Method Post `
        -Uri ($Session.BaseUrl + "/api/v1/auth/signout") `
        -WebSession $Session.WebSession `
        -Headers $headers | Out-Null
}
