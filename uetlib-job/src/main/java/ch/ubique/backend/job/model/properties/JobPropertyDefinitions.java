package ch.ubique.backend.job.model.properties;

import ch.ubique.backend.job.model.properties.enums.JobManualTriggerMode;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.model.runner.RunnerGroup;

import com.pivovarit.function.ThrowingFunction;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class JobPropertyDefinitions {

    public static final JobPropertyDefinition<Duration> RUN_TIME_LIMIT =
            new DurationPropertyDefinition(
                    "RUN_TIME_LIMIT",
                    "The monitoring will report an error if this job runs longer than the specified"
                            + " limit.",
                    JobPropertyDefinitionGroup.MONITORING,
                    false,
                    Duration.ofHours(1));
    public static final JobPropertyDefinition<Duration> LAST_RUN_MAX_AGE =
            new DurationPropertyDefinition(
                    "LAST_RUN_MAX_AGE",
                    "The monitoring will report an error if the latest run is older than the"
                            + " specified value.",
                    JobPropertyDefinitionGroup.MONITORING,
                    true,
                    null);
    public static final JobPropertyDefinition<JobRunnerKey> RUNNER =
            new EnumPropertyDefinition<>(
                    "RUNNER",
                    "Defines where the job is executed.",
                    JobPropertyDefinitionGroup.RUNNER,
                    JobRunnerKey.CURRENT);

    public static final JobPropertyDefinition<RunnerGroup> RUNNER_GROUP =
            new GenericPropertyDefinition<>(
                    "RUNNER_GROUP",
                    "Jobs with the same runner group share the same executor service. Jobs with"
                            + " different groups use different executors and may run in parallel. Only"
                            + " applicable for RUNNER == CURRENT",
                    JobPropertyDefinitionGroup.RUNNER,
                    false,
                    new RunnerGroup("default", 1),
                    null,
                    RunnerGroup.class);

    public static final JobPropertyDefinition<String> K8S_SKU =
            new StringPropertyDefinition(
                    "K8S_SKU",
                    "If set, the K8s job will run on the selected node pool.",
                    JobPropertyDefinitionGroup.RUNNER,
                    true,
                    null,
                    Set.of("highmem"));
    public static final JobPropertyDefinition<Duration> K8S_TTL_AFTER_FINISHED =
            new DurationPropertyDefinition(
                    "K8S_TTL_AFTER_FINISHED",
                    "Defines how long the kubernetes job should live after completion. Useful to"
                            + " view logs after run has finished.",
                    JobPropertyDefinitionGroup.RUNNER,
                    false,
                    Duration.ofHours(1));
    public static final JobPropertyDefinition<String> K8S_REQUESTS_CPU =
            new StringPropertyDefinition(
                    "K8S_REQUESTS_CPU",
                    "CPU request for the K8s job. CPU limit will stay as unset",
                    JobPropertyDefinitionGroup.RUNNER,
                    true,
                    null,
                    null);
    public static final JobPropertyDefinition<String> K8S_REQUESTS_MEMORY =
            new StringPropertyDefinition(
                    "K8S_REQUESTS_MEMORY",
                    "Memory request and limit for the K8s job.",
                    JobPropertyDefinitionGroup.RUNNER,
                    true,
                    null,
                    null);
    public static final JobPropertyDefinition<Integer> CLEANUP_DAYS_TO_KEEP =
            new IntPropertyDefinition(
                    "CLEANUP_DAYS_TO_KEEP",
                    "Number of days since the most recent DONE job after which job data is cleaned"
                            + " up.",
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
    public static final EnumPropertyDefinition<JobManualTriggerMode> JOB_MANUAL_TRIGGER_MODE =
            new EnumPropertyDefinition<>(
                    "JOB_MANUAL_TRIGGER_MODE",
                    "Whether the job can be executed manually from the interface, or replayed with context.",
                    JobPropertyDefinitionGroup.RUNNER,
                    JobManualTriggerMode.NEW_RUN_ALLOWED);

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

    private static final Map<String, JobPropertyDefinition<Object>> VALUES_BY_KEY =
            VALUES.stream()
                    .collect(
                            Collectors.toUnmodifiableMap(
                                    JobPropertyDefinition::key, Function.identity()));

    private JobPropertyDefinitions() {}

    public static Collection<JobPropertyDefinition<Object>> values() {
        return VALUES;
    }

    public static JobPropertyDefinition<Object> get(final String key) {
        return Objects.requireNonNull(
                VALUES_BY_KEY.get(key),
                () ->
                        MessageFormat.format(
                                "found no job property definition for key {0}. Available keys: {1}",
                                key, VALUES_BY_KEY.keySet()));
    }
}
