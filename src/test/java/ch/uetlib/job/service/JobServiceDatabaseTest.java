package ch.uetlib.job.service;

import static org.junit.jupiter.api.Assertions.*;

import ch.uetlib.job.Job;
import ch.uetlib.job.cleanup.CleanupJob;
import ch.uetlib.job.data.BaseJobDatabaseTest;
import ch.uetlib.job.health.HealthCheckJob;
import ch.uetlib.job.storage.JobRunStorage;
import ch.uetlib.job.testjob.ProfileDisabledTestJob;
import ch.uetlib.job.testjob.ProfileEnabledTestJob;
import ch.uetlib.job.testjob.TestJob;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Set;
import java.util.stream.Collectors;

class JobServiceDatabaseTest extends BaseJobDatabaseTest {

    @MockitoBean private JobRunStorage jobRunStorage;

    @Autowired private JobService jobService;

    @Test
    void testInstantiateJobNotEqualInstance() {
        final var job1 = jobService.instantiateJob(TestJob.class);
        final var job2 = jobService.instantiateJob(TestJob.class);
        assertNotEquals(job1, job2);
    }

    @Test
    void testGetAllJobs() {
        final var allInstantiatedJobClasses =
                jobService.getAllJobs().stream()
                        .map(Job::getClass)
                        .collect(Collectors.toUnmodifiableSet());
        assertFalse(allInstantiatedJobClasses.contains(ProfileDisabledTestJob.class));
        // ch.uetlib is always scanned by @EnableJobs, so the framework's own jobs are
        // discovered here too, alongside this test's own job packages.
        assertEquals(
                Set.of(
                        TestJob.class,
                        ProfileEnabledTestJob.class,
                        CleanupJob.class,
                        HealthCheckJob.class),
                allInstantiatedJobClasses);
    }
}
