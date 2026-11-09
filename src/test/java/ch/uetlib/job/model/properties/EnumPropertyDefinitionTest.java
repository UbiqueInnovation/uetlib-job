package ch.uetlib.job.model.properties;

import static org.junit.jupiter.api.Assertions.*;

import ch.uetlib.job.model.properties.enums.JobRunnerKey;

import org.junit.jupiter.api.Test;

import java.util.Set;

class EnumPropertyDefinitionTest {

    @Test
    void testEncodeFromStringDecodeFromString() {
        final var definition =
                new EnumPropertyDefinition<>(
                        "RUNNER", "ABC", JobPropertyDefinitionGroup.RUNNER, JobRunnerKey.CURRENT);

        assertEquals(
                JobRunnerKey.CURRENT.name(), definition.encodeFromString(JobRunnerKey.CURRENT));
        assertEquals(
                JobRunnerKey.CURRENT,
                definition.decodeFromString(JobRunnerKey.CURRENT.name().toLowerCase()));
        assertEquals(
                JobRunnerKey.CURRENT,
                definition.decodeFromString(definition.encodeFromString(JobRunnerKey.CURRENT)));

        assertThrows(
                NullPointerException.class, () -> definition.decodeFromString("made up value"));
    }

    @Test
    void testConstructor() {
        final var definition =
                new EnumPropertyDefinition<>(
                        "RUNNER", "ABC", JobPropertyDefinitionGroup.RUNNER, JobRunnerKey.CURRENT);

        assertEquals("RUNNER", definition.key());
        assertEquals("ABC", definition.description());
        assertEquals(JobPropertyDefinitionGroup.RUNNER, definition.group());
        assertFalse(definition.nullable());
        assertTrue(definition.defaultValue().isPresent());
        assertEquals(JobRunnerKey.CURRENT, definition.defaultValue().get());
        assertTrue(definition.allowedValues().isPresent());
        assertEquals(Set.of(JobRunnerKey.values()), definition.allowedValues().get());
    }

    @Test
    void testRequiredFields() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new EnumPropertyDefinition<>(
                                null, null, JobPropertyDefinitionGroup.RUNNER, null));

        assertThrows(
                NullPointerException.class,
                () ->
                        new EnumPropertyDefinition<>(
                                null,
                                "ABC",
                                JobPropertyDefinitionGroup.RUNNER,
                                JobRunnerKey.CURRENT));

        assertThrows(
                NullPointerException.class,
                () ->
                        new EnumPropertyDefinition<>(
                                "RUNNER",
                                null,
                                JobPropertyDefinitionGroup.RUNNER,
                                JobRunnerKey.CURRENT));

        assertThrows(
                RuntimeException.class,
                () ->
                        new EnumPropertyDefinition<>(
                                "RUNNER",
                                " ",
                                JobPropertyDefinitionGroup.RUNNER,
                                JobRunnerKey.CURRENT));

        assertThrows(
                NullPointerException.class,
                () ->
                        new EnumPropertyDefinition<>(
                                "RUNNER", "ABC", JobPropertyDefinitionGroup.RUNNER, null));
    }
}
