param(
    [string]$FrontendCoverageSummaryPath = "frontend/coverage/coverage-summary.json",
    [string]$BackendJacocoCsvPath = "backend/target/site/jacoco/jacoco.csv",
    [string]$SummaryPath = $env:GITHUB_STEP_SUMMARY
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Format-Percent {
    param(
        [Parameter(Mandatory = $true)]
        [double]$Value
    )

    return "$($Value.ToString("0.00"))%"
}

function Get-CoveragePercent {
    param(
        [Parameter(Mandatory = $true)]
        [double]$Covered,
        [Parameter(Mandatory = $true)]
        [double]$Missed
    )

    $total = $Covered + $Missed
    if ($total -eq 0) {
        return 100.0
    }

    return ($Covered / $total) * 100
}

function Format-Gate {
    param(
        [Parameter(Mandatory = $true)]
        [double]$Actual,
        [Parameter(Mandatory = $true)]
        [double]$Minimum
    )

    if ($Actual -ge $Minimum) {
        return "Pass >= $(Format-Percent -Value $Minimum)"
    }

    return "Fail < $(Format-Percent -Value $Minimum)"
}

function ConvertTo-FrontendCoverage {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing frontend coverage summary: $Path"
    }

    $coverage = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    $total = $coverage.total

    [PSCustomObject]@{
        Area = "Frontend"
        Statements = [double]$total.statements.pct
        Branches = [double]$total.branches.pct
        Functions = [double]$total.functions.pct
        Lines = [double]$total.lines.pct
        Gate = @(
            Format-Gate -Actual ([double]$total.statements.pct) -Minimum 45
            Format-Gate -Actual ([double]$total.branches.pct) -Minimum 45
            Format-Gate -Actual ([double]$total.functions.pct) -Minimum 35
            Format-Gate -Actual ([double]$total.lines.pct) -Minimum 46
        ) -join "; "
    }
}

function ConvertTo-BackendCoverage {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing backend JaCoCo CSV report: $Path"
    }

    $rows = Import-Csv -LiteralPath $Path
    if ($rows.Count -eq 0) {
        throw "Backend JaCoCo CSV report is empty: $Path"
    }

    $instructionCovered = 0.0
    $instructionMissed = 0.0
    $branchCovered = 0.0
    $branchMissed = 0.0
    $lineCovered = 0.0
    $lineMissed = 0.0
    $methodCovered = 0.0
    $methodMissed = 0.0

    foreach ($row in $rows) {
        $instructionCovered += [double]$row.INSTRUCTION_COVERED
        $instructionMissed += [double]$row.INSTRUCTION_MISSED
        $branchCovered += [double]$row.BRANCH_COVERED
        $branchMissed += [double]$row.BRANCH_MISSED
        $lineCovered += [double]$row.LINE_COVERED
        $lineMissed += [double]$row.LINE_MISSED
        $methodCovered += [double]$row.METHOD_COVERED
        $methodMissed += [double]$row.METHOD_MISSED
    }

    $linePercent = Get-CoveragePercent -Covered $lineCovered -Missed $lineMissed

    [PSCustomObject]@{
        Area = "Backend"
        Statements = Get-CoveragePercent -Covered $instructionCovered -Missed $instructionMissed
        Branches = Get-CoveragePercent -Covered $branchCovered -Missed $branchMissed
        Functions = Get-CoveragePercent -Covered $methodCovered -Missed $methodMissed
        Lines = $linePercent
        Gate = Format-Gate -Actual $linePercent -Minimum 80
    }
}

$frontend = ConvertTo-FrontendCoverage -Path $FrontendCoverageSummaryPath
$backend = ConvertTo-BackendCoverage -Path $BackendJacocoCsvPath

$summaryLines = New-Object System.Collections.Generic.List[string]
$summaryLines.Add("# Coverage summary packet")
$summaryLines.Add("")
$summaryLines.Add("- Generated: $((Get-Date).ToUniversalTime().ToString("o"))")
$summaryLines.Add("- Frontend source: ``$FrontendCoverageSummaryPath``")
$summaryLines.Add("- Backend source: ``$BackendJacocoCsvPath``")
$summaryLines.Add("")
$summaryLines.Add("| Area | Statements / Instructions | Branches | Functions / Methods | Lines | Gate |")
$summaryLines.Add("| ---- | -------------------------: | -------: | ------------------: | ----: | ---- |")

foreach ($coverage in @($frontend, $backend)) {
    $summaryLines.Add(
        "| $($coverage.Area) | $(Format-Percent -Value $coverage.Statements) | $(Format-Percent -Value $coverage.Branches) | $(Format-Percent -Value $coverage.Functions) | $(Format-Percent -Value $coverage.Lines) | $($coverage.Gate) |"
    )
}

$summaryLines.Add("")
$summaryLines.Add("## Use with AI")
$summaryLines.Add("")
$summaryLines.Add("Use this packet as coverage context only. It summarizes aggregate coverage")
$summaryLines.Add("reports from CI and does not prove PostgreSQL smoke tests, browser workflow")
$summaryLines.Add("coverage, Snyk status, or production readiness.")

$summaryText = $summaryLines -join [Environment]::NewLine

if (-not [string]::IsNullOrWhiteSpace($SummaryPath)) {
    Set-Content -LiteralPath $SummaryPath -Value $summaryText -Encoding UTF8
}

Write-Host $summaryText
