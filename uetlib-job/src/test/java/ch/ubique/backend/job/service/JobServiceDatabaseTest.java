package ch.ubique.backend.job.service;

import static org.junit.jupiter.api.Assertions.*;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.data.BaseJobDatabaseTest;
import ch.ubique.backend.job.storage.JobRunStorage;
import ch.ubique.backend.job.testjob.ProfileDisabledTestJob;
import ch.ubique.backend.job.testjob.ProfileEnabledTestJob;
import ch.ubique.backend.job.testjob.TestJob;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.util.Set;
import java.util.stream.Collectors;

class JobServiceDatabaseTest extends BaseJobDatabaseTest {

    @MockBean private JobRunStorage jobRunStorage;

    @SpyBean private JobService jobService;

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testInstantiateJob() {
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
        assertEquals(Set.of(TestJob.class, ProfileEnabledTestJob.class), allInstantiatedJobClasses);
    }
}
