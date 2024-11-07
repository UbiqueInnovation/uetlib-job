package ch.ubique.backend.job.model.properties;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Optional;
import java.util.Set;

final class IntPropertyDefinition extends AbstractJobPropertyDefinition<Integer> {

    @SuppressWarnings("NullableProblems")
    IntPropertyDefinition(
            @NotBlank final String key,
            @NotBlank final String description,
            @NotNull final JobPropertyDefinitionGroup group,
            @NotNull final boolean nullable,
            @Nullable final Integer defaultValue,
            @Nullable final Set<Integer> allowedValues) {
        super(key, description, group, nullable, defaultValue, allowedValues);
    }

    @Override
    public Integer decode(final String value) {
        return Optional.ofNullable(value).map(Integer::parseInt).orElse(null);
    }

    @Override
    public String encode(final Integer value) {
        return Optional.ofNullable(value).map(String::valueOf).orElse(null);
    }
}
