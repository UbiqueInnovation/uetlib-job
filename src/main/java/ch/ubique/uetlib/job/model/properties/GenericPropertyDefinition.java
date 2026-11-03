package ch.ubique.uetlib.job.model.properties;

import org.jspecify.annotations.Nullable;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;
import java.util.Set;

final class GenericPropertyDefinition<T extends @Nullable Object>
        extends AbstractJobPropertyDefinition<T> {

    private final JsonMapper jsonMapper;

    private final TypeReference<T> typeReference;

    GenericPropertyDefinition(
            final String key,
            final String description,
            final JobPropertyDefinitionGroup group,
            final boolean nullable,
            @Nullable final T defaultValue,
            @Nullable final Set<T> allowedValues,
            final JsonMapper jsonMapper,
            final TypeReference<T> typeReference) {
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
    public T decodeFromString(final @Nullable String value) {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        return jsonMapper.readValue(value, typeReference);
    }

    @Override
    public String encodeFromString(final T value) {
        return jsonMapper.writeValueAsString(value);
    }
}
