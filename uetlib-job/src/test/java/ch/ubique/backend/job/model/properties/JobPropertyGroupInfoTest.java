package ch.ubique.backend.job.model.properties;

import static org.junit.jupiter.api.Assertions.*;

import ch.ubique.backend.job.model.client.JobPropertyGroupInfo;

import org.junit.jupiter.api.Test;

class JobPropertyGroupInfoTest {

    @Test
    void testCapitalize() {
        assertEquals(
                "Monitoring",
                new JobPropertyGroupInfo(JobPropertyDefinitionGroup.MONITORING).title());

        assertEquals("Runner", new JobPropertyGroupInfo(JobPropertyDefinitionGroup.RUNNER).title());

        assertEquals(
                "Cleanup", new JobPropertyGroupInfo(JobPropertyDefinitionGroup.CLEANUP).title());
    }
}
