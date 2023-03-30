package ch.ubique.backend.job.model.properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class DurationPropertyDefinitionTest {

    @Test
    void testEncodeDecode() {
        final var definition = new DurationPropertyDefinition("RUN_TIME_LIMIT", "ABC", true, null);

        final var duration = Duration.ofHours(42);

        Assertions.assertEquals(duration, definition.decode(duration.toString()));
        Assertions.assertEquals(duration, definition.decode(definition.encode(duration)));
        Assertions.assertEquals(duration, definition.decode(String.valueOf(duration.toMillis())));
    }
}
