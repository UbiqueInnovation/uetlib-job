package ch.uetlib.job.checks;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ch.uetlib.job.Job;
import ch.uetlib.job.checks.cache.JobStateCheckEvaluationCache;
import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.properties.JobProperties;
import ch.uetlib.job.model.properties.JobPropertiesConfig;
import ch.uetlib.job.model.properties.JobProperty;
import ch.uetlib.job.model.properties.JobPropertyDefinitions;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;
import ch.uetlib.job.model.properties.enums.StateCheckStrategy;
import ch.uetlib.job.service.JobService;
import ch.uetlib.job.test.config.JobStateCheckTestConfig;
import ch.uetlib.job.testjob.TestJob;
import ch.uetlib.monitoring.check.models.CheckFailed;
import ch.uetlib.monitoring.check.models.CheckPassed;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@SpringBootTest(
        classes = {
            JobStateCheckTestConfig.class,
            JobErrorStateCheck.class,
            JobStateCheckEvaluationCache.class
        })
class JobStateCheckTest {

    private final JobClassAndId job = new JobClassAndId(1, TestJob.class.getCanonicalName());
    @Autowired private JobErrorStateCheck jobErrorStateCheck;
    @Autowired private JobStateCheckEvaluationCache jobStateCheckEvaluationCache;
    @MockitoBean private JobDataService jobDataService;
    @MockitoBean private JobService jobService;

    @BeforeEach
    void beforeEach() {
        jobStateCheckEvaluationCache.purge();
        when(jobService.getEnabledJobClassesWithId()).thenReturn(List.of(job));
        when(jobService.getJobClassMaybe(Mockito.<JobClassAndId>any()))
                .thenReturn(Optional.of(TestJob.class));
        when(jobService.instantiateJob(Mockito.<Class<? extends Job>>any()))
                .thenReturn(new TestJob());
    }

    @Test
    void checkFailWithStrategyCheckLatestRunFailed() {
        final var stateCheckStrategy = StateCheckStrategy.CHECK_LATEST_SUCCESSFUL;
        setupMockForStrategy(stateCheckStrategy);
        when(jobDataService.findLatestFinishedJobRuns(List.of(job.id())))
                .thenReturn(mockJobRunWith(JobRunState.ERROR));
        final var checkStatus = jobErrorStateCheck.check();
        assertInstanceOf(CheckFailed.class, checkStatus);
    }

    private void setupMockForStrategy(final StateCheckStrategy stateCheckStrategy) {
        when(jobService.getProperties((Job) any()))
                .thenReturn(
                        new JobProperties(
                                JobPropertiesConfig.fixed(
                                                new JobProperty<>(
                                                        JobPropertyDefinitions.STATE_CHECK_STRATEGY,
                                                        stateCheckStrategy))
                                        .properties()));
    }

    @Test
    void checkPassWithStrategyCheckLatestRunFailed() {
        setupMockForStrategy(StateCheckStrategy.CHECK_LATEST_SUCCESSFUL);
        when(jobDataService.findLatestFinishedJobRuns(List.of(job.id())))
                .thenReturn(mockJobRunWith(JobRunState.DONE));
        final var checkStatus = jobErrorStateCheck.check();
        assertInstanceOf(CheckPassed.class, checkStatus);
    }

    @Test
    void checkPassWithStrategyCheckSomeRunFailed() {
        setupMockForStrategy(StateCheckStrategy.CHECK_ALL_SUCCESSFUL);
        when(jobDataService.findJobRunStates(List.of(job.id())))
                .thenReturn(Map.of(job.id(), JobRunState.allExcept(Set.of(JobRunState.ERROR))));
        // Check passes, even if the job has had runs with problematic states; these will fail the
        // JobStateChecks for the respective problem states.
        final var checkStatus = jobErrorStateCheck.check();
        assertInstanceOf(CheckPassed.class, checkStatus);
    }

    @Test
    void checkFailWithStrategyCheckSomeRunFailed() {
        setupMockForStrategy(StateCheckStrategy.CHECK_ALL_SUCCESSFUL);
        when(jobDataService.findJobRunStates(List.of(job.id())))
                .thenReturn(Map.of(job.id(), Set.of(JobRunState.values())));
        final var checkStatus = jobErrorStateCheck.check();
        assertInstanceOf(CheckFailed.class, checkStatus);
    }

    @Test
    void checkPassWithStrategyCheckSomeRunInIntervalFailed() {
        setupMockForStrategy(StateCheckStrategy.CHECK_INTERVAL_ALL_SUCCESSFUL);
        when(jobDataService.findJobRunStatesSince(Mockito.anyMap()))
                .thenReturn(Map.of(job.id(), JobRunState.allExcept(Set.of(JobRunState.ERROR))));
        final var checkStatus = jobErrorStateCheck.check();
        assertInstanceOf(CheckPassed.class, checkStatus);
    }

    @Test
    void checkFailWithStrategyCheckSomeRunInIntervalFailed() {
        setupMockForStrategy(StateCheckStrategy.CHECK_INTERVAL_ALL_SUCCESSFUL);
        when(jobDataService.findJobRunStatesSince(Mockito.anyMap()))
                .thenReturn(Map.of(job.id(), Set.of(JobRunState.values())));
        final var checkStatus = jobErrorStateCheck.check();
        assertInstanceOf(CheckFailed.class, checkStatus);
    }

    @Test
    void checkPassWithStrategyCheckNoRunInIntervalSuccessful() {
        setupMockForStrategy(StateCheckStrategy.CHECK_INTERVAL_SOME_SUCCESSFUL);
        when(jobDataService.findJobRunStatesSince(Mockito.anyMap()))
                .thenReturn(Map.of(job.id(), Set.of(JobRunState.values())));
        final var checkStatus = jobErrorStateCheck.check();
        assertInstanceOf(CheckPassed.class, checkStatus);
    }

    @Test
    void checkFailWithStrategyCheckNoRunInIntervalSuccessful() {
        setupMockForStrategy(StateCheckStrategy.CHECK_INTERVAL_SOME_SUCCESSFUL);
        when(jobDataService.findJobRunStatesSince(Mockito.anyMap()))
                .thenReturn(Map.of(job.id(), JobRunState.allExcept(JobRunState.SUCCESS_STATES)));
        final var checkStatus = jobErrorStateCheck.check();
        assertInstanceOf(CheckFailed.class, checkStatus);
    }

    private List<JobRun> mockJobRunWith(final JobRunState jobRunState) {
        return List.of(
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
