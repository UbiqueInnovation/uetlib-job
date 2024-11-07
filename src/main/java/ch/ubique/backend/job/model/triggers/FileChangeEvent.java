package ch.ubique.backend.job.model.triggers;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;

public enum FileChangeEvent {
    ENTRY_CREATED,
    ENTRY_MODIFIED,
    ENTRY_DELETED,
    /** A special eventContext to indicate that events may have been lost or discarded. */
    OVERFLOW,
    @JsonEnumDefaultValue
    UNKNOWN;

    public static FileChangeEvent fromWatchEventKind(final WatchEvent.Kind<?> kind) {
        if (kind.equals(StandardWatchEventKinds.ENTRY_CREATE)) {
            return FileChangeEvent.ENTRY_CREATED;
        }
        if (kind.equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
            return FileChangeEvent.ENTRY_MODIFIED;
        }
        if (kind.equals(StandardWatchEventKinds.ENTRY_DELETE)) {
            return FileChangeEvent.ENTRY_DELETED;
        }
        if (kind.equals(StandardWatchEventKinds.OVERFLOW)) {
            return FileChangeEvent.OVERFLOW;
        }
        throw new IllegalArgumentException("Got unknown eventContext kind " + kind);
    }
}
