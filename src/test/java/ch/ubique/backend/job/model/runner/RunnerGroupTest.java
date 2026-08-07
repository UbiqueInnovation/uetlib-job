package ch.ubique.backend.job.model.runner;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import ch.ubique.backend.job.cleanup.CleanupJob;

import java.util.Set;

class RunnerGroupTest {

    private final JsonMapper jsonMapper = JsonMapper.shared();

    @Test
    void differentRunnerGroupsWithSameIdentifier() {
        new RunnerGroup("TEST", 1);
        assertThrows(IllegalArgumentException.class, () -> new RunnerGroup("TEST", 2));
    }

    @Test
    void testDeserializeRunnerGroup() {
        final var validJson =
                "{\"identifier\":\"cleanup\",\"threadPoolSize\":1,\"allowedJobs\":[\"ch.ubique.backend.job.cleanup.CleanupJob\"]}";
        final var runnerGroup =
                assertDoesNotThrow(() -> jsonMapper.readValue(validJson, RunnerGroup.class));
        assertEquals("cleanup", runnerGroup.identifier());
        assertEquals(1, runnerGroup.threadPoolSize());
        assertEquals(Set.of(CleanupJob.class), runnerGroup.allowedJobs());
    }

    @Test
    void testDeserializeRunnerGroupMissingAllowedJobsFails() {
        final var invalidJson = "{\"identifier\":\"default\",\"threadPoolSize\":1}";
        assertThrows(
                JacksonException.class,
                () -> jsonMapper.readValue(invalidJson, RunnerGroup.class));
    }
}
