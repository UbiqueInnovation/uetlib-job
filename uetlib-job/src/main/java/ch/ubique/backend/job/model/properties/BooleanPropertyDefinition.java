package ch.ubique.backend.job.model.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.BooleanUtils;

import java.util.Objects;
import java.util.Set;

public final class BooleanPropertyDefinition extends JobPropertyDefinition<Boolean> {
    @SuppressWarnings("NullableProblems")
    BooleanPropertyDefinition(
            @NotBlank final String key,
            @NotBlank final String description,
            @NotNull final JobPropertyDefinitionGroup group,
            @NotNull final boolean defaultValue) {
        super(key, description, group, false, defaultValue, Set.of(true, false));
    }

    @Override
    public Boolean decode(final String value) {
        return BooleanUtils.toBoolean(value);
    }

    @Override
    public String encode(final Boolean value) {
        return String.valueOf(Objects.requireNonNull(value));
    }
}
