package ch.ubique.backend.job.model.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import java.util.Set;

final class StringPropertyDefinition extends AbstractJobPropertyDefinition<String> {

    StringPropertyDefinition(
            final String key,
            final String description,
            final JobPropertyDefinitionGroup group,
            final boolean nullable,
            @Nullable final String defaultValue,
            @Nullable final Set<String> allowedValues) {
        super(key, description, group, nullable, defaultValue, allowedValues);
    }

    @Override
    public String decodeFromString(final @Nullable String value) {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        return value;
    }

    @Override
    public String encodeFromString(final String value) {
        return value;
    }
}
