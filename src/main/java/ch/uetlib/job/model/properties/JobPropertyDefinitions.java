package ch.uetlib.job.model.properties;

import ch.uetlib.job.model.k8s.KubernetesPodTolerations;
import ch.uetlib.job.model.properties.enums.JobManualTriggerMode;
import ch.uetlib.job.model.properties.enums.JobQueueBehavior;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.model.properties.enums.StateCheckStrategy;
import ch.uetlib.job.model.runner.RunnerGroup;
import ch.uetlib.job.model.runner.conditions.JobRunnerTaint;

import com.pivovarit.function.ThrowingFunction;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

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
    public static final JobPropertyDefinition<String> K8S_LIMITS_CPU =
            new StringPropertyDefinition(
                    "K8S_LIMITS_CPU",
                    "CPU limit for the k8s job.",
                    JobPropertyDefinitionGroup.RUNNER,
                    true,
                    null,
                    null);
    public static final JobPropertyDefinition<String> K8S_REQUESTS_CPU =
            new StringPropertyDefinition(
                    "K8S_REQUESTS_CPU",
                    "CPU request for the k8s job.",
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
    public static final JobPropertyDefinition<KubernetesPodTolerations> K8S_POD_TOLERATIONS =
            new GenericPropertyDefinition<>(
                    "K8S_POD_TOLERATIONS",
                    "K8S_POD_TOLERATIONS to be set for for the k8s job pod.",
                    JobPropertyDefinitionGroup.RUNNER,
                    true,
                    null,
                    null,
                    JsonMapper.shared(),
                    new TypeReference<>() {});
    public static final JobPropertyDefinition<Map<String, String>> K8S_POD_NODE_SELECTOR =
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
    public static final JobPropertyDefinition<Set<JobRunnerTaint>> JOB_RUN_TAINTS =
            new GenericPropertyDefinition<>(
                    "JOB_RUN_TAINTS",
                    "Taints to be set for the job run. Taints are used to prevent certain jobs from"
                            + " running on certain runners.",
                    JobPropertyDefinitionGroup.QUEUE,
                    false,
                    Set.of(),
                    null,
                    JsonMapper.shared(),
                    new TypeReference<>() {});
    public static final JobPropertyDefinition<Integer> PRIORITY =
            new IntPropertyDefinition(
                    "PRIORITY",
                    "Higher-priority runs are dequeued first. Stamped onto the queue row when the run"
                            + " is queued; a still-queued run can be reordered individually via the"
                            + " status API.",
                    JobPropertyDefinitionGroup.QUEUE,
                    false,
                    1,
                    null);
    public static final JobPropertyDefinition<Boolean> ALLOW_PARALLEL_RUNS =
            new BooleanPropertyDefinition(
                    "ALLOW_PARALLEL_RUNS",
                    "If true, a run may start while a previous run of the same job is still running.",
                    JobPropertyDefinitionGroup.RUNNER,
                    false);
    public static final JobPropertyDefinition<Boolean> FORCE_RUN_OVERRIDES_ALLOW_PARALLEL_RUNS =
            new BooleanPropertyDefinition(
                    "FORCE_RUN_OVERRIDES_ALLOW_PARALLEL_RUNS",
                    "If true, a force run is allowed to start in parallel even when ALLOW_PARALLEL_RUNS"
                            + " is false.",
                    JobPropertyDefinitionGroup.RUNNER,
                    true);
    public static final JobPropertyDefinition<JobQueueBehavior> QUEUE_BEHAVIOR =
            new EnumPropertyDefinition<>(
                    "QUEUE_BEHAVIOR",
                    "What happens when a run is queued for a job that already has one waiting or"
                            + " running.",
                    JobPropertyDefinitionGroup.QUEUE,
                    JobQueueBehavior.REPLACE_RUN);
    public static final JobPropertyDefinition<Duration> CLAIM_TIMEOUT =
            new DurationPropertyDefinition(
                    "CLAIM_TIMEOUT",
                    "Override the default timeout for claimed queue runs. If a job is claimed but not "
                            + "running for longer than this timeout, it will be reset to queued and can be claimed "
                            + "by another runner.",
                    JobPropertyDefinitionGroup.QUEUE,
                    false,
                    Duration.ofMinutes(2));
    public static final JobPropertyDefinition<Duration> QUEUE_TIMEOUT =
            new DurationPropertyDefinition(
                    "QUEUE_TIMEOUT",
                    "Override the default timeout for how long a run may sit queued without activity"
                            + " before it's considered unclaimable and marked CANCELLED - e.g. no"
                            + " currently running instance matches its version or taints. Measured"
                            + " from the run's last activity, not from when it was first queued - an"
                            + " instance that could run it, waiting for a free thread or for the"
                            + " job's current run to finish, keeps it alive.",
                    JobPropertyDefinitionGroup.QUEUE,
                    false,
                    Duration.ofMinutes(30));
    public static final JobPropertyDefinition<Duration> CANCELLED_QUEUE_RETENTION =
            new DurationPropertyDefinition(
                    "CANCELLED_QUEUE_RETENTION",
                    "Override how long a CANCELLED queue row (see QUEUE_TIMEOUT) is kept before being"
                            + " automatically deleted. Acknowledging it in the status interface"
                            + " deletes it immediately, independent of this timeout.",
                    JobPropertyDefinitionGroup.QUEUE,
                    false,
                    Duration.ofDays(7));

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
