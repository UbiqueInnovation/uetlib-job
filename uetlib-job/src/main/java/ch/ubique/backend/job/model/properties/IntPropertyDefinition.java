package ch.ubique.backend.job.model.properties;

import java.util.Optional;
import java.util.Set;

final class IntPropertyDefinition extends JobPropertyDefinition<Integer> {

    IntPropertyDefinition(
            final String key,
            final String description,
            final boolean nullable,
            final int defaultValue,
            final Set<Integer> allowedValues) {
        super(key, description, nullable, defaultValue, allowedValues);
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
