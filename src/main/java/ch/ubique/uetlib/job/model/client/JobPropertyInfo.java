package ch.ubique.uetlib.job.model.client;

import ch.ubique.uetlib.job.model.properties.JobProperty;
import ch.ubique.uetlib.job.model.properties.JobPropertyDefinition;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public record JobPropertyInfo(
        @JsonProperty(required = true) @NotBlank String key,
        @JsonProperty(required = true) @NotBlank String type,
        @JsonProperty(required = true) @NotNull JobPropertyGroupInfo group,
        @Nullable List<String> allowedValues,
        @Nullable String defaultValue,
        @JsonProperty(required = true) boolean nullable,
        @JsonProperty(required = true) @NotBlank String description,
        @Nullable String value) {

    public static JobPropertyInfo from(final JobProperty<?> property) {
        return from(property.definition(), property.encodedValue());
    }

    public static JobPropertyInfo from(final JobPropertyDefinition<?> definition) {
        return from(definition, null);
    }

    private static <T> JobPropertyInfo from(
            final JobPropertyDefinition<T> definition, final @Nullable String value) {
        final var defaultValue =
                definition.defaultValue().map(definition::encodeFromString).orElse(null);
        return new JobPropertyInfo(
                definition.key(),
                definition.getClass().getName(),
                new JobPropertyGroupInfo(definition.group()),
                definition
                        .allowedValues()
                        .map(
                                values ->
                                        values.stream()
                                                .map(definition::encodeFromString)
                                                .filter(Objects::nonNull)
                                                .toList())
                        .orElse(null),
                defaultValue,
                definition.nullable(),
                definition.description(),
                Optional.ofNullable(value).orElse(defaultValue));
    }
}
