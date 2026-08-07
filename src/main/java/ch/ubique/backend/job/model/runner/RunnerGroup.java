package ch.ubique.backend.job.model.runner;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import ch.ubique.backend.job.Job;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;

import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RunnerGroup(
        @JsonProperty(required = true) @NotBlank String identifier,
        @JsonProperty(required = true) @Positive int threadPoolSize,
        @JsonProperty(required = true) @NotNull Set<Class<? extends Job>> allowedJobs) {

    private static final Set<RunnerGroup> INSTANCES = new HashSet<>();

    @JsonCreator
    @SuppressWarnings("java:S6207") // A canonical constructor is not possible since,
    // it calls check() before fields are initialized
    public RunnerGroup(
            @JsonProperty(required = true) @NotBlank final String identifier,
            @JsonProperty(required = true) @Positive final int threadPoolSize,
            @JsonProperty final @Nullable Set<Class<? extends Job>> allowedJobs) {
        this.identifier = identifier;
        this.threadPoolSize = threadPoolSize;
        this.allowedJobs = requireNonNull(requireNonNullElse(allowedJobs, Set.of()));
        check(this);
    }

    public RunnerGroup(final String identifier, final int threadPoolSize) {
        this(identifier, threadPoolSize, Set.of());
    }

    private static synchronized void check(final RunnerGroup runnerGroup) {
        final var sameIdentifierButNotEqual =
                INSTANCES.stream()
                        .filter(existing -> sameIdentifierButNotEqual(runnerGroup, existing))
                        .findAny();
        if (sameIdentifierButNotEqual.isPresent()) {
            throw new IllegalArgumentException(
                    MessageFormat.format(
                            "There is already a different RunnerGroup with the same identifier. New"
                                    + " runner group: {0}, existing: {1}",
                            runnerGroup, sameIdentifierButNotEqual));
        }
        INSTANCES.add(runnerGroup);
    }

    private static boolean sameIdentifierButNotEqual(final RunnerGroup a, final RunnerGroup b) {
        return b.identifier().equals(a.identifier()) && !b.equals(a);
    }
}
