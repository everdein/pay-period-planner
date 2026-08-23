# Azure Deployment Prerequisites

Status: Owner setup checklist; no credentials or subscription identifiers belong
in this repository

## Purpose

Complete this checklist before the first Azure resource deployment. It separates
interactive account and billing steps that only the owner can perform from
repository and infrastructure work that can be automated later.

The target remains the production-shaped, single-owner, synthetic-only boundary
accepted in
[ADR 0030](adr/0030-use-a-production-shaped-single-owner-azure-environment.md).
Completing account setup does not authorize personal financial data in Azure.

## Owner-Only Account Setup

### 1. Create the Azure account

- [ ] Use a durable personal Microsoft account that the owner expects to retain.
- [ ] Create an Azure free account at the official
      [Azure free account page](https://azure.microsoft.com/free/).
- [ ] Complete Microsoft's identity and payment verification. Expect to provide
      contact information, phone verification, and an accepted payment method;
      confirm the current terms during signup.
- [ ] Store account-recovery information in the owner's password manager, not in
      the repository, shell profile, issue tracker, or project documentation.
- [ ] Enable multi-factor authentication before creating workload resources.
- [ ] Record the tenant and subscription names in the password manager. Do not
      record tenant IDs, subscription IDs, payment details, or tokens in source.

Eligible new accounts may include promotional credit and limited free service
quantities. Those offers, durations, and eligible SKUs can change. Confirm them
in the portal instead of designing the application around a promotional tier.
Microsoft currently documents that a free account is disabled when its initial
credit expires unless the owner upgrades it, and that usage outside free limits
can be billed after upgrade. See
[Avoid charges with an Azure free account](https://learn.microsoft.com/azure/cost-management-billing/manage/avoid-charges-free-account).

### 2. Verify the subscription boundary

- [ ] Confirm the active directory and subscription in the Azure portal.
- [ ] Give the subscription a recognizable display name for personal development
      if the account permits renaming it.
- [ ] Keep one subscription for the first learning environment rather than
      creating multiple billing boundaries prematurely.
- [ ] Confirm that the owner can create role assignments, federated identities,
      budgets, and the planned resource types.
- [ ] Do not grant another user, application, or GitHub workflow subscription-wide
      Owner or Contributor access by default.

The owner's interactive account may need broad rights during initial setup. The
application identity and deployment identity do not. Scope each workload role
to the development resource group or narrower resource whenever Azure permits
it.

### 3. Establish cost safeguards before workload resources

- [ ] Choose a monthly spending ceiling.
- [ ] Choose warning notifications below the ceiling, initially recommended at
      50 percent and 80 percent, plus a 100 percent notification.
- [ ] Create the Azure budget and notification recipients before provisioning
      Container Apps, PostgreSQL, or telemetry resources.
- [ ] Verify the current free-credit balance, expiration date, and free-service
      limits in Cost Management.
- [ ] Save an Azure pricing-calculator estimate for the chosen region and SKUs.
- [ ] Review actual cost daily during initial setup and weekly afterward.

A budget alert is a notification, not a guaranteed spending cap. The emergency
procedure must also support disabling ingress, scaling down the application, and
deleting disposable resources through reviewed infrastructure changes.

## Workstation Prerequisites

The August 23, 2026 workstation check found:

| Tool                    | Current status | Needed for                                            |
| ----------------------- | -------------- | ----------------------------------------------------- |
| Git                     | Available      | Source and branch workflow                            |
| GitHub CLI              | Available      | Pull requests and repository setup                    |
| Java                    | Available      | Spring Boot build and tests                           |
| Node.js and npm         | Available      | React build and tests                                 |
| Azure CLI               | Missing        | Azure sign-in, validation, preview, and deployment    |
| Local container runtime | Missing        | Building and testing the production container locally |

Before implementation:

- [ ] Install Azure CLI using Microsoft's current Windows instructions. The
      documented WinGet package is `Microsoft.AzureCLI`.
- [ ] Close and reopen the terminal, then record only the output of `az version`.
- [ ] Run `az login` interactively and verify the intended subscription with
      `az account show`; do not copy its JSON into the repository or chat.
- [ ] Install or select a local OCI-compatible container runtime, normally
      Docker Desktop on this Windows workstation.
- [ ] Confirm Linux-container mode and verify `docker version` and a minimal
      local container run.
- [ ] Confirm the existing JDK is Java 21 and the existing Node.js release meets
      the repository's supported toolchain.

Official installation reference:
[Install Azure CLI on Windows](https://learn.microsoft.com/cli/azure/install-azure-cli-windows).
Bicep support is available through current Azure CLI releases; a separate
global Bicep installation should not become an undocumented second toolchain.

Docker Desktop has its own license and system requirements. Review those terms
before installation. An alternative such as Podman is acceptable only if the
repository scripts and CI remain ordinary OCI/Docker compatible.

## Azure Service Prerequisites

After account and tool setup, but before deploying the application:

- [ ] Confirm East US 2 supports the selected Container Apps, PostgreSQL
      Flexible Server, Container Registry, Key Vault, Application Insights, and
      private-networking SKUs for this subscription.
- [ ] Register only the resource providers required by the reviewed Bicep plan.
      Expected providers include `Microsoft.App`, `Microsoft.ContainerRegistry`,
      `Microsoft.DBforPostgreSQL`, `Microsoft.KeyVault`, `Microsoft.ManagedIdentity`,
      `Microsoft.Network`, `Microsoft.OperationalInsights`, and `Microsoft.Insights`.
- [ ] Confirm sufficient regional quota for the selected Container Apps and
      PostgreSQL SKUs before deployment.
- [ ] Select a short globally unique suffix without personal information.
- [ ] Confirm the development resource-group name and required tags.
- [ ] Run Bicep lint, validation, and a preview before the first write operation.

Do not register providers, request quota, or create resources manually until the
infrastructure definition shows why they are needed. Portal experiments must be
captured in Bicep or removed before the environment is treated as reproducible.

## GitHub Deployment Prerequisites

The initial manual Azure login is for owner setup and diagnosis. Repeatable
deployment uses GitHub OpenID Connect federation without a client secret.

- [ ] Create a protected GitHub environment named `azure-dev`.
- [ ] Restrict deployment to the default branch and require owner approval.
- [ ] Create a user-assigned managed identity or Microsoft Entra application for
      GitHub deployment.
- [ ] Scope its Azure roles to the development resource group or narrower
      resources; avoid subscription-wide Contributor when possible.
- [ ] Add a federated credential whose subject is limited to this repository and
      the `azure-dev` GitHub environment.
- [ ] Store the client ID, tenant ID, and subscription ID as GitHub environment
      configuration or secrets as required by the selected actions. They are
      identifiers, but keeping them environment-scoped avoids unnecessary
      repository disclosure.
- [ ] Give the deployment job only `contents: read` and `id-token: write` unless
      another permission is justified.
- [ ] Do not create or store a long-lived Azure client secret.

Microsoft documents both Entra applications and user-assigned managed identities
as supported OIDC trust targets. See
[Use Azure Login with OpenID Connect](https://learn.microsoft.com/azure/developer/github/connect-from-azure-openid-connect).

## Values to Confirm Without Committing Them

Keep the following in the owner's password manager, Azure/GitHub settings, or
local ignored configuration as appropriate:

- Azure sign-in email and recovery details;
- tenant and subscription identifiers;
- billing and payment information;
- final monthly ceiling and alert recipients;
- globally unique resource-name suffix;
- owner-bootstrap credential;
- PostgreSQL administrator or emergency credentials, if any;
- deployment identity identifiers; and
- custom-domain registrar details, if a custom domain is added later.

The repository may safely contain non-secret conventions such as region,
environment name, resource-name patterns, data classification, retention
targets, and role definitions.

## Ready-to-Start Gate

Cloud implementation can begin when:

1. the Azure account, tenant, and subscription are accessible with multi-factor
   authentication;
2. the owner has confirmed current credit/free-tier terms and selected a monthly
   ceiling;
3. Azure CLI and a local container runtime pass their version checks;
4. the intended region, initial SKUs, quota, names, and tags are known;
5. the Bicep plan can create the budget before workload resources;
6. the GitHub `azure-dev` environment and OIDC design are reviewed; and
7. no credential, personal financial artifact, or subscription-specific secret
   has entered the repository.
