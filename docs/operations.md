# Operations

The Deployment Log Service is designed so that a temporary failure of Confluence or Jira never loses a
deployment record: the deployment is recorded synchronously, everything else is repaired later. This page
describes the jobs that do the repairing, the metrics to watch, and the knobs available at runtime.

## Scheduled jobs

Three scheduled tasks run in the documentation generator. The two cron jobs hold a ShedLock lock, so exactly
one instance runs them at a time.

| Job                       | Schedule                                                              | Lock                        | Purpose                                                    |
|---------------------------|-----------------------------------------------------------------------|-----------------------------|--------------------------------------------------------------|
| Missing page generation   | `jeap.deploymentlog.documentation-generator.scheduled.cron`, default every 10 minutes | `generate-missing-pages` (at least 60s, at most 5m)   | Regenerates deployment pages that are missing or outdated. |
| Outdated page housekeeping| `jeap.deploymentlog.documentation-generator.housekeeping.cron`, default daily at 03:30 | `outdated-page-housekeeping` (at least 60s, at most 30m) | Deletes old deployment pages of non-productive environments. |
| Metrics update            | every 15 minutes, and once at startup                                 | none                        | Refreshes the page generation lag gauge.                     |

Setting a cron expression to `-` disables the corresponding job.

### Missing page generation

The job looks for deployments whose page is missing or older than the deployment's last state change, and
re-triggers the generation for them. Two guards keep it from fighting the normal, request-triggered
generation:

- Only deployments **older than `min-age-minutes`** (default 5) are considered — a younger one is assumed
  to be still generating.
- Only deployments **younger than `max-age-minutes`** (default 1440, i.e. one day) are considered, and at
  most `retried-pages-limit` (default 50) per run.

Re-triggering a deployment regenerates its whole page path, not just its deployment letter page: the system
page, the deployment history page of the environment, the yearly deployment list page and the deployment
history overview are all written again on the way. The pages that aggregate several deployments are
therefore repaired along with the deployment, without being tracked separately.

### Outdated page housekeeping

The generated tree would otherwise grow without bound on the test stages. The housekeeping deletes
deployment pages that are all of the following:

- on a **non-productive** environment (`productive = false`), so productive history is never cleaned up,
- older than **7 days**,
- beyond the `keep-deployment-page-per-env-count` (default 200) most recent pages of that system and
  environment,
- not the last successful deployment page of the component, not the last deployment page of the component
  regardless of state, and not newer than the component's last successful deployment.

The pages are removed in Confluence and in the database; a page that cannot be deleted is logged and the run
continues. Afterwards the deployment history pages of the affected system and environment combinations are
regenerated so that the deleted entries disappear from the lists.

## Manual triggers

The same work can be triggered on demand through the job endpoints, all requiring the role
`deploymentlog-write` — see [REST API](rest-api.md#jobs):

- `POST /api/jobs/docgen/deployment/{deploymentId}` — one deployment, the usual first step when a single
  page is wrong.
- `POST /api/jobs/docgen/system/{systemName}?year=…` — one system, optionally restricted to one year.
- `POST /api/jobs/outdatedPageHousekeeping` — the housekeeping, ahead of its schedule.
- `POST /api/jobs/docgen/system/{systemName}/repairJiraLinks?from=…&to=…` — the Jira remote links of a date
  range.
- `POST /api/jobs/docgen` — everything, synchronously. Intended for test and development only; on a
  populated instance this rewrites the whole tree.

## Metrics

The metrics are exposed through the actuator endpoints provided by the jEAP monitoring starter.

| Metric                                       | Type    | Meaning                                                                                          |
|----------------------------------------------|---------|----------------------------------------------------------------------------------------------------|
| `deploymentlog.docgen.deploymentpages.lag`   | gauge   | Number of deployments of the last 7 days whose page is missing or outdated. Refreshed every 15 minutes. |
| `deploymentlog.docgen.deploymentpages.error` | counter | Incremented for every failed generation attempt, including failed system migrations and merges.   |
| `deploymentlog_generate_deployment_page`     | timer   | Duration of generating the pages for one deployment.                                              |
| `update_deployment_history_pages`            | timer   | Duration of refreshing the deployment history pages after a housekeeping run.                     |

A lag that stays above zero over several intervals means the repair job cannot keep up or keeps failing —
check the log for docgen warnings and the availability of Confluence. A lag that spikes and recovers is
normal after a Confluence outage. Note that the lag only counts the last 7 days, so pages older than that
are never repaired automatically.

## Environment settings

Environments are created automatically when a deployment first names them. The name is upper-cased, and the
three attributes are derived from it at creation time:

| Attribute       | Set at creation to                | Effect                                                                                        |
|-----------------|-----------------------------------|-------------------------------------------------------------------------------------------------|
| `productive`    | `true` for the name `PROD`        | Deployment pages of productive environments are never deleted by the housekeeping.             |
| `development`   | `true` for the name `DEV`         | Development environments are excluded from the version comparison on the system page, because they usually carry snapshot versions. |
| `staging_order` | `Integer.MAX_VALUE` if productive, otherwise `0` | Controls the order in which the environments are listed on the generated pages, and which environment counts as the "next stage" for the highlighting. |

There is no API for these attributes. An instance that uses names other than `DEV` and `PROD`, or that has
more than two stages, has to adjust the `productive`, `development` and `staging_order` columns of the
`environment` table directly — typically once, after the environment first appears.

## Failure behaviour

| Situation                                  | Effect                                                                                                    |
|--------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| Confluence unreachable or failing          | The request that recorded the deployment already succeeded. The generation fails, the error counter is incremented and the lag gauge rises; the repair job retries. |
| Confluence rejects an update as a conflict | The adapter waits `retry-on-conflict-wait-duration`, re-reads the page, re-renders the content and retries — up to three update attempts per call. If the conflict persists, the retry around the whole call repeats it up to four times with exponential backoff, so at most twelve update requests are sent. |
| Jira issue cannot be updated               | Logged as a warning; the page generation succeeds. Use `repairJiraLinks` to catch up.                       |
| Jira unavailable during a ready-for-deploy check | The request fails with `503` and the deployment is **not** recorded — the check is synchronous by design. |
| Docgen lock cannot be acquired within 3 minutes | The run is skipped with a warning; the repair job picks the deployment up later.                        |
| An instance dies mid-generation            | Its lock expires; the pages it did not finish are detected as missing or outdated and repaired.            |

## Related

- [Architecture](architecture.md)
- [Configuration](configuration.md)
- [Documentation Generation](documentation-generation.md)
- [REST API](rest-api.md)
