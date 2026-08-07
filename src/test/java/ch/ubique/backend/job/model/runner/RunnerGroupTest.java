package ch.ubique.backend.job.model.runner;

import static org.junit.jupiter.api.Assertions.*;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.cleanup.CleanupJob;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

class RunnerGroupTest {

    private final JsonMapper jsonMapper = JsonMapper.shared();

    @Test
    void differentRunnerGroupsWithSameIdentifier() {
        new RunnerGroup("TEST", 1);
        assertThrows(IllegalArgumentException.class, () -> new RunnerGroup("TEST", 2));
    }

    @ParameterizedTest
    @CsvSource(
            """
            '{"identifier":"cleanup","threadPoolSize":1,"allowedJobs":["ch.ubique.backend.job.cleanup.CleanupJob"]}', cleanup, 1, ch.ubique.backend.job.cleanup.CleanupJob
            '{"identifier":"test","threadPoolSize":1}', test, 1,
            '{"identifier":"test","threadPoolSize":1,"allowedJobs":null}', test, 1,
            '{"identifier":"test","threadPoolSize":1,"allowedJobs":[]}', test, 1,
            """)
    void testDeserializeRunnerGroup(
            final String json,
            final String expectedIdentifier,
            final int expectedThreadPoolSize,
            final @Nullable Class<? extends Job> expectedAllowedJob) {
        final var runnerGroup =
                assertDoesNotThrow(() -> jsonMapper.readValue(json, RunnerGroup.class));
        assertEquals(expectedIdentifier, runnerGroup.identifier());
        assertEquals(expectedThreadPoolSize, runnerGroup.threadPoolSize());
        if (expectedAllowedJob != null) {
            assertEquals(Set.of(expectedAllowedJob), runnerGroup.allowedJobs());
        } else {
            assertEquals(Set.of(), runnerGroup.allowedJobs());
        }
    }

    @Test
    void testSerializationRoundtrip() {
        final var original = new RunnerGroup("roundtrip", 2, Set.of(CleanupJob.class));
        final var json = assertDoesNotThrow(() -> jsonMapper.writeValueAsString(original));
        final var deserialized =
                assertDoesNotThrow(() -> jsonMapper.readValue(json, RunnerGroup.class));
        assertEquals(original, deserialized);
    }

    @Test
    void testDeserializeRunnerGroupMissingIdentifierFails() {
        final var invalidJson = "{\"threadPoolSize\":1}";
        assertThrows(
                JacksonException.class, () -> jsonMapper.readValue(invalidJson, RunnerGroup.class));
    }

    @Test
    void testDeserializeRunnerGroupMissingThreadPoolSizeFails() {
        final var invalidJson = "{\"identifier\":\"test\"}";
        assertThrows(
                JacksonException.class, () -> jsonMapper.readValue(invalidJson, RunnerGroup.class));
    }
}
