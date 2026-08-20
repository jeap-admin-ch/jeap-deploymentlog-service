# Configuration

All properties of the Deployment Log Service are namespaced under `jeap.deploymentlog.*`. Baseline defaults
are defined in `deploymentlogDefaultProperties.properties` of the service library. See
[Getting Started](getting-started.md#5-configure-the-application) for a minimal configuration of an
instance.

Two of the property groups are bound with `ignoreUnknownFields = false`
(`…documentation-generator.confluence` and `…jira`), so a typo in one of their keys fails the startup
instead of being silently ignored.

## Documentation root URL

| Property                                  | Default | Description                                                                                                                              |
|-------------------------------------------|---------|--------------------------------------------------------------------------------------------------------------------------------------------|
| `jeap.deploymentlog.documentation.root-url` | —     | Prefix a generated Confluence page id is appended to. Used for the `GET /api/deployment-doc/…` redirect and for the remote links written into Jira issues. Required — the application does not start without it. |

Since the page id is appended verbatim, the value has to end where the id belongs, for example
`https://confluence.example.com/pages/viewpage.action?pageId=`.

## API users

The `/api/**` endpoints are secured with HTTP basic authentication against two in-memory users:

| Property                                   | Default          | Description                                              |
|--------------------------------------------|------------------|----------------------------------------------------------|
| `jeap.deploymentlog.read-user.username`    | `read`           | Username of the user with the role `deploymentlog-read`.  |
| `jeap.deploymentlog.read-user.password`    | `{noop}secret`   | Password of the read user, in Spring Security password-encoder format. |
| `jeap.deploymentlog.write-user.username`   | `write`          | Username of the user with the role `deploymentlog-write`. |
| `jeap.deploymentlog.write-user.password`   | `{noop}secret`   | Password of the write user, in Spring Security password-encoder format. |

The defaults exist so that the library's own tests run out of the box and **must** be overridden in every
real deployment. Use an encoder prefix such as `{bcrypt}` for the passwords; `{noop}` stores them in clear
text and is only acceptable locally. See [REST API](rest-api.md#security) for which endpoint requires which
role.

## Confluence

Prefix `jeap.deploymentlog.documentation-generator.confluence`. Unknown keys under this prefix fail the
startup.

| Property                              | Default | Description                                                                                                    |
|---------------------------------------|---------|------------------------------------------------------------------------------------------------------------------|
| `url`                                 | —       | Base URL of the Confluence instance.                                                                            |
| `space-key`                           | —       | Key of the Confluence space the documentation is generated into.                                                |
| `root-page-id`                        | —       | Id of the existing page below which the whole page tree is generated. Has to be created manually once.          |
| `username`                            | —       | Technical user with write permission on the page tree.                                                          |
| `password`                            | —       | Password of the technical user. Excluded from the configuration log output.                                     |
| `deployment-history-max-show`         | `50`    | Number of deployments listed on a deployment history page and on a deployment history overview page.            |
| `deployment-history-overview-max-time`| `P7D`   | Only deployments started within this duration appear on the deployment history overview pages.                  |
| `retry-on-conflict-wait-duration`     | `PT10S` | How long to wait before re-reading the page and retrying an update that Confluence rejected as a conflict (HTTP 409). |
| `mock-confluence-client`              | `false` | Replaces the Confluence client with a mock that generates no pages. For local development and tests only.       |

## Jira

Prefix `jeap.deploymentlog.jira`. Unknown keys under this prefix fail the startup.

| Property          | Default | Description                                                                                                                |
|-------------------|---------|--------------------------------------------------------------------------------------------------------------------------------|
| `url`             | —       | Base URL of the Jira instance. The client calls its `rest/api/2` endpoints.                                                    |
| `app-id`          | —       | Application id of the linked Confluence instance in Jira. Part of the `globalId` of the remote link, so that re-generating a page updates the existing link instead of adding a duplicate. |
| `username`        | —       | Technical user. Needs browse permission on the referenced projects and permission to add remote links.                        |
| `password`        | —       | Password of the technical user. Excluded from the configuration log output.                                                   |
| `mock-jira-client`| `false` | Replaces the Jira client with a mock. For local development and tests only.                                                   |
| `retry-delay-ms`  | `2000`  | Delay in milliseconds before the first retry of a failed Jira request; doubled for every further retry.                        |

## Documentation generator

Prefix `jeap.deploymentlog.documentation-generator.config`.

| Property                       | Default | Description                                                                                                          |
|--------------------------------|---------|--------------------------------------------------------------------------------------------------------------------------|
| `remedy-change-link-root-url`  | —       | Prefix the `remedyChangeId` of a deployment is appended to, turning it into a link on the generated page. A missing trailing slash is added. If unset, the id is rendered without a link. |

## Scheduled jobs

Prefix `jeap.deploymentlog.documentation-generator.scheduled`, plus the housekeeping cron expression. See
[Operations](operations.md#scheduled-jobs) for what the jobs do.

| Property                                                                | Default          | Description                                                                                                     |
|-------------------------------------------------------------------------|------------------|-------------------------------------------------------------------------------------------------------------------|
| `jeap.deploymentlog.documentation-generator.scheduled.cron`             | `0 0/10 * * * *` | Cron expression of the repair job generating missing and outdated pages. Set to `-` to disable the job.           |
| `jeap.deploymentlog.documentation-generator.scheduled.retried-pages-limit` | `50`          | Maximum number of deployment pages that one repair run picks up.                                                |
| `jeap.deploymentlog.documentation-generator.scheduled.min-age-minutes`  | `5`              | Minimum age of a deployment before the repair job regenerates its page — younger ones are assumed to be still in progress. |
| `jeap.deploymentlog.documentation-generator.scheduled.max-age-minutes`  | `1440`           | Maximum age of a deployment considered by the repair job. Must be greater than `min-age-minutes`, otherwise the startup fails. |
| `jeap.deploymentlog.documentation-generator.scheduled.keep-deployment-page-per-env-count` | `200` | How many deployment pages per system and non-productive environment the housekeeping keeps regardless of age. |
| `jeap.deploymentlog.documentation-generator.housekeeping.cron`          | `0 30 3 * * *`   | Cron expression of the housekeeping job deleting outdated pages. Set to `-` to disable the job.                   |

## Database

The service requires PostgreSQL. Only the datasource has to be configured — the JPA, Hikari and Flyway
settings come with the library:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db-host:5432/deploymentlog
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

Defaults set by `deploymentlogDefaultProperties.properties` that an instance should not override:
`spring.jpa.hibernate.ddl-auto=none`, `spring.jpa.generate-ddl=false`, `spring.jpa.open-in-view=false`,
the PostgreSQL dialect and driver, `spring.flyway.enabled=true`, and a Hikari pool of 10 connections named
after `spring.application.name`.

Flyway migrates the schema at startup from the migrations bundled in `jeap-deploymentlog-persistence`
(`db/migration`), picked up through the default `spring.flyway.locations`.

## Related

- [Getting Started](getting-started.md)
- [REST API](rest-api.md)
- [Operations](operations.md)
