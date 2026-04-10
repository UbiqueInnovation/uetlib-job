package ch.ubique.backend.job.model.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;
import java.util.Set;

final class GenericPropertyDefinition<T> extends AbstractJobPropertyDefinition<T> {

    private final JsonMapper jsonMapper;

    private final TypeReference<T> typeReference;

    GenericPropertyDefinition(
            @NotBlank final String key,
            @NotBlank final String description,
            @NotNull final JobPropertyDefinitionGroup group,
            final boolean nullable,
            @Nullable final T defaultValue,
            @Nullable final Set<T> allowedValues,
            @NotNull final JsonMapper jsonMapper,
            @NotNull final TypeReference<T> typeReference) {
        super(
                key,
                description,
                group,
                nullable,
                nullable ? defaultValue : Objects.requireNonNull(defaultValue),
                allowedValues);
        this.jsonMapper = jsonMapper;
        this.typeReference = typeReference;
    }

    @Override
    public T decodeFromString(final String value) {
        return jsonMapper.readValue(value, typeReference);
    }

    @Override
    public String encodeFromString(final T value) {
        return jsonMapper.writeValueAsString(value);
    }
}
