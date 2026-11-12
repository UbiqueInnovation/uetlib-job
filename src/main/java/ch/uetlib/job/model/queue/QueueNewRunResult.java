package ch.uetlib.job.model.queue;

import ch.uetlib.job.model.JobQueuedRun;

/**
 * @param run the newly queued run, or the waiting or running one kept instead (based on {@link
 *     ch.uetlib.job.model.properties.enums.JobQueueBehavior})
 * @param queued whether {@code run} was newly queued
 */
public record QueueNewRunResult(JobQueuedRun run, boolean queued) {

    public String notQueuedReason() {
        return "Not queued: QUEUE_BEHAVIOR keeps queue entry %d, which is already %s."
                .formatted(run.jobQueuedRunId(), run.queueState().name().toLowerCase());
    }
}
