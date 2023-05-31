package ch.ubique.backend.job.model.properties;

import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;

import com.google.common.collect.MoreCollectors;
import com.pivovarit.function.ThrowingFunction;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

public final class JobPropertyDefinitions {

    private JobPropertyDefinitions() {}

    public static final JobPropertyDefinition<Duration> RUN_TIME_LIMIT =
            new DurationPropertyDefinition(
                    "RUN_TIME_LIMIT",
                    "The monitoring will report an error if this job runs longer than the specified limit.",
                    JobPropertyDefinitionGroup.MONITORING,
                    false,
                    Duration.ofHours(1));

    public static final JobPropertyDefinition<Duration> LAST_RUN_MAX_AGE =
            new DurationPropertyDefinition(
                    "LAST_RUN_MAX_AGE",
                    "The monitoring will report an error if the latest run is older than the specified value.",
                    JobPropertyDefinitionGroup.MONITORING,
                    true,
                    null);

    public static final JobPropertyDefinition<JobRunnerKey> RUNNER =
            new EnumPropertyDefinition<>(
                    "RUNNER",
                    "Defines where the job is executed.",
                    JobPropertyDefinitionGroup.RUNNER,
                    JobRunnerKey.CURRENT);

    public static final JobPropertyDefinition<String> K8S_SKU =
            new StringPropertyDefinition(
                    "K8S_SKU",
                    "If set, the K8s job will run on the selected node pool.",
                    JobPropertyDefinitionGroup.RUNNER,
                    true,
                    null,
                    Set.of("highmem", "highperf"));

    public static final JobPropertyDefinition<Duration> K8S_TTL_AFTER_FINISHED =
            new DurationPropertyDefinition(
                    "K8S_TTL_AFTER_FINISHED",
                    "Defines how long the kubernetes job should live after completion. Useful to view logs after run has finished.",
                    JobPropertyDefinitionGroup.RUNNER,
                    false,
                    Duration.ofHours(1));

    public static final JobPropertyDefinition<String> K8S_REQUESTS_CPU =
            new StringPropertyDefinition(
                    "K8S_REQUESTS_CPU",
                    "CPU request for the K8s job.",
                    JobPropertyDefinitionGroup.RUNNER,
                    true,
                    null,
                    null);

    public static final JobPropertyDefinition<String> K8S_REQUESTS_MEMORY =
            new StringPropertyDefinition(
                    "K8S_REQUESTS_MEMORY",
                    "Memory request for the K8s job.",
                    JobPropertyDefinitionGroup.RUNNER,
                    true,
                    null,
                    null);

    public static final JobPropertyDefinition<String> K8S_LIMITS_CPU =
            new StringPropertyDefinition(
                    "K8S_LIMITS_CPU",
                    "CPU limit for the K8s job.",
                    JobPropertyDefinitionGroup.RUNNER,
                    true,
                    null,
                    null);

    public static final JobPropertyDefinition<String> K8S_LIMITS_MEMORY =
            new StringPropertyDefinition(
                    "K8S_LIMITS_MEMORY",
                    "Memory limit for the K8s job.",
                    JobPropertyDefinitionGroup.RUNNER,
                    true,
                    null,
                    null);

    public static final JobPropertyDefinition<Integer> CLEANUP_DAYS_TO_KEEP =
            new IntPropertyDefinition(
                    "CLEANUP_DAYS_TO_KEEP",
                    "Number of days since the most recent DONE job after which job data is cleaned up.",
                    JobPropertyDefinitionGroup.CLEANUP,
                    false,
                    30,
                    null);

    public static final JobPropertyDefinition<Integer> CLEANUP_CHUNK_SIZE =
            new IntPropertyDefinition(
                    "CLEANUP_CHUNK_SIZE",
                    "Maximum number of job runs to clean up per cleanup job run.",
                    JobPropertyDefinitionGroup.CLEANUP,
                    false,
                    1000,
                    null);

    // ------------------------------------
    // Helpers to get fields declared above
    // ------------------------------------

    /**
     * Collection of all static {@link JobPropertyDefinitions} defined in this class.
     *
     * <p>This works by using reflections.
     */
    @SuppressWarnings("unchecked")
    private static final Collection<JobPropertyDefinition<Object>> VALUES =
            // Get all declared fields
            Arrays.stream(JobPropertyDefinitions.class.getDeclaredFields())
                    // Only consider static field
                    .filter(field -> Modifier.isStatic(field.getModifiers()))
                    // Get value for static field
                    .map(ThrowingFunction.unchecked((Field f) -> f.get(null)))
                    .filter(Objects::nonNull)
                    // Only consider JobPropertyDefinitions
                    .filter(value -> value instanceof JobPropertyDefinition<?>)
                    .map(value -> (JobPropertyDefinition<Object>) value)
                    // To collection
                    .toList();

    public static Collection<JobPropertyDefinition<Object>> values() {
        return VALUES;
    }

    public static JobPropertyDefinition<Object> get(final String key) {
        return VALUES.stream()
                .filter(value -> value.key().equals(key))
                .collect(MoreCollectors.onlyElement());
    }
}
