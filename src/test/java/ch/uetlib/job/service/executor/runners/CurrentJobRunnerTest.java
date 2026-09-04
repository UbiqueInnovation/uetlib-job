package ch.uetlib.job.service.executor.runners;

import static ch.uetlib.job.model.properties.JobPropertyDefinitions.RUNNER_GROUP;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.uetlib.job.cleanup.CleanupJob;
import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.health.HealthCheckJob;
import ch.uetlib.job.model.*;
import ch.uetlib.job.model.properties.JobProperties;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.service.JobService;
import ch.uetlib.job.storage.JobRunStorage;

import org.junit.jupiter.api.Test;

class CurrentJobRunnerTest {

    @Test
    void key() {
        assertEquals(JobRunnerKey.CURRENT, new CurrentJobRunner(mock(JobService.class)).key());
    }

    @Test
    void submit() {
        final var currentRunnerWithMock = new CurrentJobRunner(mock(JobService.class));
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
                        new CleanupJob(
                                mock(JobRunStorage.class),
                                mock(JobService.class),
                                mock(JobDataService.class)),
                        jobRunContext),
                "Only the HealthCheckJob can be submitted to the HealthCheckJob.runnerGroup().");
    }
}
