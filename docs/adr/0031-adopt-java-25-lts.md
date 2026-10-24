# 0031 Adopt Java 25 LTS

## Status

Accepted. Supersedes only the Java 21 runtime and bytecode baseline in ADR 0006.
Its validation, money, and coverage decisions remain in force.

## Context

ADR 0006 retained Java 21 while coverage tooling did not support newer class
files. The pinned JaCoCo 0.8.15 release supports Java 25, and Spring Boot 4.1.2
supports that LTS runtime. The previous coverage limitation no longer applies.

## Decision

Compile and run on Java 25 LTS. Use the current Java 25 patch selected by
setup-java in CI, including PostgreSQL, browser, security, and CodeQL jobs.
Retain the complete verification and coverage gates.

Keep Maven dependency platforms coherent. The selected Jackson 3.2.4 BOM is
imported directly so its required annotations 2.22 override the older annotation
version managed by the Spring Boot parent. This avoids a runtime class-loading
failure while preserving the application's JSON contracts.

## Consequences

- Local development requires JDK 25 or newer; supported CI uses Java 25 LTS.
- Java 21 can no longer run the generated application bytecode.
- Existing tests, coverage, and isolated PostgreSQL verification remain required.
- No database migration, persistence change, or API contract change is involved.
- Historical ADR 0006 is retained as the original decision record.
