package ch.ubique.backend.job.model.regex;

import static java.util.Map.Entry;
import static java.util.stream.Collectors.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.stream.IntStream;

public final class SerializableMatchResult implements MatchResult {

    @JsonProperty(required = true)
    private final int first;

    @JsonProperty(required = true)
    private final int last;

    @JsonProperty(required = true)
    private final int groupCount;

    @JsonProperty(required = true)
    private final int[] groupIndices;

    @JsonProperty private final @Nullable String text;

    @JsonProperty(required = true)
    @NotNull
    private final Map<String, Integer> namedGroups;

    @JsonProperty(required = true)
    @NotNull
    private final List<@Nullable String> groups;

    @JsonCreator
    public SerializableMatchResult(
            final int first,
            final int last,
            final int groupCount,
            final @NotNull List<@Nullable String> groups,
            final int[] groupIndices,
            final @Nullable String text,
            final @NotNull Map<String, Integer> namedGroups) {
        this.first = first;
        this.last = last;
        this.groupCount = groupCount;
        this.groups = groups;
        this.groupIndices = groupIndices;
        this.text = text;
        this.namedGroups = namedGroups;
    }

    public static SerializableMatchResult fromMatcher(final Matcher matcher) {
        final var matchResult = matcher.toMatchResult();
        final var hasMatch = matcher.hasMatch();

        final var groupCount = matchResult.groupCount();
        final var namedGroups = matchResult.namedGroups();

        if (!hasMatch) {
            return new SerializableMatchResult(
                    -1, -1, groupCount, List.of(), new int[0], null, namedGroups);
        }

        final var first = matchResult.start();
        final var last = matchResult.end();
        final var text = matcher.group();
        final var groupIndices =
                IntStream.rangeClosed(0, matchResult.groupCount())
                        .flatMap(g -> IntStream.of(matchResult.start(g), matchResult.end(g)))
                        .toArray();
        final var groups =
                IntStream.rangeClosed(0, matchResult.groupCount())
                        .boxed()
                        .<@Nullable String>map(matchResult::group)
                        .toList();
        return new SerializableMatchResult(
                first, last, groupCount, groups, groupIndices, text, namedGroups);
    }

    @Override
    public int start() {
        checkMatch();
        return first;
    }

    @Override
    public int start(final int group) {
        checkMatch();
        checkGroup(group);
        return groupIndices[group * 2];
    }

    @Override
    public int end() {
        checkMatch();
        return last;
    }

    @Override
    public int end(final int group) {
        checkMatch();
        checkGroup(group);
        return groupIndices[group * 2 + 1];
    }

    @Override
    public int groupCount() {
        return groupCount;
    }

    public List<@Nullable String> groups() {
        return groups;
    }

    @Override
    @Nullable
    public String group() {
        checkMatch();
        return group(0);
    }

    @Override
    @Nullable
    public String group(final int group) {
        checkMatch();
        checkGroup(group);
        return groups.get(group);
    }

    @Override
    public Map<String, Integer> namedGroups() {
        return namedGroups;
    }

    @Override
    public boolean hasMatch() {
        return first >= 0;
    }

    private void checkGroup(final int group) {
        if (group < 0 || group > groupCount) {
            throw new IndexOutOfBoundsException("No group " + group);
        }
    }

    private void checkMatch() {
        if (!hasMatch()) {
            throw new IllegalStateException("No match found");
        }
    }

    @JsonIgnore
    public Map<String, String> namedGroupValues() {
        //noinspection DataFlowIssue
        return namedGroups().entrySet().stream()
                .collect(toMap(Entry::getKey, e -> group(e.getValue())));
    }

    @Override
    public boolean equals(final @Nullable Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof final SerializableMatchResult that)) {
            return false;
        }

        return new EqualsBuilder()
                .append(first, that.first)
                .append(last, that.last)
                .append(groupCount, that.groupCount)
                .append(groups, that.groups)
                .append(groupIndices, that.groupIndices)
                .append(text, that.text)
                .append(namedGroups, that.namedGroups)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(first)
                .append(last)
                .append(groupCount)
                .append(groups)
                .append(groupIndices)
                .append(text)
                .append(namedGroups)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("first", first)
                .append("last", last)
                .append("groupCount", groupCount)
                .append("groups", groups)
                .append("groupIndices", groupIndices)
                .append("text", text)
                .append("namedGroups", namedGroups)
                .toString();
    }
}
