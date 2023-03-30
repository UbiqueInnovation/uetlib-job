package ch.ubique.backend.job.model.properties;

import static org.junit.jupiter.api.Assertions.*;

import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;

import org.junit.jupiter.api.Test;

import java.util.Set;

class EnumPropertyDefinitionTest {

    @Test
    void testEncodeDecode() {
        final var definition = new EnumPropertyDefinition<>("RUNNER", "ABC", JobRunnerKey.CURRENT);

        assertEquals(JobRunnerKey.CURRENT.name(), definition.encode(JobRunnerKey.CURRENT));
        assertEquals(
                JobRunnerKey.CURRENT, definition.decode(JobRunnerKey.CURRENT.name().toLowerCase()));
        assertEquals(
                JobRunnerKey.CURRENT, definition.decode(definition.encode(JobRunnerKey.CURRENT)));

        assertThrows(NullPointerException.class, () -> definition.decode("made up value"));
    }

    @Test
    void testConstructor() {
        final var definition = new EnumPropertyDefinition<>("RUNNER", "ABC", JobRunnerKey.CURRENT);

        assertEquals("RUNNER", definition.key());
        assertEquals("ABC", definition.description());
        assertFalse(definition.nullable());
        assertTrue(definition.defaultValue().isPresent());
        assertEquals(JobRunnerKey.CURRENT, definition.defaultValue().get());
        assertTrue(definition.allowedValues().isPresent());
        assertEquals(Set.of(JobRunnerKey.values()), definition.allowedValues().get());
    }

    @Test
    void testRequiredFileds() {
        assertThrows(
                NullPointerException.class, () -> new EnumPropertyDefinition<>(null, null, null));

        assertThrows(
                NullPointerException.class,
                () -> new EnumPropertyDefinition<>(null, "ABC", JobRunnerKey.CURRENT));

        assertThrows(
                NullPointerException.class,
                () -> new EnumPropertyDefinition<>("RUNNER", null, JobRunnerKey.CURRENT));

        assertThrows(
                RuntimeException.class,
                () -> new EnumPropertyDefinition<>("RUNNER", " ", JobRunnerKey.CURRENT));

        assertThrows(
                NullPointerException.class,
                () -> new EnumPropertyDefinition<>("RUNNER", "ABC", null));
    }
}
