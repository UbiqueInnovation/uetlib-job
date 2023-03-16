package ch.ubique.backend.job.model.client;

import ch.ubique.backend.job.model.JobPropertyKey;

import java.util.Set;

public record JobProperty(
        JobPropertyKey key,
        String type,
        Set<String> allowedValues,
        String defaultValue,
        boolean nullable,
        String description,
        String unit,
        String value) {

    public JobProperty(JobPropertyKey key, String value) {
        this(
                key,
                key.getType().getName(),
                key.getAllowedValues(),
                key.getDefaultValue(),
                key.isNullable(),
                key.getDescription(),
                key.getUnit(),
                value);
    }
}
