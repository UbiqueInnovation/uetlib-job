package ch.ubique.backend.job.service;

import static org.junit.jupiter.api.Assertions.*;

import ch.ubique.backend.job.cleanup.CleanupJob;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.service.config.JobServiceTestConfig;
import ch.ubique.backend.job.service.core.ClassLoaderService;
import ch.ubique.backend.job.storage.JobRunStorage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

@SpringBootTest(classes = JobServiceTestConfig.class)
class JobServiceTest {

    @MockBean private JobDataService jobDataService;

    @MockBean private ClassLoaderService classLoaderService;

    @MockBean private JobRunStorage jobRunStorage;

    @SpyBean private JobService jobService;

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testInstantiateJob() {
        final var job1 = jobService.instantiateJob(CleanupJob.class);
        final var job2 = jobService.instantiateJob(CleanupJob.class);
        assertNotEquals(job1, job2);
    }
}
