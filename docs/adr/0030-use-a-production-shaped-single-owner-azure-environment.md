# 0030 Use a production-shaped single-owner Azure environment

## Status

Accepted

## Context

Pay Period Planner is ready to move beyond a local-only runtime as an Azure
learning and portfolio project. The owner wants to practice production-grade
deployment, identity, authorization, recovery, monitoring, and operations
without claiming the product is ready for public financial data or supported
multi-user use.

The application already provides PostgreSQL-backed accounts, adaptive password
hashing, opaque server-managed sessions, `HttpOnly` cookies, CSRF protection,
and database-derived workspace membership. Replacing that foundation with an
external identity provider is not required to learn the first Azure deployment
boundary. Unrestricted signup would nevertheless allow an unknown visitor to
become a user, consume resources, and store unknown data.

Synthetic data lowers the confidentiality impact of an application defect. It
does not eliminate risks such as unauthorized access, account takeover,
resource abuse, data corruption, denial of service, secret exposure, or
unexpected cloud cost.

## Decision

Deploy a production-shaped, single-owner Azure environment with these limits:

- The owner is the only supported application user and the operational owner.
- The application stores synthetic financial data only. Do not migrate the
  owner's local financial snapshot, backups, exports, logs, or screenshots.
- Keep the existing application-managed account and workspace authorization
  model for the first Azure release. Revisit an external identity provider only
  when additional supported users, federation, or managed multi-factor
  authentication justify it.
- Add an explicit registration mode with `disabled` as the production default.
  A temporary `owner-bootstrap` mode permits creation of the first account only
  when the database has no application users and the request presents a
  high-entropy bootstrap credential held outside source control.
- Make the empty-user check and first-user creation one transactional operation
  so concurrent requests cannot both claim ownership. Do not use an unprotected
  "first request wins" flow.
- After successful owner creation, reject every further signup. Remove or
  rotate the bootstrap credential and redeploy with registration disabled.
- Keep ordinary sign-in, sign-out, session recovery, session revocation, CSRF,
  workspace membership, and secure-cookie behavior active.
- Use operator-assisted account recovery for the single-owner phase. Do not add
  email delivery or security questions solely for this deployment. A recovery
  operation must revoke existing sessions, create no second owner, avoid
  printing secrets, and be exercised with synthetic state.
- Treat password throttling, edge request limits, cost alerts, secret rotation,
  backups, restore drills, sanitized telemetry, and incident response as release
  gates even though the stored financial data is synthetic.

The Azure runtime remains one public origin backed by private managed
PostgreSQL. Infrastructure must be reproducible from source, but secrets and
subscription-specific identifiers remain outside version control.

## Consequences

- The deployment provides real account, session, authorization, migration, and
  operational experience without accepting real financial-data custody.
- The signup contract and frontend account flow need a closed-registration and
  owner-bootstrap state before the public endpoint is considered ready.
- The bootstrap credential is a temporary privileged secret and needs Key Vault
  storage, rotation, audit-safe handling, and explicit removal after use.
- A public endpoint still needs abuse and cost controls. Synthetic data is not a
  reason to skip security work.
- Password reset, email verification, managed multi-factor authentication,
  collaboration, and general public registration remain outside the first
  release. Supporting another real user requires a new product and privacy
  decision.
- The owner may deliberately stop or delete the environment when it is not
  being demonstrated, provided the documented recovery boundary has been
  proved first.
