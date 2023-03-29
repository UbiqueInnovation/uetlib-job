package ch.ubique.backend.job.model.properties;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.stream.Collectors;

class JobPropertyDefinitionsTest {

    @Test
    void someDefinitions() {
        assertFalse(JobPropertyDefinitions.VALUES.isEmpty());
    }

    @Test
    void testNoDuplicateKeys() {
        final var keys =
                JobPropertyDefinitions.VALUES.stream()
                        .map(JobPropertyDefinition::key)
                        .collect(Collectors.toUnmodifiableSet());

        for (final var key : keys) {
            assertDoesNotThrow(
                    () -> JobPropertyDefinitions.get(key),
                    "Expected exactly one definition for all JobPropertyKey values. "
                            + "Please make sure "
                            + JobPropertyDefinitions.class
                            + " contains exactly one definition for "
                            + key);
        }
    }

    @Test
    void testNamesMatch() throws IllegalAccessException {
        for (final var field : JobPropertyDefinitions.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    || !(field.get(null) instanceof JobPropertyDefinition<?> definition)) {
                continue;
            }

            assertEquals(
                    definition.key(),
                    field.getName(),
                    "Name miss-match. Please rename field "
                            + field.getName()
                            + " in "
                            + JobPropertyDefinitions.class
                            + " to "
                            + definition.key());
        }
    }
}
