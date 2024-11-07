package ch.ubique.backend.job.model.runner;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RunnerGroupTest {

    @Test
    void differentRunnerGroupsWithSameIdentifier() {
        new RunnerGroup("TEST", 1);
        assertThrows(IllegalArgumentException.class, () -> new RunnerGroup("TEST", 2));
    }
}
