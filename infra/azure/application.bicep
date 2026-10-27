targetScope = 'resourceGroup'

param location string = resourceGroup().location
param applicationName string
param environmentName string
param identityName string
param registryName string

@description('Existing verified image in this registry, pinned as repository@sha256:digest.')
@minLength(73)
param image string

param postgresHost string
param databaseName string = 'financial_app'

@description('Precreated least-privilege runtime role; never use the PostgreSQL administrator.')
param databaseUsername string

@description('Existing Key Vault secret URIs. Supply URIs only, never plaintext values.')
param databasePasswordSecretUri string
param operatorPasswordSecretUri string
param operatorUsername string

@allowed(['disabled', 'owner-bootstrap'])
param registrationMode string = 'disabled'

@description('Existing Key Vault secret URI containing only the SHA-256 verifier, required in owner-bootstrap mode.')
param ownerBootstrapVerifierSecretUri string = ''

@description('Keep false until registration, proxy trust, migrations, recovery and exposure gates are verified.')
param allowPublicIngress bool = false

resource environment 'Microsoft.App/managedEnvironments@2025-01-01' existing = { name: environmentName }
resource identity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' existing = { name: identityName }
resource registry 'Microsoft.ContainerRegistry/registries@2023-07-01' existing = { name: registryName }

resource app 'Microsoft.App/containerApps@2025-01-01' = {
  name: applicationName
  location: location
  tags: { application: 'pay-period-planner', 'data-classification': 'synthetic', 'managed-by': 'bicep' }
  identity: { type: 'UserAssigned', userAssignedIdentities: { '${identity.id}': {} } }
  properties: {
    environmentId: environment.id
    workloadProfileName: 'Consumption'
    configuration: {
      activeRevisionsMode: 'Single'
      // Bootstrap is reachable only from the private environment, even if public ingress was requested.
      ingress: { external: allowPublicIngress && registrationMode == 'disabled', targetPort: 8080, transport: 'auto', allowInsecure: false }
      registries: [{ server: registry.properties.loginServer, identity: identity.id }]
      secrets: concat([
        { name: 'database-password', keyVaultUrl: databasePasswordSecretUri, identity: identity.id }
        { name: 'operator-password', keyVaultUrl: operatorPasswordSecretUri, identity: identity.id }
      ], registrationMode == 'owner-bootstrap' ? [
        { name: 'owner-bootstrap-hash', keyVaultUrl: ownerBootstrapVerifierSecretUri, identity: identity.id }
      ] : [])
    }
    template: {
      containers: [{
        name: 'planner'
        image: '${registry.properties.loginServer}/${image}'
        resources: { cpu: json('0.5'), memory: '1Gi' }
        env: concat([
          { name: 'SPRING_PROFILES_ACTIVE', value: 'prod' }
          { name: 'DATABASE_URL', value: 'jdbc:postgresql://${postgresHost}:5432/${databaseName}?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory' }
          { name: 'DATABASE_USERNAME', value: databaseUsername }
          { name: 'DATABASE_PASSWORD', secretRef: 'database-password' }
          { name: 'FINANCIALS_API_USERNAME', value: operatorUsername }
          { name: 'FINANCIALS_API_PASSWORD', secretRef: 'operator-password' }
          { name: 'FINANCIALS_SESSION_COOKIE_SECURE', value: 'true' }
          { name: 'FINANCIALS_REGISTRATION_MODE', value: registrationMode }
          { name: 'SPRING_FLYWAY_ENABLED', value: 'false' }
          { name: 'MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE', value: 'health' }
          { name: 'SERVER_FORWARD_HEADERS_STRATEGY', value: 'none' }
          { name: 'SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE', value: '5' }
        ], registrationMode == 'owner-bootstrap' ? [
          { name: 'FINANCIALS_OWNER_BOOTSTRAP_TOKEN_HASH', secretRef: 'owner-bootstrap-hash' }
        ] : [])
        probes: [
          { type: 'Startup', httpGet: { path: '/actuator/health', port: 8080, scheme: 'HTTP' }, periodSeconds: 10, failureThreshold: 30 }
          { type: 'Readiness', httpGet: { path: '/actuator/health', port: 8080, scheme: 'HTTP' }, periodSeconds: 10, failureThreshold: 3 }
          { type: 'Liveness', tcpSocket: { port: 8080 }, periodSeconds: 30, failureThreshold: 3 }
        ]
      }]
      scale: { minReplicas: 0, maxReplicas: 1 }
    }
  }
}

output applicationUrl string = 'https://${app.properties.configuration.ingress.fqdn}'
