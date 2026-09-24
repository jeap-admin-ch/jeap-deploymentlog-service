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
Databases that already applied the original V30 retain its legacy markers. The repair job releases at most
`retried-pages-limit` markers per run, only within the configured repair age window, before selecting work. This avoids
an unbounded startup or scheduled bulk update. Those pages are repaired gradually; historical pages outside the repair
window require explicit regeneration. An explicit request or state update clears the marker immediately. See the
16.0.0 migration instructions in [CHANGELOG.md](../CHANGELOG.md) for the required Flyway migrate/repair procedure when
upgrading.

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

The common housekeeping run performs three independent cleanup steps in this order: Confluence deployment-page
cleanup, persistent data retention and component-page reconciliation. Each step can be disabled without disabling
the others. The run uses one ShedLock lock, so multiple service instances cannot execute it concurrently.

```properties
jeap.deploymentlog.housekeeping.cron=0 30 3 * * *

jeap.deploymentlog.housekeeping.confluence-pages.enabled=true
jeap.deploymentlog.housekeeping.confluence-pages.min-age=7d
jeap.deploymentlog.housekeeping.confluence-pages.keep-per-environment=200

jeap.deploymentlog.housekeeping.data-retention.enabled=false
jeap.deploymentlog.housekeeping.data-retention.duration=365d
jeap.deploymentlog.housekeeping.data-retention.batch-size=500

jeap.deploymentlog.housekeeping.component-pages.enabled=true
jeap.deploymentlog.housekeeping.component-pages.batch-size=100
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
- deployments referenced by the current component-version state of a stage,
- component versions that are still referenced by another retained business record.

Deployments expire individually, including repeated deployments of the same version. The cleanup also removes
exclusively dependent details, unreferenced changelogs and unreferenced component versions. Version metrics and classification only use retained deployments. Systems,
components, environments and the generated Confluence structure are not deleted. `data-retention.batch-size` limits
the amount selected for one run; later runs continue with the remaining data.

Before database deletion, any remaining deployment detail page and its tracking record are removed. If page cleanup
for one deployment fails, that deployment is retained while other eligible deployments can still be deleted.
After successful deletion, the affected deployment
histories, stage overviews, component pages, Jira project pages and Jira issue pages are regenerated.

#### Component-page reconciliation

With `jeap.deploymentlog.flow.enabled=false`, version-page generation and component-page cleanup are skipped.
Existing component pages are retained, and stage configuration is not resolved by these paths.

Component pages are retained while their component has at least one retained CODE deployment (excluding undeployments)
on a stage within the configured start-to-end range. Historical deployments qualify even without a staging type.
After data retention, and also when data retention is disabled, housekeeping selects up to
`component-pages.batch-size` obsolete tracking records in deterministic order. Each candidate is rechecked under the
component system's documentation lock before its Confluence page is deleted.

The candidate query and final conditional tracking deletion use short database transactions. The Confluence deletion
runs between them without an open database transaction. If Confluence deletion fails, tracking is retained and a
later housekeeping run retries it while continuing with other candidates. If a concurrent relevant deployment appears, the recheck
or final conditional deletion preserves the tracking as far as the HTTP/database boundary permits; normal
component-page generation recreates a deleted page when required.

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
- `POST /api/jobs/housekeeping` — all enabled housekeeping steps, ahead of their schedule.
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
| `deployment_counter`                         | counter | Persistent cumulative number of terminal deployments, tagged with `system`, `component`, `environment`, `deployment_type` (`CODE`, `CONFIG` or `INFRASTRUCTURE`) and `result` (`success`, `failed` or `cancelled`). |
| `deployment_duration_seconds`                | timer   | Duration of a terminal deployment from `started_at` to `ended_at`, tagged with `system`, `component`, `environment` and `deployment_type`. |
| `version_start` | gauge | Distinct successful versions on the start stage. |
| `version_end` | gauge | Distinct successful versions on the end stage. |
| `version_staging_latency_seconds_sum` / `version_staging_latency_seconds_count` | gauges | Sum and number of first-success latencies among retained versions, measured between deployment start timestamps. |
| `autostaging_enabled` | gauge | Latest non-ROLLBACK CODE deployment on the start stage: 1 if its explicit targets contain the end stage, 0 otherwise; NaN if no eligible deployment exists. |

Only the configured start stage determines `autostaging_enabled`; REF is merely an example. An AD_HOC deployment
on the end stage does not change this status, even if its explicit targets contain the end stage.

The former `flow_counter`, `flow_open`, `flow_duration_seconds` and `flow_recovery_duration_seconds` are removed.
Version metrics have `system`, `component`, `start_environment` and `end_environment` labels, with no version,
flow type or flow state labels. Version identity is component plus version name, not a ComponentVersion database id.
Repeated successful deployments never count the same version twice on a stage.

Deployment counters retain their existing persistence and reconciliation. Deployment duration observations are emitted
after commit and are not replayed on restart. Version counts, latency totals and AutoStaging status are calculated
directly from retained deployments on every replica. They are gauges because data retention can decrease them.
First successful timestamps determine latency; versions missing a successful start or end, or with end before start,
do not contribute a latency observation. These are the first successes among retained deployments, not the first
successes in each dashboard window. Existing deployments are neither copied nor reclassified. Missing stored targets,
including those of old deployments, mean no AutoStaging in the calculation.

All replicas publish the same database totals, refreshed every 30 seconds by default. Deduplicate replicas with
`max by (system, component, start_environment, end_environment)` before calculating changes over a time window.
For example, Lost Version Ratio is:

```promql
1 -
sum(delta((max by (system, component, start_environment, end_environment) (version_end))[$__range:]))
/
sum(delta((max by (system, component, start_environment, end_environment) (version_start))[$__range:]))
```

This is intentionally a throughput ratio, not a matched cohort: versions may start and finish in different windows,
so short windows can yield negative values. A window with no starts has no defined ratio.
AutoStaging is a per-component status, not a ratio. It reflects the latest start-stage CODE deployment by
`started_at` (deployment id breaks timestamp ties), excluding ROLLBACK and undeployments. Its outcome is irrelevant:
a failed attempt still expresses the requested automation. A RETRY can change the status. Without an eligible
start-stage deployment the status is unknown (`NaN`), rather than disabled. Deduplicate replicas with:

```promql
max by (system, component, start_environment, end_environment) (autostaging_enabled)
```

This is inferred from the latest request, not a direct observation of pipeline configuration or proof of an
automatically completed execution. No explicit targets means no AutoStaging.

Mean latency is the window delta of `version_staging_latency_seconds_sum` divided by the delta of
`version_staging_latency_seconds_count`, also deduplicated across replicas. Scrape timing affects window boundaries. Retention or stage-configuration changes also affect these deltas;
windows containing such changes do not represent pure arrival counts.

Deployments carrying several `deploymentTypes` still publish one ordinary deployment metric series per type; select
`deployment_type="CODE"` when comparing those metrics with the version metrics.

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
| Tracked structure page returns 404 but its title still exists | The page is recovered by its space-wide unique title and moved to the expected parent. If the technical user cannot see it, generation fails once with a permission-oriented error instead of retrying duplicate creation. |

## Related

- [Architecture](architecture.md)
- [Configuration](configuration.md)
- [Documentation Generation](documentation-generation.md)
- [REST API](rest-api.md)
