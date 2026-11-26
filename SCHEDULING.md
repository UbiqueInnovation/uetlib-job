# Scheduling

How a job goes from "something wants this to run" to "it ran": the queue, dispatch, runners, handoff, and the
watchers that keep it consistent. **[library]** marks what `uetlib-job` provides; **[project]** marks what a consuming
project defines.

## Concepts

- **Job** **[project]** - a class implementing `Job` (`run(JobRunContext)`, optionally `propertiesConfig()` /
  `triggersConfig()`), registered via `@RegisteredJob` + `@EnableJobs`.
- **JobRun** **[library]** - one execution attempt, `t_job_run`. Append-only; its state only moves forward.
- **Queue row** **[library]** - `t_job_run_queue`, "this should run" / "this is running", kept apart from the run
  history. `QUEUED` → `CLAIMED` → `RUNNING`, then deleted; `CANCELLED` if nothing claimed it in time (§7).
- **Daemon** **[library]** - a long-lived application instance that checks in to `t_job_daemon`. Dedicated
  `--job` processes skip daemon registration.

## 1. Getting into the queue

Nothing runs a job directly; everything goes through a queue row.

- **Triggers** **[project configures, library executes]** - `CronTrigger`, `FixedRateTrigger`, `PgListenTrigger`,
  `DatabaseSubmissionTrigger`, ..., declared via `Job#triggersConfig()` or the status API. `TriggerCoordinatorService`
  fires them on instances with the `job-primary-triggers` profile. The only thing that queues runs on a schedule.
- **Manual start / replay** **[project calls the API]** - `POST /job/{id}/start`, `/job/start_all`,
  `/job/{id}/replay_run[/{jobRunId}]` (§9).
- **`--job <class> [--run <id>]`** **[library]** - a dedicated process that executes exactly one run and exits (§3).
  It records its run as `RUNNING` in the queue before starting it; with `--run`, the dispatching instance already has.

**Exactly one primary [library].** Every instance with `job-primary-triggers` registers its triggers, but only the
holder of the *primary lease* queues runs from them. The lease is `t_job_daemon.primary_slot`, a `UNIQUE` column, so
the database rules out a second holder. Every fire checks the lease under a shared row lock, in the same transaction as
the insert - an instance that has lost the lease can never queue.

- Every instance checks in every 30s (`ch.uetlib.job.instance.check-in`). The newest live candidate takes the
  lease: from nobody, from a holder that stopped checking in (`instance.stale-after`, default 90s), or from one that
  started before it. A rollout thereby hands over without overlap or gap.
- On shutdown the holder hands the lease to the newest other live candidate.
- The holder purges rows of instances that stopped checking in (`instance.purge-after`, default 3× stale-after); an
  instance whose row is gone re-inserts it at its next check-in.
- `JobSinglePrimaryInstanceCheck` fails on every instance when no live instance holds the lease. It reports through the
  monitoring module, since `/v1/status` (§9) is gone exactly when this fires.

One schema serves exactly one application: `JobRegistrationService` deletes every `t_job` row whose class the starting
application lacks, and that cascades to runs, triggers and queue rows.

A queue row carries a priority, seeded from the job's `PRIORITY`; dequeue order is `runNow DESC, COALESCE(priority, 0)
DESC, createdAt ASC`. Reorder a still-queued row via `PUT /queue/{jobQueuedRunId}/priority`; without `priority`, it goes
back to the job's current `PRIORITY`.

**Run now.** `POST /queue/{jobQueuedRunId}/run-now` marks a fresh (not a `BaseStateJob` continuation), still-`QUEUED`
row of an enabled job whose `JOB_MANUAL_TRIGGER_MODE` is `NEW_RUN_ALLOWED`. It is dequeued before every other row and
may start next to a run of its job already under way - once, and only in that direction: while it runs, other runs of
the job wait as usual. It still needs a free thread, matching taints and its exact version (§2). The flag lives on the
queue row only; a `BaseStateJob` handoff (§4) drops it from its continuation.

## 2. Dispatch

**[library]** `JobRunQueueService`, on every instance, every 5s plus a Postgres `LISTEN`/`NOTIFY` fast path:

1. Page through `QUEUED` rows by priority, stopping once a row was claimed. A row it matches but cannot take yet (see
   3) or loses to another instance doesn't stop the scan; `max.pages.per.check` bounds it.
2. Match. The row's `versionTag` must **exactly** equal this instance's build version - a run is only ever executed by
   the version that queued it. The job's `RUNNER` property (never the taints) picks `CURRENT` or `DEDICATED_K8S_JOB`.
   Then this instance's taints (`ch.uetlib.job.runner.instance.taints`) must be a superset of the row's
   `JOB_RUN_TAINTS`, and the dedicated path also needs a dedicated runner on this instance.
3. Claim only a run that can start right away: a free thread in its `RUNNER_GROUP` (for `CURRENT`), its job enabled,
   and either `ALLOW_PARALLEL_RUNS` (or a run-now row, or a forced run it lets through) or no run of the job under way.
   A fresh run checks every run of the job, not just the latest; a continuation waits only while the latest run is
   another one under way. A dedicated `--job --run <id>` process does not check again. Otherwise the row stays
   `QUEUED`, marked as still wanted so `QUEUE_TIMEOUT` (§7) doesn't cancel it. The claim is atomic: `QUEUED` →
   `CLAIMED`, tagged with `ch.uetlib.job.runner.instance.name` (default `HOSTNAME`, the pod name).
4. `CLAIMED` → `RUNNING`, linked to the new `t_job_run` - *before* the runner gets the job, since a `BaseStateJob`
   handoff (§4) may re-queue that row right away.
5. A failed submission releases the claim; a claim whose instance died is reset after `CLAIM_TIMEOUT` (default 2m).

A row queued by a version that has since disappeared is claimable by nobody; it is marked `CANCELLED` after
`QUEUE_TIMEOUT` (§7).

## 3. Runners

| Key                 | On the claiming instance                                 | Where the job runs                                    |
|---------------------|----------------------------------------------------------|-------------------------------------------------------|
| `CURRENT`           | `CurrentJobRunner` - a thread of the `RUNNER_GROUP` pool | same JVM                                              |
| `DEDICATED_K8S_JOB` | `KubernetesJobRunner` - creates a K8s `Job` and returns  | a new pod (`--job --run <id>`), `DedicatedJobRunner`  |

A project customizes the pod via the `K8S_*` properties and `KubernetesJob`. There is no third runner; external systems
integrate via §4.

## 4. Handoff: a job ending in `SCHEDULED`

`SCHEDULED` means "not done, and not running here". **[library]** `BaseStateJob<T>`: **[project]** implements
`getLatestState` and `processState`, and may override `runnerForProcessing(state)`. If that returns another runner, the
state is saved and `JobService#queueReprocessingRun` sends the run's *own* queue row back to `QUEUED` (still bound to
its `t_job_run`); a poller on the target runner then resumes at `processState`. A run never has more than one queue row.

A plain `Job` may also return `SCHEDULED` after starting external work. Transitioning the run later
(`JobService#updateJobRunState`) is then **[project's responsibility]**; otherwise only the timeouts (§7) end it.

## 5. Liveness of a running job

A `RUNNING` queue row is trusted as long as its `t_job_run` is running. `JobStateWatcher` (every 5m) asks each run's
runner for its actual state and marks a run it cannot resolve, and that has gone quiet, `UNFINISHED`.
`JobQueueWatcherService#reconcileRunningQueuedRuns` (§7) then removes the queue row.

## 6. Kubernetes watching

`KubernetesJobWatcher` runs on the instance that submitted a K8s job (unless `KubernetesJob#watchJob()` is `false`):
it writes the job's state changes to `t_job_run` and streams the pod's logs.

## 7. Watchers (reconciliation and timeouts)

All `@Scheduled` **[library]**, on every instance (not on `--job` processes):

| Watcher                                               | Every | Does                                                                   |
|-------------------------------------------------------|-------|------------------------------------------------------------------------|
| `JobQueueWatcherService#resetAbandonedClaims`         | 15s   | `CLAIMED` longer than `CLAIM_TIMEOUT` → `QUEUED`                       |
| `JobQueueWatcherService#reconcileRunningQueuedRuns`   | 15s   | deletes a `RUNNING` row once its run is no longer running              |
| `JobQueueWatcherService#expireUnclaimableQueuedRuns`  | 1m    | `QUEUED` nothing could take for `QUEUE_TIMEOUT` (30m) → `CANCELLED`    |
| `JobQueueWatcherService#deleteOldCancelledQueuedRuns` | 1h    | purges `CANCELLED` rows past `CANCELLED_QUEUE_RETENTION` (7d)          |
| `JobStateWatcher`                                     | 5m    | run-level liveness (§5)                                                |
| `JobTimeoutWatcher`                                   | 5m    | opt-in kill after `RUN_TIME_LIMIT` / `MAX_DURATION_IN_SCHEDULED_STATE` |

A `CANCELLED` queue row was *never picked up* - unlike a `JobRun` in `CANCELLED`, which was stopped while under way. It
stays visible (`JobQueueCancelledRunsCheck` warns) until acknowledged (`POST /queue/{jobQueuedRunId}/acknowledge`,
`POST /queue/acknowledge-cancelled`) or purged. `JobRunQueueService` also cancels a row whose job its own version does
not know, after `runner.unknown-job.timeout` (5m).

## 8. Services quick reference

| Service                          | Role                                                                            |
|----------------------------------|---------------------------------------------------------------------------------|
| `TriggerCoordinatorService`      | Fires triggers (through the primary lease), syncs fixed trigger definitions     |
| `JobDaemonRegistrationService` | Check-ins, primary lease, purging stale instances (§1)                          |
| `JobRunQueueService`             | Polls, claims and dispatches `QUEUED` rows (§2)                                 |
| `JobExecutorService`             | Shared submit path: parallel-run guard, runner selection, `t_job_run` creation  |
| `JobQueueWatcherService`         | Queue reconciliation (§7)                                                       |
| `JobStateWatcher`                | Run-level liveness (§5)                                                         |
| `KubernetesJobWatcher`           | K8s job state and log watching (§6)                                             |
| `JobTimeoutWatcher`              | Opt-in duration-based kill (§7)                                                 |

## 9. Where the status endpoints live

The status API (`/v1/status/**`) and its log websocket run only on instances with `job-primary-triggers` - normally
one; route `/v1/status` there. Dispatch is unaffected: every instance polls the queue and runs jobs.

`GET /v1/status/instances` lists the live instances and the lease holder; every status response names the instance
that served it in `X-Job-Instance`.

## What the library mandates vs. what a project defines

- **Library:** the queue, dispatch, claiming and reconciliation; the instance registry and primary lease; the runners;
  the `--job` entry point; the status API.
- **Project, required:** `Job` implementations, and the `job-primary-triggers` profile on at least one instance.
- **Project, optional:** `runner.instance.name` (default `HOSTNAME`), `runner.instance.taints`, triggers, priority,
  runner group, timeouts, `K8S_*` pod settings, `BaseStateJob` subclasses.
