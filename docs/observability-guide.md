# Observability Guide

## Scope

The repository includes a vendor-neutral local observability foundation:

- backend request IDs and completion logs
- JSON structured backend logs under the `prod` profile
- Spring Boot request and JVM metrics
- snapshot save and import outcome counters
- frontend API correlation IDs
- an accessible frontend render-error recovery screen
- sanitized browser error reporting to the local console

No telemetry leaves the application. Centralized storage, dashboards, alerts,
traces, retention, and incident-management integration remain deployment work.

## Request Correlation

The frontend creates a UUID for each API call and sends it in `X-Request-ID`.
The backend accepts only IDs containing 1–100 letters, numbers, periods,
underscores, colons, or hyphens. Missing or unsafe IDs are replaced with a new
UUID. Every backend response includes the final ID in `X-Request-ID`, and
handled Problem Detail responses also include a `requestId` property.

When an API call fails, the frontend includes the request ID in the displayed
error. Use that value to find the matching backend completion log. Do not use a
financial value, user name, email address, or credential as a request ID.

## Backend Logs

Every HTTP request produces one completion event with only:

- request ID
- HTTP method
- route pattern, with record IDs normalized by Spring route matching
- response status
- elapsed milliseconds

The log intentionally excludes query strings, request and response bodies,
authorization headers, financial values, stack traces, and snapshot data.
Local profiles keep human-readable console logs. The `prod` profile uses
Logstash-compatible JSON console logs through
`logging.structured.format.console=logstash`; MDC and structured fields are
included in that JSON output.

## Metrics

`/actuator/health` and `/actuator/info` remain public and contain no financial
details. `/actuator/metrics` and its child endpoints require the same
`FINANCIALS_API_USERNAME` and `FINANCIALS_API_PASSWORD` operator credentials as
migration administration. Financial workspace routes use account sessions.
Other Actuator endpoints are denied.

Useful metric names include:

| Metric                        | Purpose                                                           |
| ----------------------------- | ----------------------------------------------------------------- |
| `http.server.requests`        | Request count, status, route, and latency                         |
| `financials.snapshot.saves`   | Full-snapshot outcomes tagged `success`, `conflict`, or `failure` |
| `financials.snapshot.imports` | CSV/XLSX import outcomes tagged by format and result              |
| `jvm.memory.used`             | JVM memory use                                                    |
| `process.uptime`              | Backend process uptime                                            |

Custom counters appear only after the corresponding operation has occurred.
Metrics contain low-cardinality operational labels only; they never include
record IDs, request IDs, users, account names, or financial values.

To inspect health:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

To inspect protected metrics without printing the password, build an ephemeral
Basic-auth header from a PowerShell credential:

```powershell
$credential = Get-Credential -UserName financial_app
$bytes = [Text.Encoding]::UTF8.GetBytes(
  '{0}:{1}' -f $credential.UserName, $credential.GetNetworkCredential().Password
)
$basicToken = [Convert]::ToBase64String($bytes)

try {
  Invoke-RestMethod `
    -Headers @{ Authorization = "Basic $basicToken" } `
    -Uri http://localhost:8080/actuator/metrics
}
finally {
  $bytes = $null
  $basicToken = $null
  $credential = $null
}
```

Append a metric name, such as
`/actuator/metrics/http.server.requests`, to inspect its available measurements
and tags. Do not paste metric output into issues or external tools before
confirming that it contains operational metadata only.

## Frontend Error Containment

The application boundary catches unexpected React render and lifecycle errors,
shows an accessible recovery screen, creates a `web-...` reference ID, and
offers a reload action. Global browser errors and unhandled promise rejections
use the same local reporter.

The reporter writes only the error category, JavaScript error type, and
reference ID to the browser console. It deliberately omits the error message,
stack, Redux state, request payload, current snapshot, URL, and credentials.
Connecting a hosted browser error provider must preserve this allowlist and
requires an approved privacy and retention policy.

## Triage Sequence

1. Capture the UTC time, HTTP status, and request or frontend reference ID.
2. Find the backend completion event with the same request ID.
3. Inspect `http.server.requests` and the relevant snapshot counter for a
   broader pattern.
4. Reproduce with synthetic data and record the smallest safe error summary.
5. Escalate with code version, profile, status, route pattern, and checks run.

Never add personal financial data, request bodies, authorization values,
database credentials, full snapshots, exports, or raw browser state to an
incident bundle.
