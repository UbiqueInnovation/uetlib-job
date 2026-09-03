package ch.uetlib.job.model.properties.enums;

/** What happens when a run is queued for a job that already has one waiting (or running). */
public enum JobQueueBehavior {
    /** Keep at most one waiting run: if one is already queued/claimed, drop the new one. */
    DISCARD_NEW,

    /** Keep at most one waiting run: delete the queued/claimed one, then queue the new one. */
    REPLACE_RUN,

    /** No limit - every request adds another queued run. */
    ALLOW_MULTIPLE,

    /** Queue only when the job is completely idle: no run queued, claimed, or running. */
    DISCARD_IF_RUNNING
}
