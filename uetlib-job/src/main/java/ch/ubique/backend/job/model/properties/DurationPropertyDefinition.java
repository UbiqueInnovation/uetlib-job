package ch.ubique.backend.job.model.properties;

import org.apache.commons.lang3.math.NumberUtils;

import java.time.Duration;

final class DurationPropertyDefinition extends JobPropertyDefinition<Duration> {

    public DurationPropertyDefinition(
            final String key,
            final String description,
            final boolean nullable,
            final Duration defaultValue) {
        super(key, description, nullable, defaultValue, null);
    }

    @Override
    public Duration decode(final String value) {
        if (NumberUtils.isDigits(value)) {
            return Duration.ofMillis(Long.parseLong(value));
        }
        return Duration.parse(value);
    }

    @Override
    public String encode(final Duration value) {
        return value.toString();
    }
}
