package ch.ubique.backend.job.model;

import java.time.Duration;
import java.util.Set;

public enum JobPropertyKey {
    RUN_TIME_LIMIT(
            Duration.class,
            "If set, the monitoring will report an error if this job runs longer than the specified limit."),
    RUNNER(
            String.class,
            Set.of("current", "dedicated_k8s_job"),
            "current",
            false,
            "Defines where the job is executed."),

    // For k8s jobs
    K8S_SKU(
            String.class,
            Set.of("highmem", "highperf"),
            null,
            true,
            "If set, the K8s job will run on the selected node pool."),
    K8S_REQUESTS_CPU(String.class, "CPU request for the K8s job."),
    K8S_REQUESTS_MEMORY(String.class, "Memory request for the K8s job."),
    K8S_LIMITS_CPU(String.class, "CPU limit for the K8s job."),
    K8S_LIMITS_MEMORY(String.class, "Memory limit for the K8s job.");

    private final Class<?> type;

    private final Set<String> allowedValues;

    private final String defaultValue;

    private final boolean nullable;
    private final String description;

    JobPropertyKey(final Class<?> type, final String description) {
        this(type, null, null, true, description);
    }

    JobPropertyKey(
            final Class<?> type,
            final Set<String> allowedValues,
            final String defaultValue,
            final boolean nullable,
            final String description) {
        this.type = type;
        this.allowedValues = allowedValues;
        this.defaultValue = defaultValue;
        this.nullable = nullable;
        this.description = description;
    }

    public Class<?> getType() {
        return type;
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
}
