package ch.ubique.backend.job.model;

import ch.ubique.backend.job.model.triggers.FileChangeEvent;
import ch.ubique.backend.job.model.triggers.FileTriggerMatchResult;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.nio.file.Path;
import java.nio.file.WatchEvent;

/**
 * @param watchedPath Path where the event was triggered
 * @param fileChangeEvent FileChangeEvent mapped from the original {@link WatchEvent}
 * @param fileName Path extracted from {@link #eventContext()} if it is of type {@link Path}
 * @param eventContext {@link Path} if {@link #fileChangeEvent()} is one of \{{@link
 *     FileChangeEvent#ENTRY_CREATED}, {@link FileChangeEvent#ENTRY_MODIFIED}, {@link
 *     FileChangeEvent#ENTRY_DELETED}\} or Object if {@link #fileChangeEvent()} is {@link
 *     FileChangeEvent#OVERFLOW}
 * @param fileTriggerMatchResult FileTriggerMatchResult the match result including all named capture
 *     groups of the supplied file matcher regex. A subset of {@link java.util.regex.MatchResult)}.
 */
public record FileTriggerContext(
        @NotNull Path watchedPath,
        @NotNull FileChangeEvent fileChangeEvent,
        @Nullable Path fileName,
        @NotNull Object eventContext,
        @NotNull FileTriggerMatchResult fileTriggerMatchResult)
        implements TriggerContext {

    public static FileTriggerContext from(
            final Path watchedPath,
            final WatchEvent<?> event,
            final FileTriggerMatchResult fileTriggerMatchResult) {
        final Path fileName;
        if (event.context() instanceof final Path eventContextPath) {
            fileName = eventContextPath;
        } else {
            fileName = null;
        }
        return new FileTriggerContext(
                watchedPath,
                FileChangeEvent.fromWatchEventKind(event.kind()),
                fileName,
                event.context(),
                fileTriggerMatchResult);
    }
}
