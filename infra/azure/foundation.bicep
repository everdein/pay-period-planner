targetScope = 'resourceGroup'

param location string = resourceGroup().location

@description('Short globally unique lowercase alphanumeric suffix; choose before preview.')
@minLength(3)
@maxLength(10)
param suffix string

@allowed(['dev', 'portfolio'])
param environment string = 'dev'

@description('Short Azure region abbreviation used in resource names.')
@minLength(2)
@maxLength(5)
param regionCode string = 'eus2'

param owner string = 'everdein'
param postgresAdministrator string

@secure()
@minLength(16)
param postgresAdministratorPassword string

@description('Check current regional SKU availability and price before deployment.')
param postgresSku string = 'Standard_B1ms'

@allowed(['16', '17', '18'])
param postgresVersion string = '17'

@description('The budget must already have been deployed separately and verified.')
param budgetName string = 'planner-monthly'

var prefix = 'ppp-${environment}-${regionCode}-${suffix}'
var tags = {
  application: 'pay-period-planner'
  environment: environment
  owner: owner
  'data-classification': 'synthetic'
  'managed-by': 'bicep'
}

// The deployment script verifies this existing resource before spending.
resource budget 'Microsoft.Consumption/budgets@2024-08-01' existing = { name: budgetName }

resource network 'Microsoft.Network/virtualNetworks@2024-05-01' = {
  name: '${prefix}-vnet'
  location: location
  tags: tags
  properties: {
    addressSpace: { addressPrefixes: ['10.42.0.0/16'] }
    subnets: [
      {
        name: 'apps'
        properties: {
          addressPrefix: '10.42.0.0/23'
          delegations: [{ name: 'apps', properties: { serviceName: 'Microsoft.App/environments' } }]
        }
      }
      {
        name: 'postgres'
        properties: {
          addressPrefix: '10.42.2.0/27'
          delegations: [{ name: 'postgres', properties: { serviceName: 'Microsoft.DBforPostgreSQL/flexibleServers' } }]
        }
      }
    ]
  }
}

resource dns 'Microsoft.Network/privateDnsZones@2024-06-01' = {
  name: '${prefix}.postgres.database.azure.com'
  location: 'global'
  tags: tags
}
resource dnsLink 'Microsoft.Network/privateDnsZones/virtualNetworkLinks@2024-06-01' = {
  parent: dns
  name: '${prefix}-link'
  location: 'global'
  properties: { registrationEnabled: false, virtualNetwork: { id: network.id } }
}

resource postgres 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
  name: '${prefix}-pg'
  location: location
  tags: tags
  sku: { name: postgresSku, tier: 'Burstable' }
  properties: {
    administratorLogin: postgresAdministrator
    administratorLoginPassword: postgresAdministratorPassword
    version: postgresVersion
    createMode: 'Default'
    storage: { storageSizeGB: 32, autoGrow: 'Enabled' }
    backup: { backupRetentionDays: 7, geoRedundantBackup: 'Disabled' }
    highAvailability: { mode: 'Disabled' }
    authConfig: { activeDirectoryAuth: 'Disabled', passwordAuth: 'Enabled' }
    network: {
      delegatedSubnetResourceId: '${network.id}/subnets/postgres'
      privateDnsZoneArmResourceId: dns.id
      publicNetworkAccess: 'Disabled'
    }
  }
  dependsOn: [dnsLink]
}
resource database 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = {
  parent: postgres
  name: 'financial_app'
  properties: { charset: 'UTF8', collation: 'en_US.utf8' }
}

resource identity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: '${prefix}-app-id'
  location: location
  tags: tags
}
resource registry 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: 'ppp${environment}${regionCode}${suffix}'
  location: location
  tags: tags
  sku: { name: 'Basic' }
  properties: { adminUserEnabled: false }
}
resource registryPull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(registry.id, identity.id, 'AcrPull')
  scope: registry
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '7f951dda-4ed3-4680-a7ca-43fe172d538d')
    principalId: identity.properties.principalId
    principalType: 'ServicePrincipal'
  }
}
resource vault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: 'ppp${take(environment, 1)}${regionCode}${suffix}kv'
  location: location
  tags: tags
  properties: {
    tenantId: tenant().tenantId
    sku: { family: 'A', name: 'standard' }
    enableRbacAuthorization: true
    enableSoftDelete: true
    softDeleteRetentionInDays: 7
    enablePurgeProtection: true
    accessPolicies: []
  }
}
resource secretReader 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(vault.id, identity.id, 'KeyVaultSecretsUser')
  scope: vault
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '4633458b-17de-408a-b874-0445c86b69e6')
    principalId: identity.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

resource logs 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: '${prefix}-logs'
  location: location
  tags: tags
  properties: {
    sku: { name: 'PerGB2018' }
    retentionInDays: 30
    workspaceCapping: { dailyQuotaGb: 1 }
  }
}
resource insights 'Microsoft.Insights/components@2020-02-02' = {
  name: '${prefix}-insights'
  location: location
  tags: tags
  kind: 'web'
  properties: { Application_Type: 'web', WorkspaceResourceId: logs.id, IngestionMode: 'LogAnalytics' }
}
resource apps 'Microsoft.App/managedEnvironments@2025-01-01' = {
  name: '${prefix}-apps'
  location: location
  tags: tags
  properties: {
    workloadProfiles: [{ name: 'Consumption', workloadProfileType: 'Consumption' }]
    vnetConfiguration: { infrastructureSubnetId: '${network.id}/subnets/apps', internal: false }
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: { customerId: logs.properties.customerId, sharedKey: logs.listKeys().primarySharedKey }
    }
  }
}

output budgetResourceId string = budget.id
output environmentName string = apps.name
output identityName string = identity.name
output registryName string = registry.name
output vaultName string = vault.name
output postgresHost string = postgres.properties.fullyQualifiedDomainName
output databaseName string = database.name
output applicationInsightsName string = insights.name
