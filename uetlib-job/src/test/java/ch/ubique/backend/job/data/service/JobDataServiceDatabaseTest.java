package ch.ubique.backend.job.data.service;

import static org.junit.jupiter.api.Assertions.*;

import ch.ubique.backend.job.data.BaseJobDatabaseTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JobDataServiceDatabaseTest extends BaseJobDatabaseTest {
    @Autowired JobDataService jobDataService;

    @Test
    void testSetup() {
        assertNotNull(jobDataService);
    }

    @Test
    void testDeleteLogsForJobRunId() {
        assertDoesNotThrow(() -> jobDataService.deleteLogsForJobRunId(42));
    }

    @Test
    void testFindLogsForJobRun() {
        assertEquals(0, jobDataService.findLogsForJobRun(42, 0, 1000).count());
    }
}
