[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('budget', 'foundation', 'application')]
    [string]$Stage,
    [Parameter(Mandatory)]
    [string]$ResourceGroup,
    [Parameter(Mandatory)]
    [string]$ParameterFile,
    [Parameter(Mandatory)]
    [ValidateRange(1, 100000)]
    [int]$ApprovedMonthlyBudget
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$parameters = (Resolve-Path -LiteralPath $ParameterFile).Path
$relative = [IO.Path]::GetRelativePath($repoRoot, $parameters)
if (-not $relative.StartsWith('..' + [IO.Path]::DirectorySeparatorChar)) {
    throw 'Keep Azure parameter files outside the repository. They can contain billing contacts and secrets.'
}
$values = (Get-Content -LiteralPath $parameters -Raw | ConvertFrom-Json).parameters
if ($Stage -eq 'budget' -and $values.monthlyBudget.value -ne $ApprovedMonthlyBudget) {
    throw 'The budget parameter must match the approved monthly amount.'
}
if ($Stage -eq 'application') {
    if ($values.image.value -notmatch '^[a-z0-9][a-z0-9/._-]*@sha256:[a-f0-9]{64}$') {
        throw 'The application image must use a repository and immutable SHA-256 digest.'
    }
    $bootstrap = $values.registrationMode.value -eq 'owner-bootstrap'
    if ($bootstrap -and ($values.allowPublicIngress.value -eq $true -or [string]::IsNullOrWhiteSpace($values.ownerBootstrapVerifierSecretUri.value))) {
        throw 'Owner bootstrap requires private ingress and an existing verifier secret URI.'
    }
}
if (-not (Get-Command az -ErrorAction SilentlyContinue)) {
    throw 'Azure CLI is required. Install it and sign in to the selected personal subscription first.'
}

if ($Stage -ne 'budget') {
    $subscriptionId = & az account show --query id -o tsv --only-show-errors
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($subscriptionId)) {
        throw 'Azure account selection failed. Sign in and select the intended personal subscription before previewing.'
    }
    $amount = & az rest --method get --url "https://management.azure.com/subscriptions/$subscriptionId/resourceGroups/$ResourceGroup/providers/Microsoft.Consumption/budgets/planner-monthly?api-version=2024-08-01" --query properties.amount -o tsv --only-show-errors
    if ($LASTEXITCODE -ne 0 -or [decimal]$amount -ne $ApprovedMonthlyBudget) {
        throw 'A verified planner-monthly budget matching the approved amount is required before previewing workload resources.'
    }
}

$template = Join-Path $repoRoot "infra/azure/$Stage.bicep"
& az deployment group what-if --resource-group $ResourceGroup --template-file $template --parameters "@$parameters" --result-format ResourceIdOnly --only-show-errors
if ($LASTEXITCODE -ne 0) {
    throw 'Azure preview failed. No deployment was requested.'
}
