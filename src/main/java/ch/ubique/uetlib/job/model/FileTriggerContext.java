package ch.ubique.uetlib.job.model;

import ch.ubique.uetlib.job.model.regex.SerializableMatchResult;
import ch.ubique.uetlib.job.model.triggers.FileChangeEvent;
import ch.ubique.uetlib.job.service.triggers.files.FileWatcherEvent;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.regex.Matcher;

/**
 * @param watchedPath Path where the event was triggered
 * @param fileChangeEvent FileChangeEvent mapped from the original {@link WatchEvent#kind()}
 * @param fileName String from {@link WatchEvent#context()}
 * @param matchResult MatchResult the match result of the supplied file matcher regex
 */
public record FileTriggerContext(
        @JsonProperty(required = true) @NotNull Path watchedPath,
        @JsonProperty(required = true) @NotNull FileChangeEvent fileChangeEvent,
        @JsonProperty(required = true) @NotNull String fileName,
        @JsonProperty(required = true) @NotNull @Valid SerializableMatchResult matchResult)
        implements TriggerContext {

    public static FileTriggerContext from(final FileWatcherEvent event, final Matcher fileMatcher) {
        return new FileTriggerContext(
                event.watchedPath(),
                event.type(),
                event.filePath().toString(),
                SerializableMatchResult.fromMatcher(fileMatcher));
    }
}
