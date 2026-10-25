# 0031 Bound request bodies and authentication work

## Status

Accepted

## Context

Declared content length does not bound chunked JSON uploads. Anonymous signup
and sign-in also need shared admission limits before adaptive password hashing.
The application currently uses synchronous MVC and a single local process.

## Decision

Read request bodies through a bounded buffer before controller handling and
replay accepted bytes through both Servlet reader interfaces. Reject declared
or observed overflow with the existing plain-text `413` response. Preserve
exact-limit bodies, reader charset, and stream/reader exclusivity.

Apply shared signup/sign-in admission at their HTTP controller boundary before
calling account services. Fixed one-minute budgets allow 10 attempts per
normalized email, 30 per socket peer, and 100 globally. Permit four concurrent
operations without queuing. Count successful and failed attempts alike; release
concurrency permits in all outcomes. Keep expiring identity state bounded and
reject new identities at capacity rather than evicting live limits. Return
safe `429` Problem Detail with `Retry-After: 60`.

Apply the same admission before HTTP Basic verification in the security chain,
so an Authorization header cannot move password work ahead of controller
limits. Keep operational and workspace authorities distinct.

## Consequences

Accepted request bodies consume bounded buffering memory before JSON parsing.
Asynchronous Servlet reads are not supported by this synchronous boundary.
Transport upload timeouts and edge limits still belong to the deployment.

Authentication limits are per process, reset on restart, and do not trust raw
forwarding headers. Proxied clients may share a socket-peer budget. Multiple
replicas need shared edge enforcement. No schema, session, workspace, password,
or financial snapshot contract changes are required.
