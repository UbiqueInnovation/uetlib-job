package ch.ubique.backend.job.checks;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.JobProperties;
import ch.ubique.backend.job.model.properties.JobPropertiesConfig;
import ch.ubique.backend.job.model.properties.JobProperty;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.model.properties.enums.StateCheckStrategy;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.test.config.JobStateCheckTestConfig;
import ch.ubique.backend.job.testjob.TestJob;
import ch.ubique.backend.monitoring.check.models.CheckFailed;
import ch.ubique.backend.monitoring.check.models.CheckPassed;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@SpringBootTest(classes = {JobStateCheckTestConfig.class, JobErrorStateCheck.class})
class JobStateCheckTest {

    private final JobClassAndId job = new JobClassAndId(1, TestJob.class.getCanonicalName());
    @Autowired private JobErrorStateCheck jobErrorStateCheck;
    @MockitoBean private JobDataService jobDataService;
    @MockitoBean private JobService jobService;

    @BeforeEach
    void beforeEach() {
        Mockito.when(jobDataService.findJobs()).thenReturn(List.of(job));
    }

    @Test
    void checkFailWithStrategyCheckLatestRunFailed() throws ClassNotFoundException {

        final var stateCheckStrategy = StateCheckStrategy.CHECK_LATEST_SUCCESSFUL;
        mock(stateCheckStrategy);
        Mockito.when(
                        jobDataService.findLatestJobRunByStates(
                                job.id(), JobRunState.ALL_EXCEPT_RUNNING_STATES))
                .thenReturn(mockJobRunWith(JobRunState.ERROR));
        final var checkStatus = jobErrorStateCheck.check();
        assertInstanceOf(CheckFailed.class, checkStatus);
    }

    private void mock(final StateCheckStrategy stateCheckStrategy) throws ClassNotFoundException {
        Mockito.when(jobService.getProperties(job))
                .thenReturn(
                        new JobProperties(
                                JobPropertiesConfig.fixed(
                                                new JobProperty<>(
                                                        JobPropertyDefinitions.STATE_CHECK_STRATEGY,
                                                        stateCheckStrategy))
                                        .properties()));
    }

    @Test
    void checkPassWithStrategyCheckLatestRunFailed() throws ClassNotFoundException {
        mock(StateCheckStrategy.CHECK_LATEST_SUCCESSFUL);
        Mockito.when(
                        jobDataService.findLatestJobRunByStates(
                                job.id(), JobRunState.ALL_EXCEPT_RUNNING_STATES))
                .thenReturn(mockJobRunWith(JobRunState.DONE));
        final var checkStatus = jobErrorStateCheck.check();
        assertInstanceOf(CheckPassed.class, checkStatus);
    }

    @Test
    void checkPassWithStrategyCheckSomeRunFailed() throws ClassNotFoundException {
        mock(StateCheckStrategy.CHECK_ALL_SUCCESSFUL);
        Mockito.when(jobDataService.findLatestJobRunByState(job.id(), JobRunState.ERROR))
                .thenReturn(Optional.empty());
        final var checkStatus = jobErrorStateCheck.check();
        assertInstanceOf(CheckPassed.class, checkStatus);
    }

    @Test
    void checkFailWithStrategyCheckSomeRunFailed() throws ClassNotFoundException {
        mock(StateCheckStrategy.CHECK_ALL_SUCCESSFUL);
        Mockito.when(jobDataService.findLatestJobRunByState(job.id(), JobRunState.ERROR))
                .thenReturn(mockJobRunWith(JobRunState.ERROR));
        final var checkStatus = jobErrorStateCheck.check();
        assertInstanceOf(CheckFailed.class, checkStatus);
    }

    @Test
    void checkPassWithStrategyCheckSomeRunInIntervalFailed() throws ClassNotFoundException {
        mock(StateCheckStrategy.CHECK_INTERVAL_ALL_SUCCESSFUL);
        Mockito.when(
                        jobDataService.findAnyJobRunInStatesSince(
                                eq(job.id()), eq(Set.of(JobRunState.ERROR)), any()))
                .thenReturn(Optional.empty());
        final var checkStatus = jobErrorStateCheck.check();
        assertInstanceOf(CheckPassed.class, checkStatus);
    }

    @Test
    void checkFailWithStrategyCheckSomeRunInIntervalFailed() throws ClassNotFoundException {
        mock(StateCheckStrategy.CHECK_INTERVAL_ALL_SUCCESSFUL);
        Mockito.when(
                        jobDataService.findAnyJobRunInStatesSince(
                                eq(job.id()), eq(Set.of(JobRunState.ERROR)), any()))
                .thenReturn(mockJobRunWith(JobRunState.ERROR));
        final var checkStatus = jobErrorStateCheck.check();
        assertInstanceOf(CheckFailed.class, checkStatus);
    }

    @Test
    void checkPassWithStrategyCheckNoRunInIntervalSuccessful() throws ClassNotFoundException {
        mock(StateCheckStrategy.CHECK_INTERVAL_SOME_SUCCESSFUL);
        Mockito.when(
                        jobDataService.findAnyJobRunInStatesSince(
                                eq(job.id()), eq(JobRunState.SUCCESS_STATES), any()))
                .thenReturn(mockJobRunWith(JobRunState.DONE));
        final var checkStatus = jobErrorStateCheck.check();
        assertInstanceOf(CheckPassed.class, checkStatus);
    }

    @Test
    void checkFailWithStrategyCheckNoRunInIntervalSuccessful() throws ClassNotFoundException {
        mock(StateCheckStrategy.CHECK_INTERVAL_SOME_SUCCESSFUL);
        Mockito.when(
                        jobDataService.findAnyJobRunInStatesSince(
                                eq(job.id()), eq(JobRunState.SUCCESS_STATES), any()))
                .thenReturn(Optional.empty());
        final var checkStatus = jobErrorStateCheck.check();
        assertInstanceOf(CheckFailed.class, checkStatus);
    }

    private @NotNull Optional<JobRun> mockJobRunWith(final JobRunState jobRunState) {
        return Optional.of(
                new JobRun(
                        12,
                        job.id(),
                        "1.0.0",
                        Instant.now(),
                        Instant.now(),
                        jobRunState,
                        JobRunnerKey.CURRENT,
                        null,
                        null));
    }
}
