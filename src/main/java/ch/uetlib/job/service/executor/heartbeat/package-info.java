/**
 * Liveness tracking for {@code RUNNING} queue rows: a {@link
 * ch.uetlib.job.service.executor.heartbeat.RunHeartbeat} refreshes {@code
 * t_job_run_queue.heartbeat_at} on a timer for as long as the run is actually progressing; once it
 * goes stale a watcher marks the run {@code UNFINISHED}.
 *
 * <p>"Progressing" is checked, not assumed: {@code ThreadProgressCheckIn} watches the worker
 * thread's CPU while {@code job.run(...)} runs locally, {@code ScheduledElsewhereCheckIn} watches
 * run state once work is handed off (K8s pod, external service). A job that only waits on its own
 * child threads must call {@link
 * ch.uetlib.job.service.JobService#touchRunningHeartbeat} itself.
 */
@NullMarked
package ch.uetlib.job.service.executor.heartbeat;

import org.jspecify.annotations.NullMarked;
