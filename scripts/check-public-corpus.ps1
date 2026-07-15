# cspell:words pscustomobject

param(
    [string]$ManifestPath = "docs/public-corpus.json"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$resolvedRepoRoot = [System.IO.Path]::GetFullPath($repoRoot)
$repoPrefix = $resolvedRepoRoot.TrimEnd(
    [System.IO.Path]::DirectorySeparatorChar,
    [System.IO.Path]::AltDirectorySeparatorChar
) + [System.IO.Path]::DirectorySeparatorChar
$resolvedManifestPath = if ([System.IO.Path]::IsPathRooted($ManifestPath)) {
    [System.IO.Path]::GetFullPath($ManifestPath)
}
else {
    [System.IO.Path]::GetFullPath((Join-Path $resolvedRepoRoot $ManifestPath))
}

if (-not (Test-Path -LiteralPath $resolvedManifestPath -PathType Leaf)) {
    throw "Missing public corpus manifest: $resolvedManifestPath"
}

$manifest = Get-Content -LiteralPath $resolvedManifestPath -Raw | ConvertFrom-Json
if ($manifest.schemaVersion -ne 1) {
    throw "Unsupported public corpus manifest schema version: $($manifest.schemaVersion)"
}
if ($manifest.defaultPolicy -ne "deny") {
    throw "The public corpus manifest must use a deny-by-default policy."
}

$entries = @($manifest.entries)
if ($entries.Count -eq 0) {
    throw "The public corpus manifest has no entries."
}

$allowedKinds = @("document", "source", "test", "workflow")
$allowedExtensions = @{
    document = @(".md")
    source = @(".java", ".sql", ".ts", ".tsx")
    test = @(".java", ".ts", ".tsx")
    workflow = @(".ps1", ".yaml", ".yml")
}
$forbiddenPathPattern =
    '(^|/)(\.agents|\.codex|node_modules|coverage|dist|target|test-results)(/|$)|' +
    '(^|/)\.env|(^|/)AGENTS\.md$|(^|/)package-lock\.json$|' +
    '^backend/data/|^docs/images/'
$seenPaths = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::OrdinalIgnoreCase
)
$resolvedEntries = New-Object System.Collections.Generic.List[object]
$problems = New-Object System.Collections.Generic.List[string]

foreach ($entry in $entries) {
    $path = [string]$entry.path
    $kind = [string]$entry.kind
    $reason = [string]$entry.reason

    if ([string]::IsNullOrWhiteSpace($path)) {
        $problems.Add("An entry has no path.")
        continue
    }

    $normalizedPath = ($path -replace "\\", "/").Trim()
    $segments = @($normalizedPath -split "/")
    if (
        [System.IO.Path]::IsPathRooted($path) -or
        $normalizedPath.StartsWith("/") -or
        $segments -contains ".." -or
        $normalizedPath -match '[*?\[]'
    ) {
        $problems.Add("Entry path must be an exact repository-relative path: $path")
        continue
    }

    if (-not $seenPaths.Add($normalizedPath)) {
        $problems.Add("Duplicate corpus entry: $normalizedPath")
        continue
    }
    if ($allowedKinds -notcontains $kind) {
        $problems.Add("Unsupported kind '$kind' for $normalizedPath")
        continue
    }
    if ([string]::IsNullOrWhiteSpace($reason)) {
        $problems.Add("Corpus entry has no inclusion reason: $normalizedPath")
    }
    if ($normalizedPath -match $forbiddenPathPattern) {
        $problems.Add("Corpus entry uses a forbidden generated, private, or local-data path: $normalizedPath")
    }

    $resolvedPath = [System.IO.Path]::GetFullPath(
        (Join-Path $resolvedRepoRoot ($normalizedPath -replace "/", [System.IO.Path]::DirectorySeparatorChar))
    )
    if (-not $resolvedPath.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        $problems.Add("Corpus entry resolves outside the repository: $normalizedPath")
        continue
    }
    if (-not (Test-Path -LiteralPath $resolvedPath -PathType Leaf)) {
        $problems.Add("Corpus entry does not exist: $normalizedPath")
        continue
    }

    $extension = [System.IO.Path]::GetExtension($resolvedPath).ToLowerInvariant()
    if ($allowedExtensions[$kind] -notcontains $extension) {
        $problems.Add("Corpus entry extension '$extension' is not allowed for kind '$kind': $normalizedPath")
    }

    & git -C $resolvedRepoRoot check-ignore --quiet -- $normalizedPath 2>$null
    if ($LASTEXITCODE -eq 0) {
        $problems.Add("Corpus entry is ignored by Git: $normalizedPath")
    }
    elseif ($LASTEXITCODE -ne 1) {
        throw "git check-ignore failed for $normalizedPath with exit code $LASTEXITCODE."
    }

    $resolvedEntries.Add(
        [PSCustomObject]@{
            Kind = $kind
            Path = $normalizedPath
            ResolvedPath = $resolvedPath
        }
    )
}

$requiredDocuments = @(
    "README.md",
    "docs/README.md",
    "docs/portfolio-case-study.md",
    "docs/architecture-map.md",
    "docs/engineering-evidence.md",
    "docs/known-limitations.md",
    "docs/adr/README.md"
)
foreach ($requiredDocument in $requiredDocuments) {
    if (-not $seenPaths.Contains($requiredDocument)) {
        $problems.Add("Required public document is missing from the corpus: $requiredDocument")
    }
}

foreach ($document in @($resolvedEntries | Where-Object { $_.Kind -eq "document" })) {
    $content = Get-Content -LiteralPath $document.ResolvedPath -Raw
    foreach ($match in [regex]::Matches($content, '\[[^\]]*\]\(([^)]+)\)')) {
        $target = $match.Groups[1].Value.Trim().Trim("<", ">")
        if ($target -match '^(https?://|mailto:|#)') {
            continue
        }

        $pathPart = ($target -split "#", 2)[0]
        if ([string]::IsNullOrWhiteSpace($pathPart)) {
            continue
        }

        $decodedPath = [uri]::UnescapeDataString($pathPart)
        $linkPath = [System.IO.Path]::GetFullPath(
            (Join-Path (Split-Path -Parent $document.ResolvedPath) $decodedPath)
        )
        if (-not (Test-Path -LiteralPath $linkPath)) {
            $problems.Add("Broken local link in $($document.Path): $target")
        }
    }
}

if ($problems.Count -gt 0) {
    Write-Host "Public corpus validation failed:" -ForegroundColor Red
    foreach ($problem in $problems) {
        Write-Host "- $problem" -ForegroundColor Red
    }
    exit 1
}

$counts = @($resolvedEntries | Group-Object Kind | Sort-Object Name)
Write-Host "Public corpus manifest passed." -ForegroundColor Green
foreach ($count in $counts) {
    Write-Host ("{0,-10} {1}" -f $count.Name, $count.Count)
}
exit 0
