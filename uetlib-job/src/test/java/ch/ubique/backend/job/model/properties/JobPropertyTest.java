package ch.ubique.backend.job.model.properties;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class JobPropertyTest {

    @Test
    void key() {
        assertEquals("RUNNER", JobPropertyDefinitions.RUNNER.createProperty(null).key());
    }

    @Test
    void encodedValue() {
        assertEquals(
                "PT0S",
                JobPropertyDefinitions.RUN_TIME_LIMIT.createProperty(Duration.ZERO).encodedValue());
        assertEquals(
                "PT0S",
                JobPropertyDefinitions.RUN_TIME_LIMIT.createPropertyFromString("0").encodedValue());
    }

    @Test
    void isValuePresent() {
        for (final var definition : JobPropertyDefinitions.VALUES) {

            if (definition.defaultValue().isEmpty()) {
                continue;
            }
            assertTrue(definition.createProperty(null).isValuePresent());
            assertTrue(definition.createPropertyFromString("  ").isValuePresent());
            assertDoesNotThrow(() -> definition.createPropertyFromString("  ").toString());
        }
    }
}
