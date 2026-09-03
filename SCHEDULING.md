# Scheduling

How a job goes from "something wants this to run" to "it ran, and we know it ran" - dispatch,
runners, handoff, liveness detection, and who's responsible for what.

Throughout this document: **[library]** marks something `uetlib-job` provides and manages for you.
**[project]** marks something a consuming project defines or implements itself.

## Concepts

- **Job** **[project implements, library defines the interface]** - a class implementing `Job`
  (`run(JobRunContext)`, plus optional `propertiesConfig()`/`triggersConfig()`/`supportsForceRun()`).
  Discovered via `@RegisteredJob` + `@EnableJobs` classpath scanning.
- **JobRun** **[library]** - one execution attempt of a job, `t_job_run`. Append-only history;
  state only ever moves forward (`BUSY`/`SCHEDULED` → a terminal state).
- **Queue row** **[library]** - `t_job_run_queue`. A separate, mutable, re-claimable table
  representing "this job should run" / "this job is currently running", decoupled from the
  `t_job_run` history. States: `QUEUED` → `CLAIMED` → `RUNNING`, then deleted.

## 1. Getting into the queue

Nothing runs a job directly. Everything that wants a job to run inserts a `QUEUED` row.

- **Triggers** **[project configures, library executes]** - `CronTrigger`, `FixedRateTrigger`,
  `PgListenTrigger`, `DatabaseSubmissionTrigger`, etc. A project declares triggers via
  `Job#triggersConfig()` or the status API; `TriggerCoordinatorService` **[library]** owns firing
  them and calls `JobService#queueNewRun(...)` on fire. This is the only thing that inserts
  `QUEUED` rows on a schedule - a project never calls the queue API directly for scheduled work.
- **Manual start / replay** **[project triggers via API, library executes]** -
  `POST /job/{id}/start`, `/job/start_all`, `/job/{id}/replay_run[/{jobRunId}]`. Same
  `queueNewRun(...)` path as triggers.
- **`--job <class> [--run <id>]` CLI** **[library]** - bypasses the queue entirely. Used by
  dedicated Kubernetes pods (see §3) and available for local/manual single-job runs. Not
  something a project's own code calls; it's a process entry point (`JobApplication`).

A queue row also carries a priority. `t_job_run_queue.priority` is seeded from the job's `PRIORITY`
property when the row is queued; dequeue order is `COALESCE(row.priority, 0) DESC, createdAt ASC`.
Reorder a still-queued row via `PUT /queue/{jobQueuedRunId}/priority` **[library, project calls
it]**; `RUNNING` rows are never reordered.

## 2. Dispatch

**[library, entirely]** - `JobRunQueueService`, on every instance (except dedicated `job-run`
pods), every 5s (`@Scheduled`) plus a Postgres `LISTEN`/`NOTIFY` fast path for lower latency:

1. Page through `QUEUED` rows, highest priority / oldest first.
2. For each, check whether *this instance* matches: its own build version must **exactly** equal
   the row's `versionTag` (see "Version-pinned claiming" below); the job's `RUNNER` property then
   decides which of the two paths applies (see "Runner kind vs. taints" below) - `CURRENT` requires
   this instance's own taints (`ch.uetlib.job.runner.instance.taints`) to be a superset of
   the row's required taints (`JOB_RUN_TAINTS` job property); `DEDICATED_K8S_JOB` requires the same
   taint check *plus* a dedicated-runner bean available on this instance.
3. On a match: atomically claim it (`QUEUED` → `CLAIMED`, tagged with this instance's claim
   identifier - see end of §2), then dispatch to the matching `JobRunner`.
4. On successful submission: `CLAIMED` → `RUNNING`, linked to the new `t_job_run` row.
5. On failure, or if a `CLAIMED` row's claiming instance died before ever reaching `RUNNING`
   (`CLAIM_TIMEOUT`, default 2m): released back to `QUEUED` for another instance to retry.

**Runner kind vs. taints [library, entirely].** These are two different, orthogonal decisions, both
made by `JobRunQueueService#claimIfMatchesThisRunner`, in this order:

1. *Which kind of runner does this job want* - `CURRENT` (in-process, the default) or
   `DEDICATED_K8S_JOB` (its own K8s pod)? Decided **solely** by the job's own `RUNNER` property
   (cached per job, same 30s-refresh pattern as the timeout properties in §7) - never by taints.
2. *Given that kind, does this specific instance qualify* - taint subset-matching, as described
   above (§1's `JOB_RUN_TAINTS`/`ch.uetlib.job.runner.instance.taints`), plus (for
   `DEDICATED_K8S_JOB` only) whether this instance actually has a dedicated-runner bean at all.

A `DEDICATED_K8S_JOB` row with no `JOB_RUN_TAINTS` of its own - the common case, since a project
sets `RUNNER`, not a synthetic taint - must still only ever be claimed via the dedicated path. Taint
subset-matching alone can't tell the two paths apart: the empty set is trivially a subset of *any*
taint set, so a row with no taints would otherwise satisfy the plain path too, and (being checked
first) would end up dispatched to a `CurrentJobRunner` instance instead of K8s. This was a real bug,
fixed by making the `RUNNER` property decide which path even applies before either taint check runs,
rather than trying to distinguish the two paths by taint content.

**Version-pinned claiming [library, entirely].** Every queued row is tagged with the enqueueing
instance's own build version (`JobService#versionTag`, from `BuildProperties`); `JobRunQueueService`
only ever claims a row whose `versionTag` exactly matches its own. This matters during a staggered
or rolling release, where instances running different versions briefly coexist: without this check,
an older instance could claim a row for a job class it doesn't even have yet, or a newer instance
could run a row using different (possibly incompatible) code than whatever enqueued it intended.
Only the exact version that enqueued a run may execute it - never an older or newer one.

One consequence worth knowing: if every instance of the version that enqueued a row disappears
before it's claimed (e.g. a very short-lived old version during a fast rollout), that row is
version-pinned to nobody. It doesn't stay `QUEUED` forever, though - `expireUnclaimableQueuedRuns`
(§7) eventually marks it `CANCELLED` once it's had no activity for `QUEUE_TIMEOUT`.

**[project]** only participates by setting `JOB_RUN_TAINTS` on a job (to pin it to specific
instances). Each instance's claim identifier defaults to the `HOSTNAME` env var (the pod name on
Kubernetes); override it with `ch.uetlib.job.runner.instance.name` only where that isn't
unique per instance.

## 3. Runners

A `JobRunner` **[library interface, library-provided implementations only today]** actually
executes a claimed row. There are exactly two `JobRunnerKey`s:

| Key                | Runner (on the claiming/primary instance)      | Runner (where the job actually runs)          |
|---------------------|------------------------------------------------|------------------------------------------------|
| `CURRENT`           | `CurrentJobRunner` - runs in-process on a per-`RUNNER_GROUP` thread pool | same instance, same JVM |
| `DEDICATED_K8S_JOB` | `KubernetesJobRunner` - creates a new Kubernetes `Job` resource and returns immediately | the new pod, via `DedicatedJobRunner` (see below) |

- **`CurrentJobRunner`** **[library]**: runs `job.run(...)` on a thread from the job's
  `RUNNER_GROUP` pool (project-configurable via that property; jobs sharing a group share a pool
  and its capacity).
- **`KubernetesJobRunner`** **[library]**: doesn't run the job itself - it submits a K8s `Job`
  spec (built from `Job`/`KubernetesJob` **[project can customize the pod spec via]** properties
  like `K8S_REQUESTS_CPU`, `K8S_POD_LABELS`, ...) and returns. The actual execution happens in
  that new pod.
- **`DedicatedJobRunner`** **[library]**: active only under the `job-run` Spring profile, i.e.
  inside a pod started via `--job --run <id>`. Runs the job on its own single thread. This is what
  actually executes work dispatched by `KubernetesJobRunner` above - two different classes for the
  two sides of the same K8s dispatch (primary instance submits; dedicated pod executes).

**Anything else** (a completely external system - a Python service, a different deployment
entirely) is **not** a `JobRunner` the library dispatches to. There is no third `JobRunnerKey`.
See §4 for how a project builds this kind of integration on top of what the library does provide.

## 4. Handoff: a job ending in `SCHEDULED`

Sometimes a job's own `run()` call shouldn't do the work itself - it should hand off to another
runner (or another system entirely) and return quickly. `JobRunState.SCHEDULED` is how a job
expresses "not done, but not running here either right now."

**[library]** `BaseStateJob<T>` is the built-in pattern for this within the two known runners:

1. **[project implements]** `getLatestState(ctx)` - fetch/compute the latest state.
2. **[project implements]** `processState(state, ctx)` - do the actual work.
3. **[project may override]** `runnerForProcessing(state)` - return a different `JobRunnerKey` than
   the one this run is currently on.

If `runnerForProcessing` returns a different key, `BaseStateJob.run()` **[library]** calls
`JobExecutorService#submit(jobClass, arguments, sameJobRunId, thatKey)` - same `t_job_run` row,
different runner - and returns `SCHEDULED` (or `SCHEDULING_FAILED` if that submission itself
failed, e.g. queue full). The job run then sits in `SCHEDULED` until *something* transitions it:
the newly-dispatched execution eventually calling `processState(...)` and returning a terminal
state, `KubernetesJobWatcher` observing the pod finish (§6), or a timeout (§7).

**Beyond `BaseStateJob`**: a project can also just return `SCHEDULED` directly from a plain `Job`
after kicking off arbitrary external work (e.g. an HTTP call to another service). The library will
correctly treat the run as still-in-progress (§5-§7 all apply), but **[project's responsibility]**
is then to eventually transition it out of `SCHEDULED` itself - there's no library-provided watcher
for a system it doesn't know about. Do this via `JobDataService`/`JobService#updateJobRunState`
(in-process) or by exposing your own callback/webhook that does the same. Without this, the run
relies entirely on the heartbeat/timeout safety nets below, which don't know anything went wrong,
only that it's gone quiet or run long.

## 5. Heartbeats

**[library, entirely]** Package `ch.uetlib.job.service.executor.heartbeat`. Proves a
`RUNNING` queue row is still alive, not just that its last-known state hasn't changed.

`RunHeartbeat` wraps every `CurrentJobRunner`/`DedicatedJobRunner` execution (`JobRunnerHelper`)
and switches strategy as the run moves through phases:

- **While `job.run(...)` executes synchronously** (`ThreadProgressCheckIn`): every 15s, checks the
  actual worker thread is alive *and* has consumed CPU time since the last check. A thread stuck
  forever on a lock/wait/unbounded call looks alive but makes no progress, so its heartbeat isn't
  touched - a plain liveness check alone wouldn't catch this. If the thread is found dead without
  having gone through normal completion (e.g. its thread pool was torn down), the run is marked
  `CANCELLED` immediately - a known cause, not left for the timeout-based reconciliation below.
- **Once `job.run(...)` returns `SCHEDULED`** (`ScheduledElsewhereCheckIn`): the local thread's
  progress is meaningless now (it already returned), so this instead polls the run's own DB state
  every 15s and touches the heartbeat as long as it's still `SCHEDULED`. Stops the moment anything
  moves it to a different state.
- **Kubernetes-dispatched jobs** get heartbeat coverage from `KubernetesJobWatcher` instead (§6),
  not from `RunHeartbeat` on the primary instance - the primary never runs `job.run()` for these.

`HEARTBEAT_TIMEOUT` **[project can override per job]** (default 1 minute) - how stale a `RUNNING`
row's heartbeat may get before `JobQueueWatcherService` (§7) treats it as abandoned.

**`HeartbeatRegistry`** owns one small, shared scheduler pool - not a dedicated thread per running
job or watched pod - and tracks every active `RunHeartbeat` by `jobRunId`. Anything that observes a
run is no longer active (normal completion, `JobQueueWatcherService` marking it `UNFINISHED`, ...)
can call `HeartbeatRegistry#stop(jobRunId)` to cancel it immediately on this instance, rather than
waiting for that heartbeat's own next tick to notice - this is what closes the one gap
`ThreadProgressCheckIn` can't close on its own: a worker thread that's alive but genuinely,
permanently stuck (a real deadlock) never dies on its own, so nothing local would otherwise ever
tell its heartbeat to stop, even after `JobQueueWatcherService` has already given up on it.
`KubernetesJobWatcher`'s per-pod quiet-log watchdog (§6) shares the same pool for the same reason.

**Virtual threads and fan-out jobs [library guarantee + project responsibility].** `job.run(...)`
always executes on a platform thread - `CurrentJobRunner` and `DedicatedJobRunner` both build their
executors with plain (non-virtual) thread factories, deliberately, because per-thread CPU time
(what `ThreadProgressCheckIn` measures) is not supported for virtual threads; `CheckInState` falls
back to `isAlive()`-only if it ever finds itself watching one anyway, so this degrades safely rather
than silently. That guarantee only covers the *main* thread. It says nothing about a job that spawns
its own child threads - e.g. a bunch of virtual threads doing long-running sub-tasks in parallel -
and then has its main thread simply wait (`Future#get`, `invokeAll`, a `StructuredTaskScope` join).
The main thread makes no CPU progress of its own for the whole wait, and nothing here polls the
child threads' progress, so such a job looks identical to a stuck one from `ThreadProgressCheckIn`'s
point of view. A job shaped like this should call `JobService#touchRunningHeartbeat(jobRunId)`
itself during the wait (e.g. once per completed sub-task) to keep its heartbeat honest - the same
escape hatch `ScheduledElsewhereCheckIn` and `KubernetesJobWatcher` already rely on for liveness they
can't observe locally.

**Observability [library].** `GET /v1/status/queue` (`UetlibJobStatusController`/
`JobControllerService`) already returns each `RUNNING` row's raw `lastHeartbeat`; it also now returns
a computed `heartbeatStale` boolean alongside it - the exact same verdict
`JobQueueWatcherService#reconcileRunningQueuedRuns` will act on, using that job's own
`HEARTBEAT_TIMEOUT` override (`JobQueuedRun#isHeartbeatStale`, the single shared definition of
"stale" both use). `null` for anything not `RUNNING` (queued/claimed rows have no heartbeat to judge
yet). This lets a dashboard or alert flag an at-risk run before the next reconciliation cycle marks
it `UNFINISHED`, without reimplementing or guessing at the per-job timeout itself.

## 6. Kubernetes watching

**[library]** `KubernetesJobWatcher`, active only when `KubernetesJob#watchJob()` returns `true`
(the default; a project can opt a given job dispatch out). Runs on the primary instance that
submitted the K8s job, bridging the gap until the pod's own `RunHeartbeat` takes over:

- Watches the K8s `Job` resource; on every observed event where the job hasn't failed, touches the
  heartbeat. On a real state change, updates `t_job_run`'s state directly (this is also what
  eventually moves a `BaseStateJob` handoff out of `SCHEDULED` on success/failure).
- Streams each pod's logs; each real log line also touches the heartbeat (debounced to once per
  15s so a chatty job doesn't turn into a heartbeat write per line).
- If logs go quiet for 15s, falls back to actively polling the pod's container status: if it's
  still present with no new restarts, the heartbeat is touched anyway (a legitimately-quiet job
  isn't penalized); if it's restarted or gone, the heartbeat is *not* touched, and reconciliation
  (§7) takes it from there.

This closes the two gaps a pure-heartbeat-from-inside-the-pod approach has: the cold-start window
before the pod's own JVM is even up, and pods that go quiet without producing log output.

## 7. Watchers (reconciliation and timeouts)

Five independent `@Scheduled` watchers, all **[library]**, all disabled on dedicated `job-run`
instances (they only make sense on a "primary" instance):

- **`JobQueueWatcherService#resetAbandonedClaims`** (every 15s) - a `CLAIMED` row whose claiming
  instance died before reaching `RUNNING` is reset to `QUEUED` after `CLAIM_TIMEOUT`.
- **`JobQueueWatcherService#reconcileRunningQueuedRuns`** (every 15s) - for every `RUNNING` row:
  removed once its linked run is no longer running; if the heartbeat has gone stale
  (`HEARTBEAT_TIMEOUT`), the run is marked `UNFINISHED` (cause unknown - could be a dead runner, a
  stuck one, or a network partition; distinct from the `CANCELLED` a runner marks when it directly
  observes its own worker died, see §5) and the row removed. Otherwise left alone - it does *not*
  refresh the heartbeat itself; only the runner proving its own liveness does that.
- **`JobQueueWatcherService#expireUnclaimableQueuedRuns`** (every minute) - a `QUEUED` row with no
  activity for `QUEUE_TIMEOUT` (default 30m) is marked `JobRunQueueState#CANCELLED` - given up on,
  because nothing currently running matches it (see "Version-pinned claiming and unclaimable runs"
  below). "Activity" is a claim attempt, however unsuccessful (same null-safe idiom as
  `resetAbandonedClaims`): a row still being actively retried keeps getting more time rather than
  being cancelled out from under it.
- **`JobQueueWatcherService#deleteOldCancelledQueuedRuns`** (hourly) - purges `CANCELLED` rows past
  `CANCELLED_QUEUE_RETENTION` (default 7 days). Cleanup only, hence the coarse cadence.
- **`JobTimeoutWatcher`** - independent, duration-based, **opt-in per job**
  (`KILL_JOBS_RUN_TIMEOUT`/`RUN_TIME_LIMIT`, `KILL_JOBS_SCHEDULE_TIMEOUT`/
  `MAX_DURATION_IN_SCHEDULED_STATE`, both off by default). Kills a run that's simply taken too
  long, regardless of whether it's still heartbeating - a legitimately-alive-but-too-slow run is a
  different problem than an abandoned one. This is the only mechanism that bounds how long a
  `SCHEDULED` handoff (§4) may run when nothing else ever reports it finished; the heartbeat
  watchers above deliberately don't duplicate this.

**Version-pinned claiming and unclaimable runs [library, entirely].** Every queued row is tagged
with the enqueueing instance's own build version (`JobService#versionTag`); `JobRunQueueService`
only claims a row whose `versionTag` exactly matches its own (see §2) - never an older or newer
instance, which matters during a staggered/rolling release where multiple versions briefly coexist:
an older instance may not even know a newly-added job class, and a newer one may run different code
for it than whatever enqueued the run intended. A consequence of that strictness: if every instance
of the enqueueing version disappears before a row is claimed, nothing will ever claim it. Rather than
leaving it `QUEUED` forever, `expireUnclaimableQueuedRuns` above eventually marks it `CANCELLED`.

**`JobRunQueueState#CANCELLED` vs a `JobRun`'s own `CANCELLED` `JobRunState` [distinction worth
being precise about].** These mean different things and are otherwise unrelated: a queued run
reaching `JobRunQueueState#CANCELLED` means it was *never picked up* - no `JobRun` ever existed for
it, `jobRunId` stays `null`. A `JobRun` reaching `JobRunState#CANCELLED` means a worker thread that
*was* executing it died without completing normally (§5, `ThreadProgressCheckIn`). One is about a row
never leaving the queue; the other is about an execution that was already underway.

**Acknowledging a cancelled queue row [project, via the status API].** `POST
/queue/{jobQueuedRunId}/acknowledge` deletes a single `CANCELLED` row immediately, without waiting
for `CANCELLED_QUEUE_RETENTION`; `POST /queue/acknowledge-cancelled` does the same for every
`CANCELLED` row at once. Both are no-ops (`success: false` / `0` acknowledged) if nothing matches -
neither ever touches a `QUEUED`/`CLAIMED`/`RUNNING` row. Distinct from acknowledging a `JobRun`
(`POST /run/{jobRunId}/acknowledge`), which is about a completed-but-problematic execution, not a
queue row that never ran at all.

## 8. Services quick reference

| Service | Role |
|---|---|
| `TriggerCoordinatorService` | Fires configured triggers, syncs fixed trigger definitions to the DB |
| `JobRunQueueService` | Polls/claims/dispatches `QUEUED` rows |
| `JobExecutorService` | Shared submit path: parallel-run guard, runner selection, `t_job_run` creation |
| `JobQueueWatcherService` | Claim-staleness, running-row, and unclaimable-queued-row reconciliation (§7) |
| `KubernetesJobWatcher` | K8s job/pod state + log watching, heartbeat bridging (§6) |
| `JobTimeoutWatcher` | Duration-based kill, opt-in (§7) |
| `JobService` / `JobDataService` | Core orchestration / persistence, used by all of the above |

## What the library mandates vs. what a project defines

**Library-provided, not something a project reimplements:**
- The queue table, dispatch loop, claim/heartbeat/reconciliation machinery.
- `CurrentJobRunner`, `DedicatedJobRunner`, `KubernetesJobRunner` and their heartbeat coverage.
- The `--job` CLI entry point and `job-run` profile wiring.
- The status REST API (`/v1/status/...`) for queue/run inspection and control.

**Project-defined, required:**
- `Job` implementations (`run()`, at minimum).

**Project-defined, optional, with library defaults:**
- `ch.uetlib.job.runner.instance.name` (claim identifier; defaults to `HOSTNAME`).
- Triggers (`Job#triggersConfig()`), taints, priority, runner group, all timeout properties.
- For Kubernetes jobs: pod spec details (`K8S_*` properties), `KubernetesJob#watchJob()`.
- For state-driven jobs: `BaseStateJob` subclasses, `runnerForProcessing()`.

**Project-defined, entirely, if wanted:**
- Any integration with a system the library has no concept of (a non-Kubernetes external
  scheduler, a different deployment reporting back via webhook, ...). The library's contribution
  here is exactly `JobRunState.SCHEDULED` plus the generic (non-K8s-specific) heartbeat/timeout
  safety nets in §5 and §7 - not a runner, not a watcher, not a callback mechanism.
