package ch.uetlib.job.model.queue;

/** Lifecycle of a {@code t_job_run_queue} row: {@code QUEUED -> CLAIMED -> RUNNING}, then gone. */
public enum JobRunQueueState {
    /** Waiting to be picked up. */
    QUEUED,

    /** An instance won the claim, about to submit; back to {@link #QUEUED} if that fails. */
    CLAIMED,

    /** Submitted, linked to a {@code t_job_run}, kept alive by a heartbeat. */
    RUNNING,

    /** Terminal: sat {@link #QUEUED} past {@code QUEUE_TIMEOUT}, nothing able to claim it. */
    CANCELLED,
    ;
}
