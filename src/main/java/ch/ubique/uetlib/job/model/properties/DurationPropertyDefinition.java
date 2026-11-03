package ch.ubique.uetlib.job.model.properties;

import org.apache.commons.lang3.math.NumberUtils;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

final class DurationPropertyDefinition extends AbstractJobPropertyDefinition<Duration> {

    public DurationPropertyDefinition(
            final String key,
            final String description,
            final JobPropertyDefinitionGroup group,
            final boolean nullable,
            @Nullable final Duration defaultValue) {
        super(key, description, group, nullable, defaultValue, null);
    }

    @Override
    public Duration decodeFromString(final @Nullable String value) {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        if (NumberUtils.isDigits(value)) {
            return Duration.ofMillis(Long.parseLong(value));
        }

        return Duration.parse(value);
    }

    @Override
    public String encodeFromString(final Duration value) {
        // We use the time4j normalizer to get nicer string representations. E.g. PT720H gets
        // converted to P30D
        return net.time4j.Duration.STD_PERIOD.normalize(net.time4j.Duration.from(value)).toString();
    }
}
