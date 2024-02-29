package ch.ubique.backend.job.service.executor.runners;

import static ch.ubique.backend.job.model.properties.JobPropertyDefinitions.RUNNER_GROUP;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.ubique.backend.job.cleanup.CleanupJob;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.heath.HealthCheckJob;
import ch.ubique.backend.job.model.*;
import ch.ubique.backend.job.model.properties.JobProperties;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.storage.JobRunStorage;

import org.junit.jupiter.api.Test;

class CurrentJobRunnerTest {

    @Test
    void key() {
        assertEquals(JobRunnerKey.CURRENT, new CurrentJobRunner(mock(JobService.class)).key());
    }

    @Test
    void submit() {
        final var runner = new CurrentJobRunner(mock(JobService.class));
        final var jobProperties = mock(JobProperties.class);
        when(jobProperties.get(RUNNER_GROUP))
                .thenReturn(RUNNER_GROUP.createProperty(HealthCheckJob.runnerGroup()));
        final var jobRunContext =
                new JobRunContext(
                        mock(JobClassAndId.class),
                        mock(JobRun.class),
                        mock(JobRunArguments.class),
                        jobProperties);
        assertTrue(runner.submit(new HealthCheckJob(), jobRunContext));
        assertFalse(
                runner.submit(
                        new CleanupJob(mock(JobRunStorage.class), mock(JobDataService.class)),
                        jobRunContext),
                "Only the HealthCheckJob can be submitted to the HealthCheckJob.runnerGroup().");
    }
}
