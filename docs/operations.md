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
| Housekeeping | `jeap.deploymentlog.housekeeping.cron` with fallback to `jeap.deploymentlog.documentation-generator.housekeeping.cron`, default daily at 03:30 | `outdated-page-housekeeping` (at least 60s, at most 30m) | Independently runs enabled Confluence-page cleanup and persistent data retention. |
| Metrics update            | every 15 minutes, and once at startup                                 | none                        | Refreshes the page generation lag gauge.                     |

Setting a cron expression to `-` disables the corresponding job.

### Missing page generation

The job looks for deployments whose page is missing or older than the deployment's last state change, and
re-triggers the generation for them. The following guards keep it from fighting the normal, request-triggered
generation:

- Only deployments **older than `min-age-minutes`** (default 5) are considered — a younger one is assumed
  to be still generating.
- Discovery is limited to `max-age-minutes` (default 10080, i.e. seven days), based on `Deployment.startedAt`.
  Persisted pending generation requests are exempt from this maximum age, so they remain repairable during longer
  outages. Older missing or outdated pages without a pending request require explicit regeneration or a larger window.
- At most `retried-pages-limit` (default 50) are submitted per run. The last repair
  attempt is persisted per deployment when the worker starts it, not when selecting or enqueueing it. Never-attempted
  pages are selected oldest-first, followed by the least recently
  attempted pages; permanently failing pages therefore cannot block newer entries in the backlog. Pages intentionally
  removed by Confluence housekeeping are marked accordingly and excluded from automatic repair. A deployment state
  update or explicit single-deployment generation request clears that marker before remote generation, so even a failed
  attempt or queue rejection leaves the page eligible for subsequent repair.

From version 16.0.0, V30 no longer marks historical deployments without a page-tracking row for legacy classification.
These missing pages enter automatic repair directly within the configured age window (seven days by default).
Pages historically deleted by housekeeping without a suppression marker can therefore be recreated. Existing
suppression markers and future housekeeping deletions remain protected.
Databases that already applied the original V30 retain its legacy markers. The repair job still classifies these rows
using the configured Confluence housekeeping policy before admitting them to repair. An explicit request or state
update clears the legacy marker and takes precedence over classification. See the 16.0.0 migration instructions in
[CHANGELOG.md](../CHANGELOG.md) for the required Flyway migrate/repair procedure when upgrading.

Repair tasks use the background queue. New deployment and undeployment pages overtake queued repair work, while the
configured live-task burst guarantees that the repair backlog continues to make progress. Multiple queued requests
for the same deployment are coalesced into one task.
Before executing a queued repair, the worker rechecks persistent page state and suppression under the system lock.
Confluence housekeeping uses the same lock, so a repair queued before a deliberate deletion cannot recreate the page
after that deletion. Obsolete repairs for pages already brought up to date are skipped as well.
Explicit generation persists a pending request identifier before enqueueing. Housekeeping cannot suppress deployments
with an outstanding request, even if the worker times out waiting for the system lock. Pending requests also enter
repair when an older page still exists. Only successful generation acknowledges the captured request identifier;
a newer request received during generation remains pending. This also preserves requests across process restarts.

Re-triggering a deployment regenerates its whole page path, not just its deployment letter page: the system
page, the deployment history page of the environment, the yearly deployment list page and the deployment
history overview are all written again on the way. The pages that aggregate several deployments are
therefore repaired along with the deployment, without being tracked separately.

### Housekeeping

The common housekeeping run performs two independent cleanup steps: Confluence deployment-page cleanup and
persistent data retention. Either step can be disabled without disabling the other. The run uses one ShedLock lock,
so multiple service instances cannot execute it concurrently.

```properties
jeap.deploymentlog.housekeeping.cron=0 30 3 * * *

jeap.deploymentlog.housekeeping.confluence-pages.enabled=true
jeap.deploymentlog.housekeeping.confluence-pages.min-age=7d
jeap.deploymentlog.housekeeping.confluence-pages.keep-per-environment=200

jeap.deploymentlog.housekeeping.data-retention.enabled=false
jeap.deploymentlog.housekeeping.data-retention.duration=365d
jeap.deploymentlog.housekeeping.data-retention.batch-size=500
```

The defaults and validation rules for all properties are listed in
[Configuration](configuration.md#scheduled-jobs).

#### Confluence deployment-page cleanup

This step limits the generated tree on test stages without deleting the corresponding deployment data. It deletes
deployment detail pages that are all of the following:

- on a **non-productive** environment (`productive = false`), so productive history is never cleaned up,
- older than `confluence-pages.min-age` (default 7 days),
- beyond the `confluence-pages.keep-per-environment` (default 200) most recent pages of that system and environment,
- not the last successful deployment page of the component, not the last deployment page of the component
  regardless of state, and not newer than the component's last successful deployment.

The detail page is removed from Confluence together with its page-tracking record. The deployment remains available
through the API and can still appear in flows, Jira issue pages and evaluations. A page that cannot be deleted is
logged and skipped. Afterwards the deployment history pages of the affected system and environment combinations are
regenerated so that they no longer link to the removed detail page.

#### Persistent data retention

This step permanently deletes deployments whose `started_at` lies before the configured retention cutoff. It is
disabled by default. Enabling it requires a positive `data-retention.duration`; an absent, invalid or non-positive
duration prevents application startup.

Expired `SUCCESS`, `FAILURE` and `CANCELLED` deployments are eligible. The following data remains protected:

- `STARTED` deployments,
- every deployment assigned to an `OPEN` flow,
- deployments referenced by the current component-version state of a stage,
- component versions that are still referenced by another retained business record.

A `CLOSED` or `ABORTED` flow is deleted only as a complete unit after all of its deployments are eligible. The cleanup
also removes exclusively dependent details, unreferenced changelogs and unreferenced component versions. Systems,
components, environments and the generated Confluence structure are not deleted. `data-retention.batch-size` limits
the amount selected for one run; later runs continue with the remaining data.

Before database deletion, any remaining deployment detail page and its tracking record are removed. If page cleanup
for one retention unit fails, that complete unit is retained. After successful deletion, the affected deployment
histories, stage overviews, component pages, Jira project pages and Jira issue pages are regenerated.

#### Retrying documentation refreshes

Every successful data-retention batch persists its aggregate-page refresh task in the same database transaction as
the deleted deployment data. One task row stores the affected systems, environments, components and Jira issues as an
opaque JSON payload; these values are never queried individually. The task is
removed only after the Confluence refresh succeeds. A lock timeout, Confluence failure or instance restart therefore
leaves the task pending; every subsequent housekeeping run retries up to 50 oldest pending tasks, even when data
retention has meanwhile been disabled.

## Manual triggers

The same work can be triggered on demand through the job endpoints, all requiring the role
`deploymentlog-write` — see [REST API](rest-api.md#jobs):

- `POST /api/jobs/docgen/deployment/{deploymentId}` — one deployment, the usual first step when a single
  page is wrong.
- `POST /api/jobs/docgen/system/{systemName}?year=…` — one system, optionally restricted to one year.
- `POST /api/jobs/housekeeping` — both enabled housekeeping steps, ahead of their schedule.
- `POST /api/jobs/outdatedPageHousekeeping` — compatibility endpoint delegating to the same housekeeping run.
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
| `deploymentlog.docgen.jiraissuelink.error`   | counter | Incremented after all retries to create or update a stable Jira link to a DeploymentLog issue page failed. |
| `deploymentlog_generate_deployment_page`     | timer   | Duration of generating the pages for one deployment.                                              |
| `update_deployment_history_pages`            | timer   | Duration of refreshing the deployment history pages after a housekeeping run.                     |
| `deployment_counter`                         | counter | Terminal deployments, tagged with `system`, `component`, `environment` and `result` (`success` or `failed`). |
| `deployment_duration_seconds`                | timer   | Duration of a terminal deployment from `started_at` to `ended_at`, tagged with `system`, `component` and `environment`. |
| `flow_counter`                               | counter | Terminal flow transitions, tagged with `system`, `component`, `type` and `state` (`closed` or `aborted`). |
| `flow_open`                                  | gauge   | Current persistent number of open flows, tagged with `system`, `component` and `type`. |
| `flow_duration_seconds`                      | timer   | Duration in seconds from `Flow.born_at` to the successful deployment on the effective final environment, tagged with `system`, `component` and `type`. |
| `flow_recovery_duration_seconds`             | timer   | Recovery duration of successfully closed rollback flows, tagged with `system`, `component` and their effective final `environment`. |

Counters and duration values are emitted only for actual persisted state transitions, so retrying the same request
does not count a terminal deployment or flow twice. A deployment duration is omitted and a warning is logged if
`started_at` or `ended_at` is missing, or if `ended_at` precedes `started_at`. Flow durations are emitted only for
successfully closed flows; open and aborted flows do not contribute a duration.

`flow_open` is reconstructed from the database at startup, updated incrementally after local flow changes and reconciled
periodically (every 30 seconds by default). Local updates do not query the complete flow history. The gauge therefore
remains correct across application restarts and converges
after changes made by another service instance or by housekeeping. Known label combinations that only have terminal
flows remain present with value zero after a restart. Historical label combinations are discovered once during startup;
periodic reconciliation queries only flows in state `OPEN` so that the state index can be used. The refresh interval is configurable through
`jeap.deploymentlog.metrics.flow-open-refresh-interval`.

Micrometer's Prometheus naming convention may expose counters with a `_total` suffix, for example
`deployment_counter_total`. Enum label values are normalized to lower case; deployment, component and environment
UUIDs, deployment external ids and version names are deliberately not used as labels in order to avoid unbounded
cardinality.

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
| Docgen lock cannot be acquired within 30 seconds (default) | Deployment-page generation is deferred to repair, while a persisted retention refresh remains pending. Non-repairable full generation, migration/merge and structure/history refreshes report failure instead of silently succeeding. |
| An instance dies mid-generation            | Its lock expires; the pages it did not finish are detected as missing or outdated and repaired.            |

## Related

- [Architecture](architecture.md)
- [Configuration](configuration.md)
- [Documentation Generation](documentation-generation.md)
- [REST API](rest-api.md)
