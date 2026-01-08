package ch.ubique.backend.job.model.properties;

import ch.ubique.backend.core.jackson.ObjectMapperUtils;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.exception.UncheckedException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;
import java.util.Set;

final class GenericPropertyDefinition<T> extends AbstractJobPropertyDefinition<T> {

    private static final JsonMapper OBJECT_MAPPER = ObjectMapperUtils.jsonMapper();

    private final TypeReference<T> typeReference;

    GenericPropertyDefinition(
            @NotBlank final String key,
            @NotBlank final String description,
            @NotNull final JobPropertyDefinitionGroup group,
            @NotNull final boolean nullable,
            @Nullable final T defaultValue,
            @Nullable final Set<T> allowedValues,
            @NotNull final TypeReference<T> typeReference) {
        super(
                key,
                description,
                group,
                nullable,
                nullable ? defaultValue : Objects.requireNonNull(defaultValue),
                allowedValues);
        this.typeReference = typeReference;
    }

    @Override
    public T decode(final String value) {
        try {
            return OBJECT_MAPPER.readValue(value, typeReference);
        } catch (final JacksonException e) {
            throw new UncheckedException(e);
        }
    }

    @Override
    public String encode(final T value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (final JacksonException e) {
            throw new UncheckedException(e);
        }
    }
}
