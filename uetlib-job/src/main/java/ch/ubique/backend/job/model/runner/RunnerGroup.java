package ch.ubique.backend.job.model.runner;

import ch.ubique.backend.job.Job;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.HashSet;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RunnerGroup(
        @NotBlank String identifier,
        @Positive int threadPoolSize,
        @NotNull @JsonSetter(nulls = Nulls.AS_EMPTY) Set<Class<? extends Job>> allowedJobs) {

    private static final Set<RunnerGroup> INSTANCES = new HashSet<>();

    @SuppressWarnings("java:S6207") // A canonical constructor is nor possible since,
    // it calls check() before fields are initialized
    public RunnerGroup(
            @NotBlank final String identifier,
            @Positive final int threadPoolSize,
            @NotNull @JsonSetter(nulls = Nulls.AS_EMPTY)
                    final Set<Class<? extends Job>> allowedJobs) {
        this.identifier = identifier;
        this.threadPoolSize = threadPoolSize;
        this.allowedJobs = allowedJobs;
        check(this);
    }

    public RunnerGroup(@NotBlank final String identifier, @Positive final int threadPoolSize) {
        this(identifier, threadPoolSize, Set.of());
    }

    private static synchronized void check(final RunnerGroup runnerGroup) {
        if (INSTANCES.stream()
                .anyMatch(existing -> sameIdentifierButNotEqual(runnerGroup, existing))) {
            throw new IllegalArgumentException(
                    "There is already a different RunnerGroup with the same identifier.");
        }
        INSTANCES.add(runnerGroup);
    }

    private static boolean sameIdentifierButNotEqual(final RunnerGroup a, final RunnerGroup b) {
        return b.identifier().equals(a.identifier()) && !b.equals(a);
    }
}
