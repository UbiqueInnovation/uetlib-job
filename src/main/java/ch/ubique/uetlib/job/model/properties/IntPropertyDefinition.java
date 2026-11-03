package ch.ubique.uetlib.job.model.properties;

import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

final class IntPropertyDefinition extends AbstractJobPropertyDefinition<Integer> {

    IntPropertyDefinition(
            final String key,
            final String description,
            final JobPropertyDefinitionGroup group,
            final boolean nullable,
            @Nullable final Integer defaultValue,
            @Nullable final Set<Integer> allowedValues) {
        super(key, description, group, nullable, defaultValue, allowedValues);
    }

    @Override
    public Integer decodeFromString(final @Nullable String value) {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        return Integer.parseInt(value);
    }

    @Override
    @Nullable
    public String encodeFromString(final @Nullable Integer value) {
        return Optional.ofNullable(value).map(String::valueOf).orElse(null);
    }
}
