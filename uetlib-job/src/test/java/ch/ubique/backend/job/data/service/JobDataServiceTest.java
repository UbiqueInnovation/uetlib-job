package ch.ubique.backend.job.data.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestEntityManager;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base test class for job service tests. Currently, only some queries are executed to check if the
 * sql syntax is valid on each database system.
 */
@Transactional
@AutoConfigureTestEntityManager
@EnableJpaRepositories(basePackages = {"ch.ubique.backend.job.data.repository"})
@EntityScan(basePackages = {"ch.ubique.backend.job.model.entity"})
public abstract class JobDataServiceTest {

    @Autowired JobDataService jobDataService;

    @Test
    public void testSetup() {
        assertNotNull(jobDataService);
    }

    @Test
    public void testDeleteLogsForJobRunId() {
        jobDataService.deleteLogsForJobRunId(42);
    }

    @Test
    public void testFindLogsForJobRun() {
        jobDataService.findLogsForJobRun(42, 0, 1000);
    }
}
