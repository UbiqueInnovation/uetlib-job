package ch.ubique.backend.job.model.triggers;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.stream.IntStream;

public record FileTriggerMatchResult(
        @PositiveOrZero int groupCount,
        @NotNull List<String> groups,
        @NotNull Map<String, Integer> namedGroups) {

    public static FileTriggerMatchResult from(final MatchResult matchResult) {
        final var namedGroups = matchResult.namedGroups();
        final var groups =
                IntStream.rangeClosed(0, matchResult.groupCount())
                        .mapToObj(matchResult::group)
                        .toList();
        return new FileTriggerMatchResult(matchResult.groupCount(), groups, namedGroups);
    }

    public String get(final int group) {
        return groups.get(group);
    }

    public String get(final String group) {
        return groups.get(namedGroups().get(group));
    }
}
