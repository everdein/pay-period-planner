# Single-origin container

Milestone A packages the compiled frontend and Spring Boot API in one image.
It is a synthetic verification artifact, not an Azure deployment or approval
to expose signup publicly. Restricted registration is implemented; ADR 0030's
cloud budget, identity and operational gates remain separate work.

## Build and verify

From the repository root with Docker and Python 3 available:

```sh
python3 scripts/verify-container.py --evidence /tmp/planner-container-evidence.json
```

The script builds from digest-pinned Node 24 and Java 25 images, starts a
disposable PostgreSQL 18 container on a unique bridge network, then binds the
application to a random loopback port. It never reads the host database or
personal financial files. The database has no published host port. Random fixture credentials stay in process/container
environment variables and are not printed or retained in the evidence packet.
The script removes only its uniquely named containers, volumes, network and
image tag on exit. CI runs this on PRs and main, retains the exact source SHA
and local image ID, and includes its result in the required Scans gate.

The runtime contains the executable JAR and JRE, without Node, Maven, source,
development dependencies or local data. Build context exclusions omit local
configuration, dependency directories, generated output and private data.
The dependency lockfiles, Maven wrapper and image digests constrain build
inputs; this is not a claim of bit-identical JAR bytes across build times.
Update pinned image digests deliberately alongside compatibility verification.

## Runtime contract

- Port 8080 serves `/`, compiled `/assets/`, and relative `/api/v1` requests.
- `/workspace` and its descendants forward to the packaged frontend entry.
  API, Actuator and missing asset paths are never caught by this fallback.
  The current application has no separate client-side router.
- UID/GID 10001 owns the application. The smoke run drops all capabilities,
  enables no-new-privileges, and uses a read-only root with writable `/tmp`.
  Other runners must apply equivalent runtime restrictions explicitly.
- The image selects `prod` and secure cookies by default. Supply
  `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`,
  `FINANCIALS_API_USERNAME` and `FINANCIALS_API_PASSWORD` externally.
  Existing production guards reject default operator credentials or insecure
  cookie configuration. No credentials are embedded in image layers.
- Production registration defaults to disabled. The smoke fixture explicitly
  enables owner bootstrap with a random token verifier and proves that missing
  proof and a second signup are rejected. Operational bootstrap must use a
  separately generated secret and return to disabled mode afterward; see the
  [API contract](api-contract.md).
- `/actuator/health` is the health probe; database availability participates
  in readiness. No cloud health configuration is provisioned here.
- Forwarded headers are ignored by default. A future trusted proxy must strip
  untrusted headers and establish an explicit trust boundary before enabling
  forwarding support. The container does not supply HTTPS itself.

The smoke gate checks static assets/fallback, health, non-root/read-only
execution, authentication, workspace access, CSRF/CORS, cookie attributes,
untrusted forwarding headers and the existing request size limit. Its
loopback HTTP fixture explicitly returns Secure cookies to test server
contracts; this is not a browser/TLS or production exposure demonstration.
Existing browser and PostgreSQL integration jobs retain their own gates.
