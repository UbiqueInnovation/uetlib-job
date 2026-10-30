package ch.ubique.uetlib.job.model.properties;

import ch.ubique.uetlib.job.model.k8s.KubernetesPodTolerations;
import ch.ubique.uetlib.job.model.properties.enums.JobManualTriggerMode;
import ch.ubique.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.ubique.uetlib.job.model.properties.enums.StateCheckStrategy;
import ch.ubique.uetlib.job.model.runner.RunnerGroup;
import com.pivovarit.function.ThrowingFunction;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

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
    public static final JobPropertyDefinition<Boolean> LAST_RUN_MAX_AGE_ALLOW_NO_RUN =
            new BooleanPropertyDefinition(
                    "LAST_RUN_MAX_AGE_ALLOW_NO_RUN",
                    "When set to true, the JobLastRunMaxAgeCheck will not report an error if there"
                            + " is no last run",
                    JobPropertyDefinitionGroup.MONITORING,
                    false);
    public static final JobPropertyDefinition<Duration> MAX_DURATION_IN_SCHEDULED_STATE =
            new DurationPropertyDefinition(
                    "MAX_DURATION_IN_SCHEDULED_STATE",
                    "The monitoring will report an error if the latest run is longer in the"
                            + " SCHEDULED state than the specified value.",
                    JobPropertyDefinitionGroup.MONITORING,
                    false,
                    Duration.ofMinutes(15));

    public static final JobPropertyDefinition<Boolean> KILL_JOBS_SCHEDULE_TIMEOUT =
            new BooleanPropertyDefinition(
                    "KILL_JOBS_SCHEDULE_TIMEOUT",
                    "If enabled, jobs that are scheduled for too long will be killed. The timeout is defined by MAX_DURATION_IN_SCHEDULED_STATE.",
                    JobPropertyDefinitionGroup.MONITORING,
                    false);
    public static final JobPropertyDefinition<Boolean> KILL_JOBS_RUN_TIMEOUT =
            new BooleanPropertyDefinition(
                    "KILL_JOBS_RUN_TIMEOUT",
                    "If enabled, jobs that are running for too long will be killed. The timeout is defined by RUN_TIME_LIMIT.",
                    JobPropertyDefinitionGroup.MONITORING,
                    false);
    public static final JobPropertyDefinition<Duration> KILL_GRACE_PERIOD =
            new DurationPropertyDefinition(
                    "KILL_GRACE_PERIOD",
                    "Grace period after timeout before killing a job. This affects both KILL_JOBS_SCHEDULE_TIMEOUT and KILL_JOBS_RUN_TIMEOUT.",
                    JobPropertyDefinitionGroup.MONITORING,
                    false,
                    Duration.ofMinutes(5));
    public static final JobPropertyDefinition<StateCheckStrategy> STATE_CHECK_STRATEGY =
            new EnumPropertyDefinition<>(
                    "STATE_CHECK_STRATEGY",
                    "Sets the monitoring strategy for the state checks of job runs.",
                    JobPropertyDefinitionGroup.MONITORING,
                    StateCheckStrategy.CHECK_LATEST_SUCCESSFUL);
    public static final JobPropertyDefinition<Duration> STATE_CHECK_INTERVAL =
            new DurationPropertyDefinition(
                    "STATE_CHECK_INTERVAL",
                    "Interval for STATE_CHECK_STRATEGY if an interval strategy is selected.",
                    JobPropertyDefinitionGroup.MONITORING,
                    false,
                    Duration.ofHours(24));

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
                    JsonMapper.shared(),
                    new TypeReference<>() {});

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
                    "CPU request for the k8s job. CPU limit will stay as unset",
                    JobPropertyDefinitionGroup.RUNNER,
                    true,
                    null,
                    null);
    public static final JobPropertyDefinition<String> K8S_REQUESTS_MEMORY =
            new StringPropertyDefinition(
                    "K8S_REQUESTS_MEMORY",
                    "Memory request and limit for the k8s job.",
                    JobPropertyDefinitionGroup.RUNNER,
                    true,
                    null,
                    null);
    public static final JobPropertyDefinition<String> K8S_ADDITIONAL_JAVA_OPTS =
            new StringPropertyDefinition(
                    "K8S_ADDITIONAL_JAVA_OPTS",
                    "ADDITIONAL_JAVA_OPTS to be set for for the k8s job.",
                    JobPropertyDefinitionGroup.RUNNER,
                    false,
                    "-XX:MaxRAMPercentage=80.0",
                    null);
    public static final JobPropertyDefinition<Map<String, String>> K8S_POD_LABELS =
            new GenericPropertyDefinition<>(
                    "K8S_POD_LABELS",
                    "K8S_POD_LABELS to be set for for the k8s job pod.",
                    JobPropertyDefinitionGroup.RUNNER,
                    false,
                    Map.of(),
                    null,
                    JsonMapper.shared(),
                    new TypeReference<>() {});
    public static final JobPropertyDefinition<@Nullable KubernetesPodTolerations>
            K8S_POD_TOLERATIONS =
                    new GenericPropertyDefinition<>(
                            "K8S_POD_TOLERATIONS",
                            "K8S_POD_TOLERATIONS to be set for for the k8s job pod.",
                            JobPropertyDefinitionGroup.RUNNER,
                            true,
                            null,
                            null,
                            JsonMapper.shared(),
                            new TypeReference<>() {});
    public static final JobPropertyDefinition<@Nullable Map<String, String>> K8S_POD_NODE_SELECTOR =
            new GenericPropertyDefinition<>(
                    "K8S_POD_NODE_SELECTOR",
                    "K8S_POD_NODE_SELECTOR to be set for for the k8s job pod.",
                    JobPropertyDefinitionGroup.RUNNER,
                    true,
                    null,
                    null,
                    JsonMapper.shared(),
                    new TypeReference<>() {});
    public static final JobPropertyDefinition<Integer> CLEANUP_DAYS_TO_KEEP =
            new IntPropertyDefinition(
                    "CLEANUP_DAYS_TO_KEEP",
                    "Number of days since the most recent DONE job after which job data is cleaned"
                            + " up.",
                    JobPropertyDefinitionGroup.CLEANUP,
                    false,
                    30,
                    null);
    public static final JobPropertyDefinition<Integer> CLEANUP_DAYS_TO_KEEP_SKIPPED =
            new IntPropertyDefinition(
                    "CLEANUP_DAYS_TO_KEEP_SKIPPED",
                    "Number of days to keep SKIPPED jobs (absolute, not relative to DONE jobs)",
                    JobPropertyDefinitionGroup.CLEANUP,
                    false,
                    7,
                    null);
    public static final JobPropertyDefinition<Integer> CLEANUP_CHUNK_SIZE =
            new IntPropertyDefinition(
                    "CLEANUP_CHUNK_SIZE",
                    "Maximum number of job runs to clean up per cleanup job run.",
                    JobPropertyDefinitionGroup.CLEANUP,
                    false,
                    1000,
                    null);
    public static final JobPropertyDefinition<JobManualTriggerMode> JOB_MANUAL_TRIGGER_MODE =
            new EnumPropertyDefinition<>(
                    "JOB_MANUAL_TRIGGER_MODE",
                    "Whether the job can be executed manually from the interface, or replayed with"
                            + " context.",
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
