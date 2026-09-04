# Scheduling

How a job goes from "something wants this to run" to "it ran, and we know it ran" - dispatch, runners, handoff, liveness
detection, and who's responsible for what.

Throughout this document: **[library]** marks something `uetlib-job` provides and manages for you. **[project]** marks
something a consuming project defines or implements itself.

## Concepts

- **Job** **[project implements, library defines the interface]** - a class implementing `Job`
  (`run(JobRunContext)`, plus optional `propertiesConfig()`/`triggersConfig()`/`supportsForceRun()`). Discovered via
  `@RegisteredJob` + `@EnableJobs` classpath scanning.
- **JobRun** **[library]** - one execution attempt of a job, `t_job_run`. Append-only history; state only ever moves
  forward (`BUSY`/`SCHEDULED` → a terminal state).
- **Queue row** **[library]** - `t_job_run_queue`. A separate, mutable, re-claimable table representing "this job should
  run" / "this job is currently running", decoupled from the
  `t_job_run` history. States: `QUEUED` → `CLAIMED` → `RUNNING`, then deleted.

## 1. Getting into the queue

Nothing (except for a local `--job` execution) runs a job directly.

- **Triggers** **[project configures, library executes]** - `CronTrigger`, `FixedRateTrigger`,
  `PgListenTrigger`, `DatabaseSubmissionTrigger`, etc. A project declares triggers via
  `Job#triggersConfig()` or the status API; `TriggerCoordinatorService` **[library]** owns firing them and calls
  `JobService#queueNewRun(...)` on fire. This is the only thing that inserts
  `QUEUED` rows on a schedule - a project never calls the queue API directly for scheduled work.
- **Manual start / replay** **[project triggers via API, library executes]** -
  `POST /job/{id}/start`, `/job/start_all`, `/job/{id}/replay_run[/{jobRunId}]`. Same
  `queueNewRun(...)` path as triggers. Served by the primary instance only (§9).
- **`--job <class> [--run <id>]` CLI** **[library]** - bypasses the queue entirely. Used by dedicated Kubernetes pods
  (see §3) and available for local/manual single-job runs. Not something a project's own code calls; it's a process
  entry point (`JobApplication`).

A queue row also carries a priority. `t_job_run_queue.priority` is seeded from the job's `PRIORITY`
property when the row is queued; dequeue order is `COALESCE(row.priority, 0) DESC, createdAt ASC`. Reorder a
still-queued row via `PUT /queue/{jobQueuedRunId}/priority` **[library, project calls it]**; `RUNNING` rows are never
reordered.

## 2. Dispatch

**[library, entirely]** - `JobRunQueueService`, on every instance (except dedicated `job-run`
pods), every 5s (`@Scheduled`) plus a Postgres `LISTEN`/`NOTIFY` fast path for lower latency:

1. Page through `QUEUED` rows, highest priority / oldest first, stopping once a row was actually claimed.
   A row this instance matches but cannot take - its runner group is at capacity, or another instance won
   the claim - is not progress, so the scan looks behind it rather than stopping; `max.pages.per.check`
   bounds how far.
2. For each, check whether *this instance* matches: its own build version must **exactly** equal the row's `versionTag`
   (see "Version-pinned claiming" below); the job's `RUNNER` property then decides which of the two paths applies (see
   "Runner kind vs. taints" below) - `CURRENT` requires this instance's own taints
   (`ch.uetlib.job.runner.instance.taints`) to be a superset of the row's required taints (`JOB_RUN_TAINTS` job
   property); `DEDICATED_K8S_JOB` requires the same taint check *plus* a dedicated-runner bean available on this
   instance.
3. On a match: atomically claim it (`QUEUED` → `CLAIMED`, tagged with this instance's claim identifier - see end of §2),
   then dispatch to the matching `JobRunner`.
4. `CLAIMED` → `RUNNING`, linked to the new `t_job_run` row - *before* the runner is handed the job, since it may
   start (and finish) it on another thread immediately, and a `BaseStateJob` handoff (§4) re-queues that same row.
5. On failure, or if a `CLAIMED` row's claiming instance died before ever reaching `RUNNING`
   (`CLAIM_TIMEOUT`, default 2m): released back to `QUEUED` for another instance to retry.

**Runner kind vs. taints [library, entirely].** These are two different, orthogonal decisions, both made by
`JobRunQueueService#claimIfMatchesThisRunner`, in this order:

1. *Which kind of runner does this job want* - `CURRENT` (in-process, the default) or
   `DEDICATED_K8S_JOB` (its own K8s pod)? Decided **solely** by the job's own `RUNNER` property (cached per job, same
   30s-refresh pattern as the timeout properties in §7) - never by taints.
2. *Given that kind, does this specific instance qualify* - taint subset-matching, as described above (§1's
   `JOB_RUN_TAINTS`/`ch.uetlib.job.runner.instance.taints`), plus (for
   `DEDICATED_K8S_JOB` only) whether this instance actually has a dedicated-runner bean at all.

A `DEDICATED_K8S_JOB` row with no `JOB_RUN_TAINTS` of its own - the common case, since a project sets `RUNNER`, not a
synthetic taint - must still only ever be claimed via the dedicated path. Taint subset-matching alone can't tell the two
paths apart: the empty set is trivially a subset of *any*
taint set, so a row with no taints would otherwise satisfy the plain path too, and (being checked first) would end up
dispatched to a `CurrentJobRunner` instance instead of K8s. This was a real bug, fixed by making the `RUNNER` property
decide which path even applies before either taint check runs, rather than trying to distinguish the two paths by taint
content.

**Version-pinned claiming [library, entirely].** Every queued row is tagged with the enqueueing instance's own build
version (`JobService#versionTag`, from `BuildProperties`); `JobRunQueueService`
only ever claims a row whose `versionTag` exactly matches its own. This matters during a staggered or rolling release,
where instances running different versions briefly coexist: without this check, an older instance could claim a row for
a job class it doesn't even have yet, or a newer instance could run a row using different (possibly incompatible) code
than whatever enqueued it intended. Only the exact version that enqueued a run may execute it - never an older or newer
one.

One consequence worth knowing: if every instance of the version that enqueued a row disappears before it's claimed (e.g.
a very short-lived old version during a fast rollout), that row is version-pinned to nobody. It doesn't stay `QUEUED`
forever, though - `expireUnclaimableQueuedRuns`
(§7) eventually marks it `CANCELLED` once it's had no activity for `QUEUE_TIMEOUT`.

**[project]** only participates by setting `JOB_RUN_TAINTS` on a job (to pin it to specific instances). Each instance's
claim identifier defaults to the `HOSTNAME` env var (the pod name on Kubernetes); override it with
`ch.uetlib.job.runner.instance.name` only where that isn't unique per instance.

## 3. Runners

A `JobRunner` **[library interface, library-provided implementations only today]** actually executes a claimed row.
There are exactly two `JobRunnerKey`s:

| Key                 | Runner (on the claiming/primary instance)                                               | Runner (where the job actually runs)              |
|---------------------|-----------------------------------------------------------------------------------------|---------------------------------------------------|
| `CURRENT`           | `CurrentJobRunner` - runs in-process on a per-`RUNNER_GROUP` thread pool                | same instance, same JVM                           |
| `DEDICATED_K8S_JOB` | `KubernetesJobRunner` - creates a new Kubernetes `Job` resource and returns immediately | the new pod, via `DedicatedJobRunner` (see below) |

- **`CurrentJobRunner`** **[library]**: runs `job.run(...)` on a thread from the job's
  `RUNNER_GROUP` pool (project-configurable via that property; jobs sharing a group share a pool and its capacity).
- **`KubernetesJobRunner`** **[library]**: doesn't run the job itself - it submits a K8s `Job`
  spec (built from `Job`/`KubernetesJob` **[project can customize the pod spec via]** properties like
  `K8S_REQUESTS_CPU`, `K8S_POD_LABELS`, ...) and returns. The actual execution happens in that new pod.
- **`DedicatedJobRunner`** **[library]**: active only under the `job-run` Spring profile, i.e. inside a pod started via
  `--job --run <id>`. Runs the job on its own single thread. This is what actually executes work dispatched by
  `KubernetesJobRunner` above - two different classes for the two sides of the same K8s dispatch (primary instance
  submits; dedicated pod executes).

**Anything else** (a completely external system - a Python service, a different deployment entirely) is **not** a
`JobRunner` the library dispatches to. There is no third `JobRunnerKey`. See §4 for how a project builds this kind of
integration on top of what the library does provide.

## 4. Handoff: a job ending in `SCHEDULED`

Sometimes a job's own `run()` call shouldn't do the work itself - it should hand off to another runner (or another
system entirely) and return quickly. `JobRunState.SCHEDULED` is how a job expresses "not done, but not running here
either right now."

**[library]** `BaseStateJob<T>` is the built-in pattern for this within the two known runners:

1. **[project implements]** `getLatestState(ctx)` - fetch/compute the latest state.
2. **[project implements]** `processState(state, ctx)` - do the actual work.
3. **[project may override]** `runnerForProcessing(state)` - return a different `JobRunnerKey` than the one this run is
   currently on.

If `runnerForProcessing` returns a different key, `BaseStateJob.run()` **[library]** saves the computed state, calls
`JobService#queueReprocessingRun(...)` - which sends the run's *own* queue row back to `QUEUED` (still bound to the same
`t_job_run`) and points the run at the target runner

- and returns `SCHEDULED`. Re-using the row rather than inserting a second one is what keeps a run to exactly one queue
  row; a row is inserted only for a run that has none at all, i.e. one started outside the queue by a standalone `--job`
  process. A poller then claims that row on the right runner and re-enters `run()`, which resumes at
  `processState(...)`. The job run sits in `SCHEDULED` until *something* transitions it: the re-dispatched execution
  returning a terminal state, `KubernetesJobWatcher` observing the pod finish (§6), or a timeout (§7).

**Beyond `BaseStateJob`**: a project can also just return `SCHEDULED` directly from a plain `Job`
after kicking off arbitrary external work (e.g. an HTTP call to another service). The library will correctly treat the
run as still-in-progress (§5-§7 all apply), but **[project's responsibility]**
is then to eventually transition it out of `SCHEDULED` itself - there's no library-provided watcher for a system it
doesn't know about. Do this via `JobDataService`/`JobService#updateJobRunState`
(in-process) or by exposing your own callback/webhook that does the same. Without this, the run relies entirely on the
timeout safety net below, which only knows the run has gone quiet or run long, not that anything went wrong.

## 5. Liveness of a running job

The queue itself carries **no per-run heartbeat** - a `RUNNING` row is trusted as long as its linked `t_job_run` is
still in a running state. Liveness of the run behind it is handled at the job-run level, unchanged from before the
queue:

- **`JobStateWatcher`** (every 5 min, `!job-run`) - for every running `t_job_run`, asks its runner (`CurrentJobRunner`
  via its `Future`, `KubernetesJobRunner` via the K8s API) to resolve the actual state. A run whose state can't be
  resolved and that has had no recent log activity is marked `UNFINISHED`; a resolvable terminal state is written
  through.
- **`JobTimeoutWatcher`** - duration-based kill, opt-in per job (see §7).

`JobQueueWatcherService#reconcileRunningQueuedRuns` (§7) then removes the now-stale `RUNNING` queue row once the run
behind it is no longer running.

> A dedicated, progress-aware queue-row heartbeat (`ThreadProgressCheckIn` etc.) lives on the
> `feature/queue-heartbeats` branch and is intended for a later iteration.

## 6. Kubernetes watching

**[library]** `KubernetesJobWatcher`, active only when `KubernetesJob#watchJob()` returns `true`
(the default; a project can opt a given job dispatch out). Runs on the primary instance that submitted the K8s job:

- Watches the K8s `Job` resource; on a real state change, updates `t_job_run`'s state directly (this is also what
  eventually moves a `BaseStateJob` handoff out of `SCHEDULED` on success/failure).
- Streams each pod's logs into this instance's own log output.

## 7. Watchers (reconciliation and timeouts)

Independent `@Scheduled` watchers, all **[library]**, all disabled on dedicated `job-run`
instances (they only make sense on a "primary" instance):

- **`JobQueueWatcherService#resetAbandonedClaims`** (every 15s) - a `CLAIMED` row whose claiming instance died before
  reaching `RUNNING` is reset to `QUEUED` after `CLAIM_TIMEOUT` (measured from the row's `updated_at`, which the claim
  bumped).
- **`JobQueueWatcherService#reconcileRunningQueuedRuns`** (every 15s) - a `RUNNING` row is removed once its linked run
  is no longer running (the only path that deletes a `RUNNING` row). It does not judge the run's liveness itself -
  that's `JobStateWatcher` (§5).
- **`JobQueueWatcherService#expireUnclaimableQueuedRuns`** (every minute) - a `QUEUED` row with no activity for
  `QUEUE_TIMEOUT` (default 30m) is marked `JobRunQueueState#CANCELLED` - given up on, because nothing currently running
  matches it (see "Version-pinned claiming and unclaimable runs"
  below). "Activity" is a claim attempt, however unsuccessful: a row still being actively retried keeps getting more
  time rather than being cancelled out from under it.
- **`JobQueueWatcherService#deleteOldCancelledQueuedRuns`** (hourly) - purges `CANCELLED` rows past
  `CANCELLED_QUEUE_RETENTION` (default 7 days). A second pass covers rows whose job this build doesn't know, which
  the per-job pass can never reach; those get the property's default retention.

  `JobRunQueueService` also cancels a row itself when nothing can ever claim it: its `versionTag` matches this build
  exactly, yet this build has no such job id. Not immediately, though - an instance builds its job registry at
  startup, so mid-rollout an instance about to be replaced can briefly not know a job its replacement just
  registered. It waits out `runner.unknown-job.timeout` (default 5m, far shorter than `QUEUE_TIMEOUT`) first. A row
  failing for any *other* reason is logged and skipped, so one bad row cannot block the rest of the queue.
- **`JobStateWatcher`** (every 5m) - job-run-level liveness, see §5.
- **`JobTimeoutWatcher`** - independent, duration-based, **opt-in per job**
  (`KILL_JOBS_RUN_TIMEOUT`/`RUN_TIME_LIMIT`, `KILL_JOBS_SCHEDULE_TIMEOUT`/
  `MAX_DURATION_IN_SCHEDULED_STATE`, both off by default). Kills a run that's simply taken too long. This is the only
  mechanism that bounds how long a `SCHEDULED` handoff (§4) may run when nothing else ever reports it finished.

**Version-pinned claiming and unclaimable runs [library, entirely].** Every queued row is tagged with the enqueueing
instance's own build version (`JobService#versionTag`); `JobRunQueueService`
only claims a row whose `versionTag` exactly matches its own (see §2) - never an older or newer instance, which matters
during a staggered/rolling release where multiple versions briefly coexist:
an older instance may not even know a newly-added job class, and a newer one may run different code for it than whatever
enqueued the run intended. A consequence of that strictness: if every instance of the enqueueing version disappears
before a row is claimed, nothing will ever claim it. Rather than leaving it `QUEUED` forever,
`expireUnclaimableQueuedRuns` above eventually marks it `CANCELLED`.

**`JobRunQueueState#CANCELLED` vs a `JobRun`'s own `CANCELLED` `JobRunState` [distinction worth being precise about].**
These mean different things and are otherwise unrelated: a queued run reaching `JobRunQueueState#CANCELLED` means it was
*never picked up*. For a run queued from scratch no `JobRun` ever existed for it and `jobRunId` stays `null`; a
`BaseStateJob` continuation (§4) is the exception - it carries the `jobRunId` of the run it was resuming, which is
then left stranded in `SCHEDULED`. A `JobRun` reaching
`JobRunState#CANCELLED` means an execution that was already underway was cancelled (a manual cancel, or its worker
interrupted). One is about a row never leaving the queue; the other is about an execution that was already underway.

**Acknowledging a cancelled queue row [project, via the status API].** `POST
/queue/{jobQueuedRunId}/acknowledge` deletes a single `CANCELLED` row immediately, without waiting for
`CANCELLED_QUEUE_RETENTION`; `POST /queue/acknowledge-cancelled` does the same for every
`CANCELLED` row at once. Both are no-ops (`success: false` / `0` acknowledged) if nothing matches - neither ever touches
a `QUEUED`/`CLAIMED`/`RUNNING` row. Distinct from acknowledging a `JobRun`
(`POST /run/{jobRunId}/acknowledge`), which is about a completed-but-problematic execution, not a queue row that never
ran at all.

## 8. Services quick reference

| Service                         | Role                                                                           |
|---------------------------------|--------------------------------------------------------------------------------|
| `TriggerCoordinatorService`     | Fires configured triggers, syncs fixed trigger definitions to the DB           |
| `JobRunQueueService`            | Polls/claims/dispatches `QUEUED` rows                                          |
| `JobExecutorService`            | Shared submit path: parallel-run guard, runner selection, `t_job_run` creation |
| `JobQueueWatcherService`        | Claim-staleness, running-row, and unclaimable-queued-row reconciliation (§7)   |
| `JobStateWatcher`               | Job-run-level liveness of running jobs (§5)                                    |
| `KubernetesJobWatcher`          | K8s job/pod state + log watching (§6)                                          |
| `JobTimeoutWatcher`             | Duration-based kill, opt-in (§7)                                               |
| `JobService` / `JobDataService` | Core orchestration / persistence, used by all of the above                     |

## 9. Where the status endpoints live

**[library]** The status REST API (`/v1/status/**`) and the `/v1/status/live/logs` log websocket run on the
**primary instance only** - the one carrying `job-primary-triggers` (§1). `UetlibJobStatusController`,
`JobControllerService` and `UetlibJobWebSocketConfig` share that profile because the controller drives
`TriggerCoordinatorService`, which exists only there.

- **Route `/v1/status` at that instance**, not across the deployment; other pods will 404.
- **Dispatch is unaffected** - every instance still polls the queue and runs jobs (§2). Losing the primary instance
  costs the API and new trigger firings, not runs already queued.

## What the library mandates vs. what a project defines

**Library-provided, not something a project reimplements:**

- The queue table, dispatch loop, claim/reconciliation machinery.
- `CurrentJobRunner`, `DedicatedJobRunner`, `KubernetesJobRunner`.
- The `--job` CLI entry point and `job-run` profile wiring.
- The status REST API (`/v1/status/...`) for queue/run inspection and control, and the
  `/v1/status/live/logs` log websocket - on the primary instance only, see §9.

**Project-defined, required:**

- `Job` implementations (`run()`, at minimum).

**Project-defined, optional, with library defaults:**

- `ch.uetlib.job.runner.instance.name` (claim identifier; defaults to `HOSTNAME`).
- Triggers (`Job#triggersConfig()`), taints, priority, runner group, all timeout properties.
- For Kubernetes jobs: pod spec details (`K8S_*` properties), `KubernetesJob#watchJob()`.
- For state-driven jobs: `BaseStateJob` subclasses, `runnerForProcessing()`.
