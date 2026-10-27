targetScope = 'resourceGroup'

@minValue(1)
@description('Owner-approved monthly amount in the subscription billing currency; alerts do not cap spending.')
param monthlyBudget int

@minLength(1)
param contactEmails array

@description('First day of the approved billing month, for example YYYY-MM-01T00:00:00Z.')
param startDate string

resource budget 'Microsoft.Consumption/budgets@2024-08-01' = {
  name: 'planner-monthly'
  properties: {
    amount: monthlyBudget
    category: 'Cost'
    timeGrain: 'Monthly'
    timePeriod: { startDate: startDate }
    notifications: {
      half: {
        enabled: true
        operator: 'GreaterThanOrEqualTo'
        threshold: 50
        thresholdType: 'Actual'
        contactEmails: contactEmails
      }
      warning: {
        enabled: true
        operator: 'GreaterThanOrEqualTo'
        threshold: 80
        thresholdType: 'Actual'
        contactEmails: contactEmails
      }
      ceiling: {
        enabled: true
        operator: 'GreaterThanOrEqualTo'
        threshold: 100
        thresholdType: 'Actual'
        contactEmails: contactEmails
      }
    }
  }
}
