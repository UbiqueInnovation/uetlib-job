package ch.uetlib.job.model.properties;

import org.jspecify.annotations.Nullable;

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
    public Integer decodeFromString(final String value) {
        return Integer.parseInt(value);
    }

    @Override
    public String encodeFromString(final Integer value) {
        return String.valueOf(value);
    }
}
