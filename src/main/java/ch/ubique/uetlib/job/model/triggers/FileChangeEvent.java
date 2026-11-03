package ch.ubique.uetlib.job.model.triggers;

import com.den_4.inotify_java.InotifyEvent;
import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;

public enum FileChangeEvent {
    ENTRY_CREATED,
    ENTRY_MODIFIED,
    ENTRY_DELETED,
    ENTRY_MOVED_TO,
    ENTRY_WRITE_CLOSE,
    /** A special eventContext to indicate that events may have been lost or discarded. */
    OVERFLOW,
    @JsonEnumDefaultValue
    UNKNOWN;

    public static FileChangeEvent fromInotifyEvent(final InotifyEvent event) {
        if (event.isCreate()) {
            return FileChangeEvent.ENTRY_CREATED;
        }
        if (event.isCloseWrite()) {
            return FileChangeEvent.ENTRY_WRITE_CLOSE;
        }
        if (event.isMovedTo()) {
            return FileChangeEvent.ENTRY_MOVED_TO;
        }
        if (event.isModify()) {
            return FileChangeEvent.ENTRY_MODIFIED;
        }
        if (event.isDelete()) {
            return FileChangeEvent.ENTRY_DELETED;
        }
        if (event.isOverflowed()) {
            return FileChangeEvent.OVERFLOW;
        }
        // We are listening to all inotify events, so we also get not explicitly mapped ones.
        return FileChangeEvent.UNKNOWN;
    }

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
