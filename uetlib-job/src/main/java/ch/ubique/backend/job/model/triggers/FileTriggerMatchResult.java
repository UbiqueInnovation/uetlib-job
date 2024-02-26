package ch.ubique.backend.job.model.triggers;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record FileTriggerMatchResult(
        @NotNull List<String> groups, @NotNull Map<String, String> namedGroups) {

    public static FileTriggerMatchResult from(final MatchResult matchResult) {
        final var groups =
                IntStream.rangeClosed(0, matchResult.groupCount())
                        .mapToObj(matchResult::group)
                        .toList();
        final var namedGroups =
                matchResult.namedGroups().entrySet().stream()
                        .collect(
                                Collectors.toMap(Map.Entry::getKey, e -> groups.get(e.getValue())));
        return new FileTriggerMatchResult(groups, namedGroups);
    }

    public String get(final int group) {
        return groups.get(group);
    }

    public String get(final String group) {
        return namedGroups.get(group);
    }
}
