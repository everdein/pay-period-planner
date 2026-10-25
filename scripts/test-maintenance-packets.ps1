Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ("planner-maintenance-tests-" + [guid]::NewGuid().ToString("N"))
$repository = Join-Path $fixtureRoot "repository"
$runner = Join-Path $PSScriptRoot "generate-maintenance-packets.ps1"
$previousCommitterDate = $env:GIT_COMMITTER_DATE

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}
function Invoke-FixtureGit {
    param([string[]]$Arguments)
    $output = & git -C $repository @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Synthetic fixture Git operation failed: $output" }
    return $output
}
function Invoke-Case {
    param([string]$Name, [string]$Since, [bool]$ExpectFailure = $false, [string]$HeadRef = "HEAD")
    $outputDirectory = Join-Path $fixtureRoot $Name
    $summary = Join-Path $fixtureRoot "$Name-summary.md"
    Set-Content -LiteralPath $summary -Value "Existing workflow section"
    $failed = $false
    try {
        & $runner -RepositoryRoot $repository -OutputDirectory $outputDirectory -Since $Since -SummaryPath $summary -HeadRef $HeadRef
    }
    catch { $failed = $true }
    Assert-True ($failed -eq $ExpectFailure) "Unexpected completion state in $Name."
    $status = Get-Content -Raw -LiteralPath (Join-Path $outputDirectory "maintenance-status.json") | ConvertFrom-Json
    return @{ Status = $status; Output = $outputDirectory; Summary = (Get-Content -Raw -LiteralPath $summary) }
}

try {
    New-Item -ItemType Directory -Path "$repository/scripts", "$repository/backend/src/main/java/example/api", "$repository/.agents/skills/audit-pay-period-planner-docs/references" -Force | Out-Null
    Invoke-FixtureGit @("init", "--initial-branch=main") | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "check-documentation-drift.ps1") -Destination "$repository/scripts/check-documentation-drift.ps1"
    Set-Content -LiteralPath "$repository/README.md" -Value "Synthetic fixture"
    Set-Content -LiteralPath "$repository/.agents/skills/audit-pay-period-planner-docs/references/source-map.md" -Value 'Canonical owner: `README.md`.'
    @'
param([string]$SummaryPath)
Set-Content -LiteralPath $SummaryPath -Value '# Engineering status fixture'
'@ | Set-Content -LiteralPath "$repository/scripts/generate-engineering-status.ps1"
    @'
param([string]$SummaryPath, [string]$BaseRef, [string]$HeadRef)
$changed = git -C (Split-Path -Parent $PSScriptRoot) diff --name-only $BaseRef $HeadRef
if ($LASTEXITCODE -ne 0) { throw 'Comparison failed.' }
Set-Content -LiteralPath $SummaryPath -Value "# Dependency fixture`n$changed"
'@ | Set-Content -LiteralPath "$repository/scripts/triage-dependency-updates.ps1"
    $env:GIT_COMMITTER_DATE = "2000-01-01T12:00:00Z"
    Invoke-FixtureGit @("add", ".") | Out-Null
    Invoke-FixtureGit @("-c", "user.name=Fixture", "-c", "user.email=fixture@example.test", "commit", "--no-verify", "--date=2000-01-01T12:00:00Z", "-m", "Synthetic baseline") | Out-Null
    Set-Content -LiteralPath "$repository/backend/src/main/java/example/api/Snapshot.java" -Value "// Synthetic API change"
    $env:GIT_COMMITTER_DATE = "2001-01-01T12:00:00Z"
    Invoke-FixtureGit @("add", ".") | Out-Null
    Invoke-FixtureGit @("-c", "user.name=Fixture", "-c", "user.email=fixture@example.test", "commit", "--no-verify", "--date=2001-01-01T12:00:00Z", "-m", "Synthetic API change") | Out-Null

    $weekly = Invoke-Case "weekly" "2000-06-01T00:00:00Z"
    Assert-True ($weekly.Status.comparison.mode -eq "time-window") "Weekly comparison mode is wrong."
    Assert-True ($weekly.Summary -match "Existing workflow section" -and $weekly.Summary -match "Engineering status fixture" -and $weekly.Summary -match "Dependency fixture" -and $weekly.Summary -match "Documentation drift packet") "Packets replaced another summary section."
    Assert-True ($weekly.Summary -match "Snapshot.java" -and $weekly.Summary -match "API contract") "Weekly committed API change was not detected."

    $initial = Invoke-Case "initial" "1999-01-01T00:00:00Z"
    Assert-True ($initial.Status.comparison.mode -eq "initial-full-tree" -and $initial.Summary -match "Snapshot.java") "Initial comparison omitted the full tree."
    $quiet = Invoke-Case "quiet" "2002-01-01T00:00:00Z"
    Assert-True ($quiet.Status.comparison.mode -eq "no-changes" -and $quiet.Summary -match "Changed files: 0") "No-change comparison is incorrect."

    @'
param([string]$SummaryPath)
Set-Content -LiteralPath $SummaryPath -Value 'private-fixture-sentinel'
throw 'private-fixture-sentinel'
'@ | Set-Content -LiteralPath "$repository/scripts/generate-engineering-status.ps1"
    $failure = Invoke-Case "failure" "2000-06-01T00:00:00Z" $true
    Assert-True ($failure.Status.collectors[0].status -eq "failed" -and $failure.Status.collectors[1].status -eq "passed" -and $failure.Status.collectors[2].status -eq "passed") "Collector failure blocked independent packets."
    $retained = (Get-ChildItem -LiteralPath $failure.Output -File | Get-Content -Raw) -join "`n"
    Assert-True ($retained -notmatch "private-fixture-sentinel" -and $failure.Summary -notmatch "private-fixture-sentinel") "Failed collector output was retained."

    Set-Content -LiteralPath "$repository/scripts/generate-engineering-status.ps1" -Value 'param([string]$SummaryPath); Set-Content -LiteralPath $SummaryPath -Value "# Engineering status fixture"'
    $invalid = Invoke-Case "invalid-ref" "2000-06-01T00:00:00Z" $true "missing-reference"
    Assert-True ($invalid.Status.comparison.mode -eq "unavailable" -and $invalid.Status.collectors[0].status -eq "passed" -and $invalid.Status.collectors[1].status -eq "failed" -and $invalid.Status.collectors[2].status -eq "failed") "Invalid comparison was reported as successful."
    Write-Host "Passed: committed weekly drift, summary preservation, initial tree, no-change week, independent failure handling, sanitized artifacts, and unavailable comparison."
}
finally {
    $env:GIT_COMMITTER_DATE = $previousCommitterDate
    # Only remove this exact generated fixture, after checking its resolved parent.
    $resolved = [IO.Path]::GetFullPath($fixtureRoot)
    $temporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar)
    if ([IO.Path]::GetDirectoryName($resolved) -eq $temporaryRoot -and [IO.Path]::GetFileName($resolved).StartsWith("planner-maintenance-tests-")) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
