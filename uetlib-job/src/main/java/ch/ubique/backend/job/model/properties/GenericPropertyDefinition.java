package ch.ubique.backend.job.model.properties;

import ch.ubique.backend.core.jackson.ObjectMapperUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.core.GenericTypeResolver;

import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Set;

final class GenericPropertyDefinition<T> extends JobPropertyDefinition<T> {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperUtils.objectMapper();

    GenericPropertyDefinition(
            @NotBlank final String key,
            @NotBlank final String description,
            @NotNull final JobPropertyDefinitionGroup group,
            @NotNull final boolean nullable,
            @NotNull final T defaultValue,
            @Nullable final Set<T> allowedValues) {
        super(
                key,
                description,
                group,
                nullable,
                Objects.requireNonNull(defaultValue),
                allowedValues);
    }

    @Override
    public T decode(final String value) {
        try {
            return OBJECT_MAPPER.readValue(value, genericClass());
        } catch (final JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String encode(final T value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (final JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Class<T> genericClass() {
        //noinspection unchecked
        return (Class<T>)
                GenericTypeResolver.resolveTypeArgument(
                        getClass(), GenericPropertyDefinition.class);
    }
}
