package ch.ubique.backend.job.model.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Optional;
import java.util.Set;

public final class BooleanPropertyDefinition extends JobPropertyDefinition<Boolean> {
    BooleanPropertyDefinition(
            @NotBlank final String key,
            @NotBlank final String description,
            @NotNull final JobPropertyDefinitionGroup group,
            @NotNull final Boolean defaultValue) {
        super(key, description, group, false, defaultValue, Set.of(true, false));
    }

    @Override
    public Boolean decode(final String value) {
        return Optional.ofNullable(value).map(Boolean::parseBoolean).orElse(null);
    }

    @Override
    public String encode(final Boolean value) {
        return Optional.ofNullable(value).map(String::valueOf).orElse(null);
    }
}
