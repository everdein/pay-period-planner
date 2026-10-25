param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot),
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [datetimeoffset]$Since = [datetimeoffset]::UtcNow.AddDays(-7),
    [string]$HeadRef = "HEAD",
    [string]$SummaryPath = $env:GITHUB_STEP_SUMMARY
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-Git {
    param([string[]]$Arguments)
    $result = & git -C $RepositoryRoot @Arguments 2>$null
    if ($LASTEXITCODE -ne 0) { throw "Cannot resolve maintenance comparison." }
    return $result
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$comparison = [ordered]@{
    since = $Since.ToUniversalTime().ToString("o")
    head = $null
    base = $null
    mode = "unavailable"
}
try {
    $comparison.head = Invoke-Git @("rev-parse", "--verify", "$HeadRef^{commit}")
    $comparison.base = Invoke-Git @("rev-list", "-1", "--before=$($comparison.since)", $comparison.head)
    if ([string]::IsNullOrWhiteSpace($comparison.base)) {
        # New repositories have no commit before the window: compare the full tree.
        $comparison.base = $null | & git -C $RepositoryRoot hash-object -w -t tree --stdin
        if ($LASTEXITCODE -ne 0) { throw "Cannot create empty comparison tree." }
        $comparison.mode = "initial-full-tree"
    }
    elseif ($comparison.base -eq $comparison.head) {
        $comparison.mode = "no-changes"
    }
    else { $comparison.mode = "time-window" }
}
catch { $comparison.mode = "unavailable" }
$comparison | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutputDirectory "comparison.json") -Encoding UTF8

$collectors = @(
    @{ Name = "engineering-status"; Script = "generate-engineering-status.ps1"; Comparison = $false },
    @{ Name = "dependency-triage"; Script = "triage-dependency-updates.ps1"; Comparison = $true },
    @{ Name = "documentation-drift"; Script = "check-documentation-drift.ps1"; Comparison = $true }
)
$results = @()
foreach ($collector in $collectors) {
    $packetPath = Join-Path $OutputDirectory "$($collector.Name).md"
    if (Test-Path -LiteralPath $packetPath) { Remove-Item -LiteralPath $packetPath -Force }
    $status = "passed"
    try {
        if ($collector.Comparison -and $comparison.mode -eq "unavailable") { throw "Comparison unavailable." }
        $arguments = @{ SummaryPath = $packetPath }
        if ($collector.Comparison) {
            $arguments.BaseRef = $comparison.base
            $arguments.HeadRef = $comparison.head
        }
        $global:LASTEXITCODE = 0
        # Packet files are the allowlisted output. Never retain raw error streams.
        & (Join-Path $RepositoryRoot "scripts/$($collector.Script)") @arguments *> $null
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $packetPath)) { throw "Collector did not complete." }
    }
    catch {
        $status = "failed"
        # Replace partial output; exceptions can include credentials or private data.
        $reason = if ($collector.Comparison -and $comparison.mode -eq "unavailable") {
            "Commit comparison unavailable; this packet was not evaluated."
        } else { "Collector failed; no successful packet is claimed." }
        Set-Content -LiteralPath $packetPath -Value "# $($collector.Name)`n`n$reason" -Encoding UTF8
    }
    $results += [pscustomobject]@{ collector = $collector.Name; status = $status; file = "$($collector.Name).md" }
    if (-not [string]::IsNullOrWhiteSpace($SummaryPath)) {
        Add-Content -LiteralPath $SummaryPath -Value (Get-Content -LiteralPath $packetPath -Raw) -Encoding UTF8
    }
}
$statusDocument = [ordered]@{ generated = [datetimeoffset]::UtcNow.ToString("o"); comparison = $comparison; collectors = $results }
$statusDocument | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $OutputDirectory "maintenance-status.json") -Encoding UTF8
if (@($results | Where-Object status -eq "failed").Count -gt 0) {
    throw "Maintenance packet collection incomplete; see maintenance-status.json."
}
