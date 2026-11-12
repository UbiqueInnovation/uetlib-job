package ch.uetlib.job.model.properties;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.stream.Collectors;

class JobClassAndIdPropertyDefinitionsTest {

    /**
     * Fields deliberately named differently from their persisted key - kept stable for
     * backward-compatible configs/APIs - while the Java name was clarified.
     */
    private static final Set<String> NAME_MISMATCH_EXCEPTIONS = Set.of();

    @Test
    void someDefinitions() {
        assertFalse(JobPropertyDefinitions.values().isEmpty());
    }

    /** The status interface shows each group as its own tab. */
    @Test
    void queueGroupHoldsThePropertiesThatOnlyAffectTheQueue() {
        final var queueKeys =
                JobPropertyDefinitions.values().stream()
                        .filter(
                                definition ->
                                        definition.group() == JobPropertyDefinitionGroup.QUEUE)
                        .map(JobPropertyDefinition::key)
                        .collect(Collectors.toUnmodifiableSet());

        assertEquals(
                Set.of(
                        "QUEUE_BEHAVIOR",
                        "PRIORITY",
                        "JOB_RUN_TAINTS",
                        "CLAIM_TIMEOUT",
                        "QUEUE_TIMEOUT",
                        "CANCELLED_QUEUE_RETENTION"),
                queueKeys);
    }

    @Test
    void testNoDuplicateKeys() {
        final var keys =
                JobPropertyDefinitions.values().stream()
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
                    || !Modifier.isPublic(field.getModifiers())
                    || !(field.get(null) instanceof final JobPropertyDefinition<?> definition)
                    || NAME_MISMATCH_EXCEPTIONS.contains(field.getName())) {
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
