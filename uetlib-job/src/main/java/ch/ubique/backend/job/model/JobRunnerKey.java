package ch.ubique.backend.job.model;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum JobRunnerKey {
    CURRENT,
    DEDICATED_K8S_JOB;

    public static JobRunnerKey parseString(final String value) {
        return Arrays.stream(values())
                .filter(key -> key.name().equalsIgnoreCase(value))
                .findAny()
                .orElseThrow();
    }

    public static Set<String> names() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }
}
