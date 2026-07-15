# 0024 Preserve structured frontend API failures

## Status

Accepted - implemented 2026-07-15

## Context

The frontend API client previously flattened HTTP status, safe Problem Detail
text, and request identity into one `Error.message`. The financial Redux slice
then recovered status with an `HTTP (\d{3})` regular expression so it could
distinguish missing snapshots, validation failures, authorization failures, and
optimistic-write conflicts.

That made application behavior depend on presentation wording. A copy change,
localized message, proxy response, or ordinary JavaScript error containing
HTTP-like text could change control flow. Request correlation also survived
only because the request ID had been appended to the same display string.

## Decision

Carry API problem metadata as structured fields through the frontend.

- `ApiError` keeps safe `detail`, HTTP `status`, optional Problem Detail
  `title`, and backend-confirmed `requestId` as separate fields.
- The response status is authoritative. The API client does not infer status
  from `title`, `detail`, or another response string.
- `FinancialsFailure` copies those fields directly and derives its failure kind
  only from `ApiError.status`.
- Ordinary JavaScript errors remain generic failures even when their messages
  contain text such as `HTTP 404`.
- Financial loading, onboarding, save, conflict, and export surfaces format a
  request reference from the structured `requestId`; the identifier is not
  embedded in the safe detail.
- Account and session errors continue to show correlated references by
  formatting the same structured fields at their presentation boundary.

## Consequences

- Missing-snapshot onboarding and conflict recovery no longer depend on error
  copy or a regular expression.
- Safe problem detail can change independently of status-based behavior.
- Request identity remains available for support correlation without becoming
  part of application control flow.
- Generic network, browser, and programming errors intentionally do not gain an
  HTTP classification unless they cross the API boundary as `ApiError`.
- Tests cover structured propagation and prove HTTP-like presentation text is
  not parsed.
