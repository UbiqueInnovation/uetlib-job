package ch.ubique.backend.job.model;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum JobPropertyKey {
    RUN_TIME_LIMIT(
            Long.class,
            null,
            String.valueOf(Duration.ofHours(1).toMillis()),
            false,
            "If set, the monitoring will report an error if this job runs longer than the specified limit.",
            "ms"),
    RUNNER(
            String.class,
            JobRunnerKey.names(),
            JobRunnerKey.CURRENT.name(),
            false,
            "Defines where the job is executed.",
            null),

    // For k8s jobs
    K8S_SKU(
            String.class,
            Set.of("highmem", "highperf"),
            null,
            true,
            "If set, the K8s job will run on the selected node pool.",
            null),
    K8S_TTL_AFTER_FINISHED(
            Long.class,
            null,
            String.valueOf(Duration.ofHours(1).toMillis()),
            false,
            "Defines how long the kubernetes job should live after completion. Useful to view logs after run has finished.",
            "ms"),
    K8S_REQUESTS_CPU(String.class, "CPU request for the K8s job."),
    K8S_REQUESTS_MEMORY(String.class, "Memory request for the K8s job."),
    K8S_LIMITS_CPU(String.class, "CPU limit for the K8s job."),
    K8S_LIMITS_MEMORY(String.class, "Memory limit for the K8s job.");

    private final Class<?> type;

    private final String unit;

    private final Set<String> allowedValues;

    private final String defaultValue;

    private final boolean nullable;
    private final String description;

    JobPropertyKey(final Class<?> type, final String description) {
        this(type, null, null, true, description, null);
    }

    JobPropertyKey(
            final Class<?> type,
            final Set<String> allowedValues,
            final String defaultValue,
            final boolean nullable,
            final String description,
            final String unit) {
        this.type = type;
        this.allowedValues = allowedValues;
        this.defaultValue = defaultValue;
        this.nullable = nullable;
        this.description = description;
        this.unit = unit;
    }

    public Class<?> getType() {
        return type;
    }

    public String getUnit() {
        return unit;
    }

    public Set<String> getAllowedValues() {
        return allowedValues;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public boolean isNullable() {
        return nullable;
    }

    public String getDescription() {
        return description;
    }

    public static Map<JobPropertyKey, String> defaultProperties() {
        return Arrays.stream(JobPropertyKey.values())
                .filter(key -> key.getDefaultValue() != null)
                .collect(Collectors.toMap(Function.identity(), JobPropertyKey::getDefaultValue));
    }
}
