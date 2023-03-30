package ch.ubique.backend.job.model.properties;

import java.util.Set;

final class StringPropertyDefinition extends JobPropertyDefinition<String> {

    StringPropertyDefinition(
            final String key,
            final String description,
            final boolean nullable,
            final String defaultValue,
            final Set<String> allowedValues) {
        super(key, description, nullable, defaultValue, allowedValues);
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
