package ch.uetlib.job.model.queue;

public enum JobRunQueueState {
    QUEUED,
    CLAIMED,
    RUNNING,
    CANCELLED,
    ;
}
