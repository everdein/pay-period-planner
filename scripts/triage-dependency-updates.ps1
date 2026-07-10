# cspell:words LASTEXITCODE pscustomobject

param(
    [string]$BaseRef = "",
    [string]$HeadRef = "",
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

function Normalize-RepoPath {
    param([Parameter(Mandatory = $true)][string]$Path)
    return ($Path -replace "\\", "/").Trim()
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

function Read-JsonFile {
    param([Parameter(Mandatory = $true)][string]$Path)
    return Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
}

function Add-DependencyRows {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Manifest,
        [Parameter(Mandatory = $true)]
        [string]$Scope,
        [AllowNull()]
        [object]$Dependencies
    )

    if ($null -eq $Dependencies) {
        return @()
    }

    $rows = @()
    $properties = $Dependencies.PSObject.Properties | Sort-Object Name
    foreach ($property in $properties) {
        $rows += [pscustomobject]@{
            Manifest = $Manifest
            Scope = $Scope
            Name = $property.Name
            Version = [string]$property.Value
        }
    }

    return $rows
}

function Get-NpmDependencies {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Manifest
    )

    $json = Read-JsonFile (Join-Path $repoRoot $Manifest)
    $productionDependencies = $json.PSObject.Properties["dependencies"]
    $developmentDependencies = $json.PSObject.Properties["devDependencies"]
    $rows = @()
    if ($null -ne $productionDependencies) {
        $rows += Add-DependencyRows -Manifest $Manifest -Scope "production" -Dependencies $productionDependencies.Value
    }
    if ($null -ne $developmentDependencies) {
        $rows += Add-DependencyRows -Manifest $Manifest -Scope "development" -Dependencies $developmentDependencies.Value
    }
    return @($rows)
}

function Get-MavenCoordinates {
    param([Parameter(Mandatory = $true)][string]$Manifest)

    [xml]$xml = Get-Content -Raw -LiteralPath (Join-Path $repoRoot $Manifest)
    $namespace = New-Object System.Xml.XmlNamespaceManager($xml.NameTable)
    $namespace.AddNamespace("m", "http://maven.apache.org/POM/4.0.0")

    function Get-ChildText {
        param(
            [Parameter(Mandatory = $true)]
            [System.Xml.XmlNode]$Node,
            [Parameter(Mandatory = $true)]
            [string]$Name,
            [string]$Default = ""
        )

        $child = $Node.SelectSingleNode("m:$Name", $namespace)
        if ($null -ne $child) {
            return $child.InnerText
        }

        return $Default
    }

    $rows = @()
    $dependencies = $xml.SelectNodes("//m:project/m:dependencies/m:dependency", $namespace)
    foreach ($dependency in $dependencies) {
        $group = Get-ChildText -Node $dependency -Name "groupId"
        $artifact = Get-ChildText -Node $dependency -Name "artifactId"
        $scope = Get-ChildText -Node $dependency -Name "scope" -Default "compile"
        $version = Get-ChildText -Node $dependency -Name "version" -Default "managed"
        $rows += [pscustomobject]@{
            Manifest = $Manifest
            Scope = $scope
            Name = "$group/$artifact"
            Version = $version
        }
    }

    $parent = $xml.SelectSingleNode("//m:project/m:parent", $namespace)
    if ($null -ne $parent) {
        $parentGroup = Get-ChildText -Node $parent -Name "groupId"
        $parentArtifact = Get-ChildText -Node $parent -Name "artifactId"
        $parentVersion = Get-ChildText -Node $parent -Name "version"
        $rows += [pscustomobject]@{
            Manifest = $Manifest
            Scope = "parent"
            Name = "$parentGroup/$parentArtifact"
            Version = $parentVersion
        }
    }

    return @($rows | Sort-Object Manifest, Scope, Name)
}

Push-Location $repoRoot
try {
    $changedFiles = @(Get-ChangedFiles)
    $dependencyFiles = @(
        "package.json",
        "package-lock.json",
        "frontend/package.json",
        "frontend/package-lock.json",
        "backend/pom.xml",
        ".github/dependabot.yml",
        ".github/workflows/dependency-update-triage.yml",
        ".github/workflows/weekly-maintenance.yml"
    )
    $changedDependencyFiles = @($changedFiles | Where-Object { $dependencyFiles -contains $_ })

    $dependencies = @()
    $dependencies += Get-NpmDependencies "package.json"
    $dependencies += Get-NpmDependencies "frontend/package.json"
    $dependencies += Get-MavenCoordinates "backend/pom.xml"

    $runtimeDependencyChanges = @($changedFiles | Where-Object {
        $_ -in @("frontend/package.json", "frontend/package-lock.json", "backend/pom.xml")
    })
    $toolingDependencyChanges = @($changedFiles | Where-Object {
        $_ -in @("package.json", "package-lock.json", ".github/dependabot.yml") -or
        $_ -like ".github/workflows/*"
    })

    $summaryLines.Add("# Dependency update triage packet")
    $summaryLines.Add("")
    if (-not [string]::IsNullOrWhiteSpace($BaseRef) -and -not [string]::IsNullOrWhiteSpace($HeadRef)) {
        $summaryLines.Add("- Compared: ``$BaseRef`` to ``$HeadRef``")
    }
    else {
        $summaryLines.Add("- Compared: local working tree against ``HEAD``")
    }
    $summaryLines.Add("- Direct dependencies inventoried: $($dependencies.Count)")
    $summaryLines.Add("- Dependency-related files changed: $($changedDependencyFiles.Count)")
    $summaryLines.Add("")

    $summaryLines.Add("## Changed dependency files")
    if ($changedDependencyFiles.Count -eq 0) {
        $summaryLines.Add("- No dependency manifests, lockfiles, Dependabot config, or dependency triage workflows changed.")
    }
    else {
        foreach ($file in $changedDependencyFiles) {
            $summaryLines.Add("- ``$file``")
        }
    }
    $summaryLines.Add("")

    $summaryLines.Add("## Change classification")
    if ($runtimeDependencyChanges.Count -gt 0) {
        $summaryLines.Add("- Runtime or backend/frontend dependency surface changed. Run affected builds/tests and review compatibility.")
    }
    if ($toolingDependencyChanges.Count -gt 0) {
        $summaryLines.Add("- Tooling, lockfile, Dependabot, or GitHub Actions dependency surface changed. Verify CI-equivalent commands.")
    }
    if ($runtimeDependencyChanges.Count -eq 0 -and $toolingDependencyChanges.Count -eq 0) {
        $summaryLines.Add("- No dependency-update change detected in the current comparison.")
    }
    $summaryLines.Add("")

    $summaryLines.Add("## Direct dependency inventory")
    foreach ($group in ($dependencies | Group-Object Manifest)) {
        $summaryLines.Add("")
        $summaryLines.Add("### $($group.Name)")
        foreach ($dependency in ($group.Group | Sort-Object Scope, Name)) {
            $summaryLines.Add("- ``$($dependency.Name)`` [$($dependency.Scope)] $($dependency.Version)")
        }
    }
    $summaryLines.Add("")

    $summaryLines.Add("## Triage checklist")
    $summaryLines.Add("")
    $summaryLines.Add("- Identify direct vs transitive update and whether it is runtime, development, Maven parent, plugin, or GitHub Action.")
    $summaryLines.Add("- Read release notes for major updates and security fixes before accepting generated changes.")
    $summaryLines.Add("- Regenerate the correct lockfile with the owning package manager.")
    $summaryLines.Add("- Run targeted tests, then full local verification when compatibility risk is non-trivial.")
    $summaryLines.Add("- Run authenticated security checks when available; missing Snyk/authentication is not a pass.")
    $summaryLines.Add("- Do not auto-merge dependency PRs without passing CI and human review.")

    $summaryText = $summaryLines -join [Environment]::NewLine
    if (-not [string]::IsNullOrWhiteSpace($SummaryPath)) {
        Set-Content -LiteralPath $SummaryPath -Value $summaryText -Encoding UTF8
    }

    Write-Host $summaryText
}
finally {
    Pop-Location
}
