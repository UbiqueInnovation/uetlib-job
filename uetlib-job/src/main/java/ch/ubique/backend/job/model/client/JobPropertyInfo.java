package ch.ubique.backend.job.model.client;

import ch.ubique.backend.job.model.properties.JobProperty;
import ch.ubique.backend.job.model.properties.JobPropertyDefinition;

import java.util.List;
import java.util.Objects;

public record JobPropertyInfo(
        String key,
        String type,
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
        return new JobPropertyInfo(
                definition.key(),
                definition.getClass().getName(),
                definition
                        .allowedValues()
                        .map(
                                values ->
                                        values.stream()
                                                .map(definition::encode)
                                                .filter(Objects::nonNull)
                                                .toList())
                        .orElse(null),
                definition.defaultValue().map(definition::encode).orElse(null),
                definition.nullable(),
                definition.description(),
                value);
    }
}
