package ch.ubique.backend.job.model.triggers;

public enum FileChangeEvent {
    ENTRY_CREATED,
    ENTRY_MODIFIED,
    ENTRY_DELETED,
    /** A special event to indicate that events may have been lost or discarded. */
    OVERFLOW
}
