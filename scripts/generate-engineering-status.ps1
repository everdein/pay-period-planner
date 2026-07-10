# cspell:words LASTEXITCODE

param(
    [string]$SummaryPath = $env:GITHUB_STEP_SUMMARY
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$summaryLines = New-Object System.Collections.Generic.List[string]

function Invoke-GitLines {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed with exit code $LASTEXITCODE."
    }

    return @($output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Add-Section {
    param([Parameter(Mandatory = $true)][string]$Title)
    $summaryLines.Add("")
    $summaryLines.Add("## $Title")
    $summaryLines.Add("")
}

Push-Location $repoRoot
try {
    $branch = (& git branch --show-current)
    if ([string]::IsNullOrWhiteSpace($branch)) {
        $branch = "(detached)"
    }

    $commit = (& git rev-parse --short HEAD)
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to determine current commit."
    }

    $status = @(Invoke-GitLines @("status", "--short"))
    $workflows = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot ".github/workflows") -Filter "*.yml" -File |
        Sort-Object Name |
        ForEach-Object { ".github/workflows/$($_.Name)" })
    $scripts = @(
        "scripts/check-environment.ps1",
        "scripts/verify-local.ps1",
        "scripts/run-security-checks.ps1",
        "scripts/check-documentation-drift.ps1",
        "scripts/triage-dependency-updates.ps1",
        "scripts/generate-engineering-status.ps1"
    )
    $roadmap = Get-Content -LiteralPath (Join-Path $repoRoot "docs/ai-enablement-roadmap.md")
    $openRoadmapItems = @($roadmap | Where-Object { $_ -match "^- \[ \]" })

    $summaryLines.Add("# Weekly engineering status packet")
    $summaryLines.Add("")
    $summaryLines.Add("- Generated: $(Get-Date -Format o)")
    $summaryLines.Add("- Branch: ``$branch``")
    $summaryLines.Add("- Commit: ``$commit``")
    $summaryLines.Add("- Worktree entries: $($status.Count)")
    $summaryLines.Add("- Open roadmap checklist items: $($openRoadmapItems.Count)")

    Add-Section "Roadmap items still open"
    if ($openRoadmapItems.Count -eq 0) {
        $summaryLines.Add("- None in ``docs/ai-enablement-roadmap.md``.")
    }
    else {
        foreach ($item in $openRoadmapItems) {
            $summaryLines.Add($item)
        }
    }

    Add-Section "Workflow inventory"
    foreach ($workflow in $workflows) {
        $summaryLines.Add("- ``$workflow``")
    }

    Add-Section "Deterministic local commands"
    foreach ($script in $scripts) {
        $exists = Test-Path -LiteralPath (Join-Path $repoRoot $script)
        $state = if ($exists) { "present" } else { "missing" }
        $summaryLines.Add("- ``$script`` - $state")
    }

    Add-Section "Current worktree"
    if ($status.Count -eq 0) {
        $summaryLines.Add("- Clean")
    }
    else {
        foreach ($line in $status) {
            $summaryLines.Add("- ``$line``")
        }
    }

    Add-Section "Weekly review checklist"
    $summaryLines.Add("- Review dependency-update PRs using ``docs/dependency-update-triage.md``.")
    $summaryLines.Add("- Review latest CI failures using ``.agents/skills/triage-github-ci/references/triage-guide.md``.")
    $summaryLines.Add("- Review documentation drift packets before changing or posting docs findings.")
    $summaryLines.Add("- Review security scan/audit state without exposing tokens or personal financial data.")
    $summaryLines.Add("- Check stale issues/PRs manually before commenting, labeling, assigning, or closing.")

    $summaryText = $summaryLines -join [Environment]::NewLine
    if (-not [string]::IsNullOrWhiteSpace($SummaryPath)) {
        Set-Content -LiteralPath $SummaryPath -Value $summaryText -Encoding UTF8
    }

    Write-Host $summaryText
}
finally {
    Pop-Location
}
