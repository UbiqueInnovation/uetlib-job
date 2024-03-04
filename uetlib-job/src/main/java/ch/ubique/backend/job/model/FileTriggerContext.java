package ch.ubique.backend.job.model;

import ch.ubique.backend.job.model.triggers.FileChangeEvent;

import jakarta.validation.constraints.NotNull;

import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.text.MessageFormat;
import java.util.regex.MatchResult;

/**
 * @param watchedPath Path where the event was triggered
 * @param fileChangeEvent FileChangeEvent mapped from the original {@link WatchEvent#kind()}
 * @param fileName Path from {@link WatchEvent#context()}
 * @param matchResult MatchResult the match result of the supplied file matcher regex
 */
public record FileTriggerContext(
        @NotNull Path watchedPath,
        @NotNull FileChangeEvent fileChangeEvent,
        @NotNull Path fileName,
        @NotNull MatchResult matchResult)
        implements TriggerContext {

    public static FileTriggerContext from(
            final Path watchedPath, final WatchEvent<?> event, final MatchResult matchResult) {
        final Path fileName;
        if (event.context() instanceof final Path eventContextPath) {
            fileName = eventContextPath;
        } else {
            throw new IllegalStateException(
                    MessageFormat.format(
                            "event.context() expected to be of type Path and not null, this should"
                                    + " have been caught in the FileTriggerService. Got: {0}",
                            event.context()));
        }
        return new FileTriggerContext(
                watchedPath,
                FileChangeEvent.fromWatchEventKind(event.kind()),
                fileName,
                matchResult);
    }
}
