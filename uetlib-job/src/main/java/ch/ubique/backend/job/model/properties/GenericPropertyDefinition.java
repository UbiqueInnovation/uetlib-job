package ch.ubique.backend.job.model.properties;

import ch.ubique.backend.core.jackson.ObjectMapperUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Set;

public final class GenericPropertyDefinition<T> extends JobPropertyDefinition<T> {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperUtils.objectMapper();

    private final TypeReference<T> typeReference;

    @SuppressWarnings("NullableProblems")
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
}
