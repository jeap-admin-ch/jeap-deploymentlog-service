# REST API

All endpoints of the Deployment Log Service live under `/api`. This page describes them as they are
implemented by the controllers of `jeap-deploymentlog-web`; the authoritative, always-current specification
is the OpenAPI document the service generates from the same controllers.

## Security

The `/api/**` paths (and `/error`) are protected by a stateless HTTP basic filter chain with CSRF disabled.
Authentication is against two in-memory users configured by property, see
[Configuration](configuration.md#api-users):

| Role                    | Granted to                                | Allows                                                                               |
|-------------------------|-------------------------------------------|--------------------------------------------------------------------------------------|
| `deploymentlog-read`    | `jeap.deploymentlog.read-user.*`          | The read endpoints marked *read* below.                                              |
| `deploymentlog-write`   | `jeap.deploymentlog.write-user.*`         | Everything: all read endpoints plus recording, undeploying, jobs and blog posts.     |

`GET /api/deployment-doc/**` is the one exception — it is reachable without authentication so that the
generated link can be shared freely. Every other `/api` request without valid credentials is rejected with
`401`.

## OpenAPI and Swagger

The service declares an OpenAPI definition titled *Deployment Log Service* with a `basicAuth` security
scheme, and a grouped API named `DeploymentLog-Service-API` covering `/api/**`. The rendering comes from
`jeap-spring-boot-swagger-starter`, which serves the Swagger UI at `/swagger-ui.html` and the OpenAPI
document at `/api-docs`. Both are denied by default: set `jeap.swagger.status` (`OPEN`, `SECURED`,
`CUSTOM`) in the instance configuration to make them reachable.

## Common status codes

Beyond the codes listed per endpoint, `RestResponseExceptionHandler` maps domain errors to HTTP status codes.
Existing endpoints generally return the exception message as plain text. System-group errors use
`application/problem+json` with a stable `errorCode` and a user-facing `detail`; internal exception messages are not
exposed.

| Status | Raised when                                                                                                   |
|--------|-----------------------------------------------------------------------------------------------------------------|
| `400`  | The request is invalid, the deployment state cannot be applied, or a system/alias name is already taken.         |
| `401`  | Missing or wrong credentials.                                                                                   |
| `403`  | Authenticated, but the role required by the endpoint is missing.                                                |
| `404`  | The referenced deployment, deployment page, system, system group, component or environment does not exist.       |
| `409`  | A system group already uses the requested name (case-insensitive).                                               |
| `503`  | Jira is unavailable or rejects the service's technical user during a ready-for-deploy check.                     |
| upstream status | An error of a synchronously called upstream system (for example Confluence when creating a blog post) is passed through with its original status. |

## Deployments

### `PUT /api/deployment/{id}` — record a deployment

Role `deploymentlog-write`. `{id}` is the client-chosen external id of the deployment; it has to be unique
per deployment and is the id used by all other deployment endpoints.

Request body (`DeploymentCreateDto`):

| Field                  | Type                          | Required | Description                                                                 |
|------------------------|-------------------------------|----------|-------------------------------------------------------------------------------|
| `startedAt`            | timestamp with offset         | yes      | When the deployment started.                                                 |
| `startedBy`            | string                        | yes      | Who or what started the deployment.                                          |
| `environmentName`      | string                        | yes      | Environment name; upper-cased by the service, created if unknown.            |
| `componentVersion`     | object, see below             | yes      | The component and the version being deployed.                                |
| `deploymentUnit`       | object `type`, `coordinates`, `artifactRepositoryUrl` | yes | What is deployed. `type` is one of `DOCKER_IMAGE`, `MAVEN_JAR`, `NPM_PACKAGE`, `SOURCE_BUILD`, `GIT_OPS_COMMIT`. |
| `target`               | object `type`, `url`, `details` | no     | Where it is deployed to, e.g. the cluster or account.                        |
| `links`                | array of `label`, `url`       | no       | Additional links shown on the generated page.                                |
| `changelog`            | object, see below             | no       | What changed compared to the previous version.                               |
| `remedyChangeId`       | string                        | no       | Change id, rendered as a link if a Remedy root URL is configured.            |
| `properties`           | map of string to string       | no       | Free-form key/value pairs shown on the generated page.                       |
| `referenceIdentifiers` | array of string               | no       | Identifiers resolved against the published references to find build job links. |
| `deploymentTypes`      | array of enum                 | no       | Any of `CODE`, `CONFIG`, `INFRASTRUCTURE`.                                   |

`componentVersion` (`ComponentVersionCreateDto`):

| Field               | Type                  | Required | Description                                                               |
|---------------------|-----------------------|----------|-----------------------------------------------------------------------------|
| `versionName`       | string                | yes      | The version; a semantic version number is parsed out of it if possible.    |
| `versionControlUrl` | string                | yes      | Link to the source state that was deployed.                                |
| `commitRef`         | string                | yes      | Commit reference of that state.                                            |
| `commitedAt`        | timestamp with offset | yes      | When that commit was made. Note the single `t` in the field name.          |
| `componentName`     | string                | yes      | Component name; created if unknown.                                        |
| `systemName`        | string                | yes      | System name; created if unknown.                                           |
| `taggedAt`          | timestamp with offset | no       | When the version was tagged. Absent for deployments built from a branch.   |
| `publishedVersion`  | boolean               | no       | `true` for a version deployed from an artefact repository, `false` for one built from source. |

`changelog` (`ChangelogDto`):

| Field                | Type            | Description                                                                |
|----------------------|-----------------|------------------------------------------------------------------------------|
| `comparedToVersion`  | string          | The version this changelog is relative to.                                  |
| `comment`            | string          | Free text; defaults to the empty string.                                    |
| `jiraIssueKeys`      | array of string | Issue keys touched by this version. They are linked on the generated page, and the page is linked back from each issue. |

Query parameter:

| Parameter              | Type    | Description                                                     |
|------------------------|---------|-------------------------------------------------------------------|
| `readyForDeployCheck`  | boolean | Run the [ready-for-deploy check](#ready-for-deploy-check) before recording. Only effective together with a `changelog`. |

Responses:

| Status | Body                        | Meaning                                                                             |
|--------|-----------------------------|---------------------------------------------------------------------------------------|
| `201`  | empty, or `checkResult`     | The deployment was recorded. The body is present only if the check ran.               |
| `200`  | empty                       | A deployment with this external id already exists; nothing was changed.               |
| `200`  | `checkResult`               | The check result is `NOK`; the deployment was **not** recorded.                        |

After recording, the page generation is triggered asynchronously. A failure to trigger it does not fail the
request — the scheduled repair job picks the deployment up, see [Operations](operations.md).

### `PUT /api/deployment/{id}/state` — report the outcome

Role `deploymentlog-write`. Request body (`DeploymentUpdateStateDto`):

| Field        | Type                    | Required | Description                                                       |
|--------------|-------------------------|----------|---------------------------------------------------------------------|
| `state`      | enum                    | yes      | `SUCCESS`, `FAILURE` or `CANCELLED`.                               |
| `timestamp`  | timestamp with offset   | yes      | When the deployment ended.                                         |
| `message`    | string                  | no       | State message shown on the generated page.                         |
| `properties` | map of string to string | no       | Merged into the properties of the deployment.                      |

Returns `200 OK` with an empty body, `404` if the external id is unknown, and `400` if the state is not one
of the three accepted values (`STARTED` in particular cannot be set this way). On `SUCCESS` the environment
state is advanced to this version, and the page generation is triggered again.

### `GET /api/deployment/{id}` — read a deployment

Roles `deploymentlog-read` or `deploymentlog-write`. Returns the deployment (`DeploymentDto`) with `id`,
`externalId`, `startedAt`, `endedAt`, `state`, `startedBy`, `environment` (id, name, staging order,
productive flag), `componentVersion` (including the parsed version number, the deployment unit and the
component with its system), `links` and `properties`. `404` if unknown.

### `GET /api/deployment-doc/{id}` — jump to the generated page

Public, no authentication. Responds `302 Found` with a `Location` header pointing at the generated
Confluence page of that deployment, built from `jeap.deploymentlog.documentation.root-url` plus the page id.
`404` if the deployment or its page does not exist.

## Systems

### `GET /api/system/{systemName}` — current state of a system

Roles `deploymentlog-read` or `deploymentlog-write`. Returns the current version per environment for every
component of the system:

```json
{
  "systemName": "MySystem",
  "components": [
    {
      "name": "my-component",
      "deployments": [
        { "env": "DEV", "version": "1.15.2", "deployedAt": "2026-03-18T08:16:15+01:00" },
        { "env": "PROD", "version": "1.15.1", "deployedAt": "2026-03-10T19:02:41+01:00" }
      ]
    }
  ]
}
```

`404` if the system is unknown.

### `PUT /api/system/{id}/undeploy` — record an undeployment

Role `deploymentlog-write`. `{id}` is the external id of the undeployment. Body (`UndeploymentCreateDto`):
`systemName`, `componentName`, `environmentName` (upper-cased), `startedAt`, `startedBy` and an optional
`remedyChangeId`.

Removes the component from the environment state, marks it inactive when it is no longer deployed anywhere,
records an undeployment derived from the component's last deployment, and triggers the generation of an
undeployment page. Returns `200 OK`, or `404` if the system, component or environment is unknown.

### `GET /api/system/{systemName}/component/{componentName}/currentVersion/{environment}`

Roles `deploymentlog-read` or `deploymentlog-write`. Returns the currently deployed version as
`text/plain`. `404` if the system, component or environment is unknown, or if nothing is currently deployed.

### `GET /api/system/{systemName}/component/{componentName}/previousVersion/{environment}?version=…`

Roles `deploymentlog-read` or `deploymentlog-write`. Returns, as `text/plain`, the version of the last
successful CODE deployment on that environment that differs from the `version` query parameter — the
counterpart to `currentVersion` when a changelog between the previous and the new image state is needed.
Legacy deployments without a type are treated as CODE deployments. Config-only, infrastructure-only and
undeployment entries are skipped. `404` when there is none.

### `GET /api/system/{systemName}/component/{componentName}/previousDeployment/{environment}?version=…`

Roles `deploymentlog-read` or `deploymentlog-write`. Returns the last successful CODE deployment whose
version differs from the `version` query parameter as a full `DeploymentDto`. CONFIG-only and
INFRASTRUCTURE-only entries are skipped so the result contains a deployable image version for rollback.
Legacy deployments without a deployment type remain eligible because they predate the deployment-type
classification and are treated as CODE deployments. Undeployments are never eligible rollback candidates.
`404` when there is none.

### `POST /api/system/{systemName}/alias/{aliasName}` — add an alias

Role `deploymentlog-write`. Registers an additional name under which the system is found. Returns `201`,
`404` if the system does not exist, `400` if the alias is already used as an alias or as a system name.

### `POST /api/system/{oldSystemName}/migrate-to/{newSystemName}` — rename a system

Role `deploymentlog-write`. Renames the system and keeps the old name as an alias, then asynchronously moves
the generated pages under a page tree for the new name and deletes the old system page tree. Returns `201`,
`404` if the old system does not exist, `400` if the new name is already taken.

### `POST /api/system/{systemName}/merge-from/{oldSystemName}` — merge two systems

Role `deploymentlog-write`. Moves the components and the generated pages of `oldSystemName` into
`systemName` and keeps the old name as an alias. Returns `200`, `400` if both names resolve to the same
system, `404` if either system does not exist.

## System groups

System groups are administrative structure data. They do not create or move Confluence pages. Every system is
ungrouped or belongs to exactly one group. Group responses contain the stable group `id`, its trimmed `name`, and a
deterministically sorted `systems` array whose entries contain at least `id` and `name`. Group lists are sorted
case-insensitively by group name and then by ID.

Read operations require `deploymentlog-read` or `deploymentlog-write`; mutations require `deploymentlog-write`.
These endpoints are part of the protected administrative `/api` surface, not the public
`GET /api/deployment-doc/**` endpoint.

| Endpoint | Success | Errors | Purpose |
|----------|---------|--------|---------|
| `GET /api/system-groups` | `200` | — | Lists every group in deterministic order. |
| `GET /api/system-groups/{groupId}` | `200` | `404` unknown group | Reads one group and its assigned systems. |
| `POST /api/system-groups` | `201` with group | `400` invalid name, `409` duplicate name | Creates a group from `{ "name": "Border Control" }`. Names are trimmed, non-empty, have no application-defined length limit and are case-insensitively unique. |
| `PUT /api/system-groups/{groupId}` | `200` with group | `400` invalid name, `404` unknown group, `409` duplicate name | Renames a group without changing its ID or assignments. |
| `DELETE /api/system-groups/{groupId}` | `204` | `404` unknown group | Deletes the group and removes its assignments; systems remain present and ungrouped. |
| `PUT /api/system-groups/{groupId}/systems/{systemId}` | `204` | `404` unknown group or system | Assigns the system, atomically replacing a previous group assignment. Repeating the same assignment is idempotent. |
| `DELETE /api/system-groups/{groupId}/systems/{systemId}` | `204` | `404` unknown group or system | Removes the assignment only when it points to this group. Repetition is idempotent. |

System-group error responses follow RFC 9457 Problem Details. For example, a duplicate name returns:

```json
{
  "title": "Conflict",
  "status": 409,
  "detail": "System group name already exists",
  "errorCode": "SYSTEM_GROUP_NAME_CONFLICT"
}
```

The API uses the stable error codes `INVALID_SYSTEM_GROUP_NAME`, `SYSTEM_GROUP_NAME_CONFLICT`,
`SYSTEM_GROUP_NOT_FOUND`, and `SYSTEM_NOT_FOUND`.

## Environments

### `GET /api/environment/{environmentName}/components`

Roles `deploymentlog-read` or `deploymentlog-write`. Returns the components currently deployed on that
environment across all systems, as a list of `componentName` / `version` pairs. The environment name is
upper-cased before the lookup.

## Artifact versions and references

Both resources let a build publish a link to itself *before* the deployment happens. At page-generation
time the deployment page then shows the build job link of the artefact it deployed.

### `PUT /api/artifact-version/{id}`

Role `deploymentlog-write`. `{id}` is a UUID chosen by the caller. Body: `coordinates` (the same
coordinates the deployment later reports in its `deploymentUnit`) and `buildJobLink`. Returns `201` when
stored, `200` when an artifact version with this id already exists.

### `PUT /api/reference/{id}`

Role `deploymentlog-write`. `{id}` is a UUID and has to match the `id` in the body. Body: `id`,
`referenceIdentifier`, `type` and `uri`. The only currently defined type is
`BUILD_JOB_LINK_BY_GIT_URL_AND_VERSION`, whose reference identifier is the Git URL, an `@`, and the version.
A deployment picks such references up through its `referenceIdentifiers`. Returns `201` when stored, `200`
when a reference with this id already exists, `400` when the path id and the body id differ.

## Blog posts

### `POST /api/blogposts`

Role `deploymentlog-write`. Body: `spaceKey`, `title` and `content` (Confluence storage format). Creates a
blog post in Confluence using the configured technical user and returns `201` with the new page id as the
body. This is a synchronous call — a Confluence error is passed through with its original status. The
endpoint exists so that an instance can publish release announcements alongside the generated deployment
documentation; it is unrelated to the generated page tree.

## Jobs

All job endpoints require the role `deploymentlog-write`. They trigger, on demand, the same work the
scheduled jobs do, see [Operations](operations.md#manual-triggers).

| Endpoint                                                             | Status | Purpose                                                                                        |
|----------------------------------------------------------------------|--------|--------------------------------------------------------------------------------------------------|
| `POST /api/jobs/docgen`                                              | `201`  | Regenerates the pages of **all** systems, synchronously. For test and development only.          |
| `POST /api/jobs/docgen/system/{systemName}?year=…`                   | `201`  | Regenerates the pages of one system asynchronously, optionally restricted to one year.           |
| `POST /api/jobs/docgen/deployment/{deploymentId}`                    | `201`  | Regenerates the pages for a single deployment, addressed by its internal UUID.                    |
| `POST /api/jobs/outdatedPageHousekeeping`                            | `200`  | Runs the housekeeping deleting outdated pages.                                                   |
| `POST /api/jobs/docgen/system/{systemName}/repairJiraLinks?from=…&to=…` | `200` | Re-writes the Confluence remote links into the Jira issues of the deployments of one system started between `from` (inclusive) and `to` (exclusive), both `yyyy-MM-dd`. |

## Ready-for-deploy check

With `?readyForDeployCheck=true` and a `changelog` containing `jiraIssueKeys`, the service asks Jira about
those issues before recording the deployment. The result is returned as `checkResult`:

| Field                | Description                                                                                                        |
|----------------------|----------------------------------------------------------------------------------------------------------------------|
| `result`             | `OK`, `WARNING` or `NOK`.                                                                                            |
| `message`            | Human-readable summary of the findings, `null` when everything is fine.                                              |
| `issuesWithoutLabel` | Issues that exist but do not carry the `R4DEPLOY` label.                                                             |
| `issuesNotFound`     | Issue keys that could not be resolved: they do not exist, are not readable for the technical user, or are not syntactically valid issue keys. |
| `projectsNotVisible` | Project keys of unresolved issues whose project is not visible to the technical user at all — often a key that was never a Jira reference. |

The outcome decides what happens to the request:

- **`OK`** — no findings. The deployment is recorded, `201`.
- **`WARNING`** — some issues could not be resolved, but every resolved issue carries the label. The
  deployment **is** recorded, `201`, with the findings in the body.
- **`NOK`** — at least one resolved issue lacks the `R4DEPLOY` label. The deployment is **not** recorded and
  the response is `200` with the check result, so the caller can abort the deployment.

If Jira itself is unavailable or rejects the technical user, the request fails with `503` and nothing is
recorded.

## Related

- [Getting Started](getting-started.md)
- [Configuration](configuration.md)
- [Architecture](architecture.md)
- [Operations](operations.md)
