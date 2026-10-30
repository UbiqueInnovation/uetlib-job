package ch.ubique.uetlib.job.model.properties;

import java.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DurationPropertyDefinitionTest {

    @Test
    void testEncodeFromStringDecodeFromString() {
        final var definition =
                new DurationPropertyDefinition(
                        "RUN_TIME_LIMIT", "ABC", JobPropertyDefinitionGroup.RUNNER, true, null);

        final var duration = Duration.ofHours(42);

        Assertions.assertEquals(duration, definition.decodeFromString(duration.toString()));
        Assertions.assertEquals(
                duration, definition.decodeFromString(definition.encodeFromString(duration)));
        Assertions.assertEquals(
                duration, definition.decodeFromString(String.valueOf(duration.toMillis())));
    }
}
