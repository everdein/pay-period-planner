# Deployment Provider Assessment

Status: Provider decision deferred
Research date: July 15, 2026

## Decision Status

Cloud provider selection is paused while the remaining application roadmap is
reviewed. Render is retained below as the initial low-complexity candidate, but
it is not approved. AWS must be evaluated as a portfolio-oriented alternative
before the owner selects a provider, architecture, or budget.

The first hosted environment remains a synthetic, access-controlled portfolio
demo. It must not contain the owner's personal financial snapshot or invite
visitors to enter real financial or identity data.

## Current Application Constraints

The application already has several boundaries that should shape deployment:

- The React client uses relative `/api/v1` URLs, session cookies, and CSRF
  protection. Serving the UI and API from one origin avoids a second production
  API base URL, cross-origin cookies, and a broader CORS policy.
- The backend is a stateful Spring Boot API only with respect to PostgreSQL. Its
  filesystem can remain ephemeral.
- PostgreSQL and Flyway are required at startup. There is no JSON or JSONB
  runtime fallback.
- `/actuator/health` is the existing deployment health boundary.
- CI intentionally contains no deployment job until a provider is selected.
  Provider selection alone does not establish a release pipeline.

These constraints favor one containerized web service that serves the compiled
frontend and Spring Boot API, plus one private managed PostgreSQL database in
the same region.

## Provider Comparison

| Option                                                    | Strengths for this project                                                                                                                                                                  | Material tradeoffs                                                                                                                                                   | Assessment                |
| --------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------- |
| Render paid web service and paid Render Postgres          | Predictable entry price, private same-region database networking, managed TLS, Docker support, Blueprint infrastructure as code, health checks, deploy rollback, and paid-database recovery | Entry database has limited memory; Hobby recovery is three days; application and database still require separate shutdown checks                                     | Recommended               |
| Railway Hobby application and PostgreSQL                  | Low `$5/month` minimum with the first `$5` of usage included, Docker deployment, health checks, and a documented PostgreSQL recovery pattern                                                | The final bill varies with CPU, memory, storage, and egress; PostgreSQL point-in-time recovery requires more operator-owned setup than the Render paid-database path | Viable budget alternative |
| Separate static frontend, API host, and database provider | Each component can be optimized or placed on a free tier independently                                                                                                                      | Adds cross-origin configuration, cookie and CSRF risk, multiple provider accounts, and more backup and shutdown boundaries                                           | Defer                     |
| General-purpose cloud or self-managed PostgreSQL          | Maximum infrastructure control and a stronger platform-engineering exercise                                                                                                                 | Substantially more networking, IAM, patching, observability, cost-control, and database operations than this portfolio story needs                                   | Defer                     |

Render's current Hobby workspace costs `$0/month` plus compute. A Starter web
service is `$7/month`, and a Basic-256mb Postgres instance is `$6/month`, for a
`$13/month` baseline before database storage and excess bandwidth. Hobby
includes 5 GB of bandwidth; paid Postgres storage is `$0.30/GB-month`.
[Render pricing](https://render.com/pricing)

The free Render database is not acceptable for this demo. It expires after 30
days and has no managed recovery features. A free web service also sleeps after
15 idle minutes and can take about a minute to wake, which weakens an interview
demonstration. Paid web services remain running, and paid databases receive
continuous backups. [Render service behavior](https://render.com/docs/faq)
[Render Postgres recovery](https://render.com/docs/postgresql-backups)

Railway remains the fallback when minimizing the monthly floor is more
important than cost predictability. Its Hobby plan is `$5/month`, includes the
first `$5` of resource usage, and bills additional CPU, memory, storage, and
egress by consumption. [Railway pricing](https://docs.railway.com/pricing/plans)

## Candidate Render Boundary

Use this shape only if Render is selected after the deferred comparison:

1. Create a Render Hobby workspace and one project with a single production
   environment.
2. Deploy one Starter Docker web service in `virginia`. A multi-stage image
   builds the Vite frontend, packages it with the Spring Boot application, and
   exposes one public origin.
3. Create one Basic-256mb Render Postgres database in `virginia`. Use its
   internal connection details and disable external database access after
   setup.
4. Define both resources in a root `render.yaml`. Keep secrets out of the file
   and configure auto-deploy only after the existing hosted checks pass.
5. Use Render-managed TLS and `/actuator/health` for the first release. A custom
   portfolio domain is optional and should not block proving the deployment.

Render services and datastores in the same region can communicate over a
private network. A service or database cannot later change regions in place,
so `virginia` is an intentional US East choice for this portfolio.
[Render regions](https://render.com/docs/regions)
[Render Postgres connections](https://render.com/docs/postgresql-creating-connecting)

Render Blueprints support Docker services, databases, health checks,
pre-deploy commands, database-derived environment variables, and blocked
external database access. [Render Blueprint reference](https://render.com/docs/blueprint-spec)

## Release Gates

Provider approval is only the first Phase M decision. The first hosted release
must also prove all of the following:

- **Synthetic access:** disable unrestricted public signup or replace it with a
  dedicated demo identity flow. Do not ask a reviewer for a real email address
  or real financial values.
- **Secret handling:** generate unique production operator, session, and
  database secrets in Render. Never copy local credentials into the hosted
  environment.
- **Database privilege separation:** use a Flyway migration role for DDL and a
  narrower runtime role for application queries and writes. The runtime role
  must not own the schema.
- **Migration behavior:** keep Flyway as the only schema authority, preserve
  additive and backward-compatible migrations, and prove startup against an
  empty database and the previous released schema.
- **Request safety:** enable secure session cookies, keep the application
  request-size limit, and add a matching provider edge limit if available.
- **Recovery:** prove both a logical export and a point-in-time restore to a new
  database before calling backups complete. On Hobby, the managed point-in-time
  recovery window is three days and logical exports are retained for seven
  days.
- **Rollback:** verify application rollback against the migrated schema. A
  provider image rollback is not sufficient when a release changes data.
- **Observability:** export only redacted operational logs, metrics, and browser
  failures. Never export financial payloads, credentials, session tokens, or
  database contents.
- **Demo reset:** provide a repeatable synthetic-account reset that cannot
  expose one reviewer's edits to another reviewer.
- **Cost and shutdown:** set a billing alert, record both resource names, and
  maintain a shutdown runbook that exports synthetic evidence as needed and
  then suspends or deletes the web service and database separately.

Paid Render Postgres provides continuous backup and restores into a separate
database for validation before switching application traffic. This is a useful
recovery boundary, but it does not replace an application-owned restore drill.
[Render Postgres recovery](https://render.com/docs/postgresql-backups)

## Privacy and Retention Boundary

Render publishes a privacy policy, Data Processing Addendum, and security
controls, but its general personal-data retention language is purpose-based
rather than a fixed immediate-deletion promise. That is acceptable for a
synthetic portfolio environment and is not approval to host personal financial
data. [Render privacy policy](https://render.com/privacy)
[Render Data Processing Addendum](https://render.com/dpa)

Before allowing real users or real financial data, the product would need a
separate privacy and compliance decision covering notices, account deletion,
data export and erasure, backup retention, incident response, and applicable
financial-data obligations. That is outside this portfolio deployment.

## Deferred Owner Decision

The later decision must compare at least these two goals:

- Render for the smallest operational surface and predictable entry cost.
- AWS for a broader cloud architecture and infrastructure story, with explicit
  cost controls and only the services justified by this application.

Until that comparison is complete and the owner approves a boundary, do not
create provider resources, add billing, implement provider-specific
configuration, or replace the placeholder deploy job.
