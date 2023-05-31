package ch.ubique.backend.job.model.properties;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Period;

class JobPropertyTest {

    @Test
    void key() {
        assertEquals("RUNNER", JobPropertyDefinitions.RUNNER.createProperty(null).key());
    }

    @Test
    void encodedValue() {
        final var durationDefinition =
                new DurationPropertyDefinition(
                        "test", "test", JobPropertyDefinitionGroup.CLEANUP, false, null);

        assertEquals("PT0S", durationDefinition.createProperty(Duration.ZERO).encodedValue());
        assertEquals("PT0S", durationDefinition.createPropertyFromString("0").encodedValue());
        assertEquals("P30D", durationDefinition.createProperty(Duration.ofDays(30)).encodedValue());
        assertEquals(
                Duration.ofDays(30), durationDefinition.createPropertyFromString("PT720H").value());
        assertEquals(
                Duration.ofDays(30), durationDefinition.createPropertyFromString("P30D").value());
        assertEquals(
                Duration.ofDays(30).plus(Duration.ofHours(2)),
                durationDefinition.createPropertyFromString("P30DT2H").value());
        assertEquals(
                "P30DT2H",
                durationDefinition
                        .createProperty(Duration.ofDays(30).plus(Duration.ofHours(2)))
                        .encodedValue());
        assertEquals("P30D", durationDefinition.createPropertyFromString("PT720H").encodedValue());
        assertEquals(
                "P365D",
                durationDefinition
                        .createPropertyFromString(Period.ofDays(365).toString())
                        .encodedValue());
        assertEquals(
                "P36500D",
                durationDefinition
                        .createPropertyFromString(Period.ofDays(100 * 365).toString())
                        .encodedValue());
    }

    @Test
    void isValuePresent() {
        for (final var definition : JobPropertyDefinitions.values()) {

            if (definition.defaultValue().isEmpty()) {
                continue;
            }
            assertTrue(definition.createProperty(null).isValuePresent());
            assertTrue(definition.createPropertyFromString("  ").isValuePresent());
            assertDoesNotThrow(() -> definition.createPropertyFromString("  ").toString());
        }
    }
}
