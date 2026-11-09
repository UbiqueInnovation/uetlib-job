package ch.uetlib.job.model.properties;

import static org.junit.jupiter.api.Assertions.*;

import ch.uetlib.job.model.client.JobPropertyGroupInfo;

import org.junit.jupiter.api.Test;

class JobClassAndIdPropertyGroupInfoTest {

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
