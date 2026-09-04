package ch.uetlib.job.model.queue;

/** Lifecycle of a {@code t_job_run_queue} row: {@code QUEUED -> CLAIMED -> RUNNING}, then gone. */
public enum JobRunQueueState {
    /** Waiting to be picked up. */
    QUEUED,

    /** An instance won the claim, about to submit; back to {@link #QUEUED} if that fails. */
    CLAIMED,

    /** Submitted, linked to a {@code t_job_run}. */
    RUNNING,

    /**
     * Terminal: sat {@link #QUEUED} past {@code QUEUE_TIMEOUT}. Kept as the only trace of a run
     * that never happened, until acknowledged or purged after {@code CANCELLED_QUEUE_RETENTION}.
     */
    CANCELLED,
    ;
}
