# AGENTS.md

This file provides guidance to coding agents when working with code in this repository.

## What this is

`jeap-deploymentlog-service` is a jEAP **library**, published to Maven Central, that traces microservice deployments
across stages and generates deployment documentation as Confluence pages. Downstream projects depend on
`jeap-deploymentlog-web` and add their own configuration/instance — they do not run this repo directly (a runnable
`DeploymentLogApplication` exists in the web module mainly as a default/test entry point). See `README.md`.

The Maven parent is `jeap-spring-boot-parent`, which
supplies most dependency versions and plugin config.

- **Spring Boot 4.x**: uses the `@AutoConfiguration` pattern (not legacy `spring.factories`)
- **Java 25**: required minimum version

## Commands

```bash
# Build everything (compile, test, install to local ~/.m2)
./mvnw install

# Build a module and its dependencies, skipping tests
./mvnw -pl jeap-deploymentlog-docgen -am install -Dmaven.test.skip=true

# Run all tests for one module (its deps must already be installed)
./mvnw -pl jeap-deploymentlog-docgen test

# Run a single test class / method
./mvnw -pl jeap-deploymentlog-docgen test -Dtest=ConfluenceAdapterImplTest
./mvnw -pl jeap-deploymentlog-docgen test -Dtest=ConfluenceAdapterImplTest#movePage

# Set the version across all modules (or use the /version-bump skill)
./setPomVersions.sh 3.20.0-SNAPSHOT
```

Tests run against **H2 in-memory in PostgreSQL mode** (`jdbc:h2:mem:...;MODE=PostgreSQL`) — no Docker/Testcontainers
needed. Flyway migrations run against H2 in tests, so schema changes must stay PostgreSQL/H2-compatible.

## Module architecture

Six Maven modules in a clean-architecture layering (depend downward):

- **`jeap-deploymentlog-domain`** — Domain model + repository **interfaces**. Note: domain classes are *also* the JPA
  `@Entity` classes (single model, no separate persistence DTOs). Aggregate roots: `Deployment` (central; `externalId`,
  `DeploymentState` STARTED/SUCCESS/FAILURE, `DeploymentSequence` DEPLOYED/UNDEPLOYED, links to `Environment`/
  `ComponentVersion`/`Changelog`), `System` (owns `Component`s), `Environment`. Page-tracking entities (
  `DeploymentPage`, `DeploymentListPage`, `SystemPage`, …) record generated-documentation state.
- **`jeap-deploymentlog-persistence`** (→ domain) — Repository **implementations**. Pattern: domain interface
  `XxxRepository` → impl `XxxRepositoryImpl` wrapping a Spring Data `JpaXxxRepository`. Flyway migrations live in
  `src/main/resources/db/migration/`. `PersistenceConfiguration` is an `@AutoConfiguration` and provides the ShedLock
  JDBC `LockProvider`.
- **`jeap-deploymentlog-jira`** — Standalone Jira REST client (`JiraWebClientImpl`) with retry; no domain/persistence
  dependency.
- **`jeap-deploymentlog-docgen`** (→ domain, persistence, jira) — Confluence documentation generation. See below.
- **`jeap-deploymentlog-web`** (→ persistence, docgen) — REST controllers, security, OpenAPI, the
  `@SpringBootApplication` entry point. Uses jEAP starters (`security`, `monitoring`, `swagger`, `jeap-spring-boot-tx`).
- **`jeap-deploymentlog-service-instance`** (→ web) — Packaging-only module (`pom.xml` with a single dependency, no
  source).

This service is **request-driven only** — it does *not* use jEAP messaging/Kafka/inbox/outbox/error-handling. Asynchrony
is plain Spring `@Async` + ShedLock + an in-process `DocgenLocks` mutex keyed by system name.

## Documentation generation flow (the core feature)

1. A deployment arrives via `DeploymentController` (`PUT /api/deployment/{id}`, role `deploymentlog-write`), persisted
   by `DeploymentService`.
2. `DocgenAsyncService` (`@Async`) is triggered to generate/refresh Confluence pages for the deployment, the system,
   undeployments, and deployment-list pages. Errors increment the `deploymentlog.docgen.deploymentpages.error` counter
   rather than failing the request.
3. `DocumentationGenerator` renders content with Thymeleaf (`TemplateRenderer`), publishes via `ConfluenceAdapter`, and
   links Jira issues via `JiraAdapter`.
4. `ConfluenceAdapter` (impl `ConfluenceAdapterImpl`, mock `ConfluenceAdapterMock`) wraps the
   `asciidoc-confluence-publisher-client` library. The interface carries a class-level
   `@Retryable(RequestFailedException, maxAttempts=4, exponential backoff)`. Confluence error handling is by HTTP status
   parsed out of the exception message string (e.g. `response: 404`, `response: 409`).
5. `SchedulingService` runs cron/ShedLock jobs: `generateMissingPages` (retry failures), `outdatedPageHousekeeping` (
   delete stale pages), and metrics (`deploymentlog.docgen.deploymentpages.lag`). The schedule is off by default (
   `jeap.deploymentlog.documentation-generator.scheduled.cron` defaults to `-`).

`JobsController` (`/api/jobs/docgen…`) exposes manual regeneration triggers (system, single deployment, all) for
dev/test.

## Confluence publisher dependency

`asciidoc-confluence-publisher-client` is pinned via the `asciidoc-confluence-publisher-client.version` property in the
root `pom.xml`. The client uses the Confluence **v1** REST API — use `ConfluenceRestV1Client` (not the V2 variant) to
stay on the same `/rest/api/content` endpoints the error-status string checks depend on.

## License compliance

The build runs `org.honton.chas:license-maven-plugin` (`license:compliance`) and generates `THIRD-PARTY-LICENSES.md` via
the codehaus `license-maven-plugin`. After changing dependencies, run a full `./mvnw verify` to confirm the compliance
check passes; if new transitive dependencies appear, regenerate and commit `THIRD-PARTY-LICENSES.md` (it has its own
commit convention in history).

## Versioning

- Semantic Versioning; all changes documented in [CHANGELOG.md](./CHANGELOG.md) (Keep a Changelog format).
- `setPomVersions.sh` updates the version across all module POMs.
- When working on a feature branch, increase the version to `x.y.z-SNAPSHOT` in the POMs.
- When bumping the version, also update the changelog, and updates version/date in `publiccode.yml`.
- When the version on a feature branch has not yet been bumped compared to master, ask the user if a major, minor or
  patch version bump should be performed, and update the version accordingly.
