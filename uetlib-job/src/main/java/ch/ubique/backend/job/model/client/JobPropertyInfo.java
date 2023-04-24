package ch.ubique.backend.job.model.client;

import ch.ubique.backend.job.model.properties.JobProperty;
import ch.ubique.backend.job.model.properties.JobPropertyDefinition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record JobPropertyInfo(
        String key,
        String type,
        JobPropertyGroupInfo group,
        List<String> allowedValues,
        String defaultValue,
        boolean nullable,
        String description,
        String value) {

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
                Optional.ofNullable(value).orElse(defaultValue));
    }
}
