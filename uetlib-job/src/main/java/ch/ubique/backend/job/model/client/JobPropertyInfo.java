package ch.ubique.backend.job.model.client;

import ch.ubique.backend.job.model.properties.JobProperty;
import ch.ubique.backend.job.model.properties.JobPropertyDefinition;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;

public record JobPropertyInfo(
        @NotBlank String key,
        @NotBlank String type,
        @NotNull JobPropertyGroupInfo group,
        @Nullable List<String> allowedValues,
        @Nullable String defaultValue,
        @NotNull boolean nullable,
        @NotBlank String description,
        @Nullable String value) {

    public static JobPropertyInfo from(final JobProperty<?> property) {
        return from(property.definition(), property.encodedValue());
    }

    public static JobPropertyInfo from(final JobPropertyDefinition<?> definition) {
        return from(definition, null);
    }

    private static <T> JobPropertyInfo from(
            final JobPropertyDefinition<T> definition, final String value) {
        final var defaultValue = definition.defaultValue().map(definition::encode).orElse(null);
        return new JobPropertyInfo(
                definition.key(),
                definition.getClass().getName(),
                new JobPropertyGroupInfo(definition.group()),
                definition
                        .allowedValues()
                        .map(
                                values ->
                                        values.stream()
                                                .map(definition::encode)
                                                .filter(Objects::nonNull)
                                                .toList())
                        .orElse(null),
                defaultValue,
                definition.nullable(),
                definition.description(),
                Objects.requireNonNullElse(value, defaultValue));
    }
}
