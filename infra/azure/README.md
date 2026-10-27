# Azure infrastructure preparation

<!-- cspell:ignore everdein sslmode truststore -->

These templates compile locally. They have not been validated against an Azure
subscription or deployed. The canonical boundary is
[ADR 0030](../../docs/adr/0030-use-a-production-shaped-single-owner-azure-environment.md):
one supported owner and synthetic financial data only.

## Stages

1. Select the personal subscription, approved monthly amount, region, SKU and
   resource names. Create an empty resource group through the owner-approved
   account setup. Resource-group creation and all later writes require the
   selected subscription and cost decision.
2. Preview `budget.bicep`, then deploy the budget separately. Confirm recipients
   and the 50/80/100 percent thresholds in Azure before any workload provisioning.
   An alert does not stop spending.
3. Preview `foundation.bicep`. It defines the private PostgreSQL subnet/DNS,
   workload-profile Container Apps environment, registry, workload identity,
   Key Vault RBAC, seven-day database backups, and 30-day logs with a daily quota.
   The low-cost SKU is a proposal, not a region-verified price quotation.
4. Provision the foundation only after the preview and price are approved.
   Generate a unique database administrator credential outside source. Keep
   every deployment parameter file outside this repository. Do not pass secret
   values on command lines or enable debug logging.
5. Create distinct migration and runtime database roles from a controlled host
   on the private network, apply Flyway migrations with the migration role,
   grant only required runtime privileges, and put runtime/operator credentials
   in Key Vault. This procedure is not implemented by these templates.
6. Build and scan the verified application image, publish it, and use its digest
   in `application.bicep`. Never deploy `latest` or unverified branch images.
7. Preview the application with public ingress disabled. Complete the remaining
   release gates before selecting public ingress deliberately.

`scripts/preview-azure.ps1` issues a what-if preview only. For workload stages it
first reads the existing `planner-monthly` budget and checks the approved amount.
It does not create resources, select a subscription, or store cloud identifiers.
Example, with owner-maintained parameters outside the checkout:

```powershell
./scripts/preview-azure.ps1 -Stage foundation -ResourceGroup <selected-group> -ParameterFile <outside-repository-json> -ApprovedMonthlyBudget <approved-amount>
```

Compile without a cloud account using Bicep CLI 0.48.1:

```powershell
bicep build infra/azure/budget.bicep --outfile <temporary-output-json>
bicep build infra/azure/foundation.bicep --outfile <temporary-output-json>
bicep build infra/azure/application.bicep --outfile <temporary-output-json>
```

## Application contract and unfinished gates

The application template defaults to internal ingress and closed registration.
For the first account, select `owner-bootstrap` with a Key Vault secret URI
containing only the SHA-256 verifier. Bootstrap ingress is always private;
use a controlled one-off operator job in the same Container Apps environment
to create the owner. Internal app ingress does not accept requests from an
arbitrary host on the virtual network. Do not add an environment-level HTTP
route targeting the app during bootstrap. Switch to
`disabled`, remove the verifier secret, and verify signup is closed before
allowing public ingress. The raw bootstrap credential never enters the template.
It takes Key Vault secret URIs, uses a managed identity to pull images/read
secrets, enforces encrypted hostname-verified PostgreSQL, limits the database
pool to five, exposes only health, and uses at most one replica. It disables
automatic Flyway at runtime: migrations must already have succeeded. TCP
liveness avoids restarting a healthy JVM solely because the database is down;
HTTP readiness includes database health.

PostgreSQL uses `sslmode=verify-full` with pgJDBC's `DefaultJavaSSLFactory`,
which uses the JRE truststore instead of expecting a user-home `root.crt` file.
Verify the live Azure certificate chain against the exact deployed image before
release; do not weaken certificate or hostname verification to make it connect.
See [pgJDBC SSL configuration](https://jdbc.postgresql.org/documentation/ssl/).

The trusted HTTPS-proxy boundary, protected owner bootstrap, operator recovery,
request/abuse limits, sanitized telemetry verification, authenticated smoke
checks, release rollback, point-in-time restore, incident controls, and shutdown
rehearsal remain explicit release gates. Application Insights is provisioned
but no Java agent or exporter is installed by this template. Log retention and
a daily quota are cost controls, not proof that payloads have been sanitized.
The Key Vault endpoint is public with RBAC authentication; PostgreSQL has no
public endpoint. A later private Key Vault endpoint needs an explicit network
and cost decision. Purge protection keeps a deleted vault protected for seven
days; teardown must account for retained backup/storage and soft-deleted assets.

There is intentionally no automatic application deployment workflow yet.
`azure-preview.yml` compiles templates on relevant PRs, main changes, and manual dispatch, and optionally runs a
budget-only what-if using environment-scoped OIDC. It cannot provision workloads.
Enable `AZURE_PREVIEW_ENABLED=true` only after configuring an `azure-dev`
environment with required approval and a federated subject scoped to
`repo:everdein/pay-period-planner:environment:azure-dev`. What-if requires
deployment-equivalent permissions, including deployment and budget write actions;
Reader alone is insufficient. Limit these permissions to the selected resource
group and required resource types; do not grant subscription-wide Contributor.
Confirm the exact custom role with the owner before enabling cloud preview.
Identifiers live in GitHub
environment secrets, never in tracked parameter files. Budget amount, start
date and resource-group name live in environment variables; the notification
email is an environment secret. Cloud preview failure remains a failure,
not a successful deployment.

## Schema references

- [Container Apps](https://learn.microsoft.com/azure/templates/microsoft.app/2025-01-01/containerapps)
- [Managed environments](https://learn.microsoft.com/azure/templates/microsoft.app/2025-01-01/managedenvironments)
- [PostgreSQL](https://learn.microsoft.com/azure/templates/microsoft.dbforpostgresql/2024-08-01/flexibleservers)
- [Budgets](https://learn.microsoft.com/azure/templates/microsoft.consumption/2024-08-01/budgets)
- [Key Vault references](https://learn.microsoft.com/azure/container-apps/manage-secrets)
