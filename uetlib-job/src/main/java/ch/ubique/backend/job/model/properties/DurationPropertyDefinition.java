package ch.ubique.backend.job.model.properties;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.math.NumberUtils;

import java.text.ParseException;
import java.time.Duration;
import java.time.Period;

final class DurationPropertyDefinition extends JobPropertyDefinition<Duration> {

    public DurationPropertyDefinition(
            @NotBlank final String key,
            @NotBlank final String description,
            @NotNull final JobPropertyDefinitionGroup group,
            @NotNull final boolean nullable,
            @Nullable final Duration defaultValue) {
        super(key, description, group, nullable, defaultValue, null);
    }

    @Override
    public Duration decode(final String value) {
        if (NumberUtils.isDigits(value)) {
            return Duration.ofMillis(Long.parseLong(value));
        }
        try {
            // By using time4j, we can support parsing duration strings with period components,
            // like P30D
            return Duration.from(net.time4j.Duration.parsePeriod(value).toTemporalAmount());
        } catch (final ParseException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String encode(final Duration value) {
        // We use the time4j normalizer to get nicer string representations. E.g. PT720H gets
        // converted to P30D
        return net.time4j.Duration.STD_PERIOD.normalize(net.time4j.Duration.from(value)).toString();
    }
}
