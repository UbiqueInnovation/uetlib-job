package ch.ubique.uetlib.job.service;

import static org.junit.jupiter.api.Assertions.*;

import ch.ubique.uetlib.job.Job;
import ch.ubique.uetlib.job.data.BaseJobDatabaseTest;
import ch.ubique.uetlib.job.storage.JobRunStorage;
import ch.ubique.uetlib.job.testjob.ProfileDisabledTestJob;
import ch.ubique.uetlib.job.testjob.ProfileEnabledTestJob;
import ch.ubique.uetlib.job.testjob.TestJob;

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
        assertEquals(Set.of(TestJob.class, ProfileEnabledTestJob.class), allInstantiatedJobClasses);
    }
}
