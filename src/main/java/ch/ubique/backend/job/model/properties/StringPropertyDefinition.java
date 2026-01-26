package ch.ubique.backend.job.model.properties;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

final class StringPropertyDefinition extends AbstractJobPropertyDefinition<String> {

    StringPropertyDefinition(
            @NotBlank final String key,
            @NotBlank final String description,
            @NotNull final JobPropertyDefinitionGroup group,
            final boolean nullable,
            @Nullable final String defaultValue,
            @Nullable final Set<String> allowedValues) {
        super(key, description, group, nullable, defaultValue, allowedValues);
    }

    @Override
    public String decode(final String value) {
        return value;
    }

    @Override
    public String encode(final String value) {
        return value;
    }
}
