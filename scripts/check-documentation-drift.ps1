# cspell:words LASTEXITCODE pscustomobject

param(
    [string]$BaseRef = "",
    [string]$HeadRef = "",
    [switch]$FailOnHighRisk,
    [string]$SummaryPath = $env:GITHUB_STEP_SUMMARY
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$sourceMapPath = Join-Path $repoRoot ".agents/skills/audit-end-to-end-docs/references/source-map.md"
$summaryLines = New-Object System.Collections.Generic.List[string]
$allRisks = New-Object System.Collections.Generic.List[object]

function Invoke-GitLines {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $output = & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed with exit code $LASTEXITCODE."
    }

    return @($output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Normalize-RepoPath {
    param([Parameter(Mandatory = $true)][string]$Path)
    return ($Path -replace "\\", "/").Trim()
}

function Test-AnyPattern {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Paths,
        [Parameter(Mandatory = $true)]
        [string[]]$Patterns
    )

    foreach ($path in $Paths) {
        foreach ($pattern in $Patterns) {
            if ($path -match $pattern) {
                return $true
            }
        }
    }

    return $false
}

function Test-ExpectedDocChanged {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$ChangedFiles,
        [Parameter(Mandatory = $true)]
        [string[]]$ExpectedDocs
    )

    foreach ($changedFile in $ChangedFiles) {
        foreach ($expectedDoc in $ExpectedDocs) {
            $pattern = [System.Management.Automation.WildcardPattern]::new(
                (Normalize-RepoPath $expectedDoc),
                [System.Management.Automation.WildcardOptions]::IgnoreCase
            )
            if ($pattern.IsMatch($changedFile)) {
                return $true
            }
        }
    }

    return $false
}

function Get-ChangedFiles {
    if (-not [string]::IsNullOrWhiteSpace($BaseRef) -and -not [string]::IsNullOrWhiteSpace($HeadRef)) {
        return @(Invoke-GitLines @("diff", "--name-only", $BaseRef, $HeadRef) |
            ForEach-Object { Normalize-RepoPath $_ } |
            Sort-Object -Unique)
    }

    $tracked = @(Invoke-GitLines @("diff", "--name-only", "HEAD"))
    $untracked = @(Invoke-GitLines @("ls-files", "--others", "--exclude-standard"))

    return @($tracked + $untracked |
        ForEach-Object { Normalize-RepoPath $_ } |
        Sort-Object -Unique)
}

function Get-AllRepoFiles {
    $tracked = @(Invoke-GitLines @("ls-files"))
    $untracked = @(Invoke-GitLines @("ls-files", "--others", "--exclude-standard"))

    return @($tracked + $untracked |
        ForEach-Object { Normalize-RepoPath $_ } |
        Sort-Object -Unique)
}

function Test-RepositoryReference {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Reference,
        [Parameter(Mandatory = $true)]
        [string[]]$RepoFiles
    )

    $normalizedReference = Normalize-RepoPath $Reference
    $wildcard = [System.Management.Automation.WildcardPattern]::new(
        $normalizedReference,
        [System.Management.Automation.WildcardOptions]::IgnoreCase
    )
    $fileNameWildcard = [System.Management.Automation.WildcardPattern]::new(
        (Split-Path -Leaf $normalizedReference),
        [System.Management.Automation.WildcardOptions]::IgnoreCase
    )

    foreach ($repoFile in $RepoFiles) {
        if ($repoFile.Equals($normalizedReference, [System.StringComparison]::OrdinalIgnoreCase)) {
            return $true
        }
        if ($wildcard.IsMatch($repoFile)) {
            return $true
        }
        if (-not $normalizedReference.Contains("/") -and $fileNameWildcard.IsMatch((Split-Path -Leaf $repoFile))) {
            return $true
        }
    }

    return $false
}

function Add-Risk {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Severity,
        [Parameter(Mandatory = $true)]
        [string]$Area,
        [Parameter(Mandatory = $true)]
        [string]$Message,
        [string[]]$Files = @(),
        [string[]]$ExpectedDocs = @()
    )

    $allRisks.Add([pscustomobject]@{
        Severity = $Severity
        Area = $Area
        Message = $Message
        Files = $Files
        ExpectedDocs = $ExpectedDocs
    })
}

Push-Location $repoRoot
try {
    $changedFiles = @(Get-ChangedFiles)
    $allRepoFiles = @(Get-AllRepoFiles)

    $documentationPatterns = @(
        "^AGENTS\.md$",
        "(^|/)README\.md$",
        "^docs/",
        "^\.agents/skills/.+\.md$",
        "^\.github/(ISSUE_TEMPLATE/|PULL_REQUEST_TEMPLATE\.md|copilot-instructions\.md)"
    )
    $documentationChanges = @($changedFiles | Where-Object {
        Test-AnyPattern @($_) $documentationPatterns
    })

    $rules = @(
        [pscustomobject]@{
            Area = "API contract"
            Severity = "High"
            SourcePatterns = @("^backend/src/main/java/.*/(api|dto)/", "^frontend/src/api/", "^frontend/src/features/financials/financialsTypes\.ts$")
            ExpectedDocs = @("docs/api-contract.md", "README.md")
        },
        [pscustomobject]@{
            Area = "Persistence and database"
            Severity = "High"
            SourcePatterns = @("^backend/src/main/java/.*/repository/", "^backend/src/main/resources/db/migration/", "^backend/src/main/resources/application.*\.properties$", "^scripts/.*postgres.*\.ps1$")
            ExpectedDocs = @("docs/database-storage-guide.md", "docs/known-limitations.md", "backend/README.md", "AGENTS.md")
        },
        [pscustomobject]@{
            Area = "Verification and local scripts"
            Severity = "Medium"
            SourcePatterns = @("^scripts/", "^package\.json$", "^frontend/package\.json$", "^backend/pom\.xml$", "^frontend/vite\.config\.ts$")
            ExpectedDocs = @("docs/verification-matrix.md", "README.md", "AGENTS.md", "frontend/README.md", "backend/README.md")
        },
        [pscustomobject]@{
            Area = "GitHub workflows and hosted automation"
            Severity = "Medium"
            SourcePatterns = @("^\.github/workflows/", "^\.github/ISSUE_TEMPLATE/", "^\.github/PULL_REQUEST_TEMPLATE\.md$", "^\.github/copilot-instructions\.md$")
            ExpectedDocs = @("docs/github-ai-workflows.md", "docs/verification-matrix.md", "README.md", "AGENTS.md")
        },
        [pscustomobject]@{
            Area = "Agent and AI workflow"
            Severity = "Medium"
            SourcePatterns = @("^\.agents/", "^\.codex/agents/", "^docs/ai-enablement-roadmap\.md$")
            ExpectedDocs = @("AGENTS.md", "docs/ai-enablement-roadmap.md", "docs/github-ai-workflows.md", ".agents/skills/audit-end-to-end-docs/references/source-map.md")
        },
        [pscustomobject]@{
            Area = "Frontend behavior"
            Severity = "Medium"
            SourcePatterns = @("^frontend/src/", "^frontend/e2e/", "^frontend/playwright\.config\.ts$")
            ExpectedDocs = @("frontend/README.md", "docs/verification-matrix.md", "docs/architecture-map.md")
        }
    )

    foreach ($rule in $rules) {
        $matchedFiles = @($changedFiles | Where-Object {
            Test-AnyPattern @($_) $rule.SourcePatterns
        })
        if ($matchedFiles.Count -eq 0) {
            continue
        }

        if (-not (Test-ExpectedDocChanged $changedFiles $rule.ExpectedDocs)) {
            Add-Risk `
                -Severity $rule.Severity `
                -Area $rule.Area `
                -Message "Source files changed without a matching documentation owner update." `
                -Files $matchedFiles `
                -ExpectedDocs $rule.ExpectedDocs
        }
    }

    if (-not (Test-Path -LiteralPath $sourceMapPath)) {
        Add-Risk `
            -Severity "High" `
            -Area "Documentation source map" `
            -Message "Documentation source map is missing." `
            -Files @(".agents/skills/audit-end-to-end-docs/references/source-map.md")
    }
    else {
        $sourceMapContent = Get-Content -Raw -LiteralPath $sourceMapPath
        $references = [regex]::Matches($sourceMapContent, '`([^`]+)`') |
            ForEach-Object { $_.Groups[1].Value } |
            Where-Object {
                $_ -notmatch "^\$" -and
                $_ -notmatch "\s" -and
                $_ -notmatch "^(http|https)://" -and
                $_ -notmatch "^[A-Za-z0-9_-]+$"
            } |
            Sort-Object -Unique

        $missingReferences = @($references | Where-Object {
            -not (Test-RepositoryReference $_ $allRepoFiles)
        })

        if ($missingReferences.Count -gt 0) {
            Add-Risk `
                -Severity "High" `
                -Area "Documentation source map" `
                -Message "Source map references do not resolve to tracked or new repository paths." `
                -Files $missingReferences
        }
    }

    $summaryLines.Add("# Documentation drift packet")
    $summaryLines.Add("")
    if (-not [string]::IsNullOrWhiteSpace($BaseRef) -and -not [string]::IsNullOrWhiteSpace($HeadRef)) {
        $summaryLines.Add("- Compared: ``$BaseRef`` to ``$HeadRef``")
    }
    else {
        $summaryLines.Add("- Compared: local working tree against ``HEAD``")
    }
    $summaryLines.Add("- Changed files: $($changedFiles.Count)")
    $summaryLines.Add("- Documentation files changed: $($documentationChanges.Count)")
    $summaryLines.Add("- Drift risks found: $($allRisks.Count)")
    $summaryLines.Add("")

    if ($changedFiles.Count -gt 0) {
        $summaryLines.Add("## Changed files")
        foreach ($file in $changedFiles) {
            $summaryLines.Add("- ``$file``")
        }
        $summaryLines.Add("")
    }

    if ($allRisks.Count -eq 0) {
        $summaryLines.Add("## Drift assessment")
        $summaryLines.Add("")
        $summaryLines.Add("No deterministic documentation drift risks were detected.")
    }
    else {
        $summaryLines.Add("## Drift risks")
        foreach ($risk in $allRisks) {
            $summaryLines.Add("")
            $summaryLines.Add("### [$($risk.Severity)] $($risk.Area)")
            $summaryLines.Add("")
            $summaryLines.Add($risk.Message)
            if ($risk.ExpectedDocs.Count -gt 0) {
                $summaryLines.Add("")
                $summaryLines.Add("Expected documentation owner(s):")
                foreach ($doc in $risk.ExpectedDocs) {
                    $summaryLines.Add("- ``$doc``")
                }
            }
            if ($risk.Files.Count -gt 0) {
                $summaryLines.Add("")
                $summaryLines.Add("Relevant file(s):")
                foreach ($file in $risk.Files) {
                    $summaryLines.Add("- ``$file``")
                }
            }
        }
    }

    $summaryLines.Add("")
    $summaryLines.Add("## Use with AI")
    $summaryLines.Add("")
    $summaryLines.Add("Use this packet as context only. Verify any claimed drift against the")
    $summaryLines.Add("source map, executable files, tests, workflows, and docs before changing")
    $summaryLines.Add("documentation or posting comments.")

    foreach ($risk in $allRisks) {
        $annotation = "$($risk.Severity) documentation drift risk in $($risk.Area): $($risk.Message)"
        Write-Host "::warning::$annotation"
    }

    $summaryText = $summaryLines -join [Environment]::NewLine
    if (-not [string]::IsNullOrWhiteSpace($SummaryPath)) {
        Set-Content -LiteralPath $SummaryPath -Value $summaryText -Encoding UTF8
    }

    Write-Host $summaryText

    $highRiskCount = @($allRisks | Where-Object { $_.Severity -eq "High" }).Count
    if ($FailOnHighRisk -and $highRiskCount -gt 0) {
        throw "Documentation drift check found $highRiskCount high-risk item(s)."
    }
}
finally {
    Pop-Location
}
