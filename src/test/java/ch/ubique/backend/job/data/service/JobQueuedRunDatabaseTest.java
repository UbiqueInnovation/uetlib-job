package ch.ubique.backend.job.data.service;

import static org.junit.jupiter.api.Assertions.*;

import ch.ubique.backend.job.data.BaseJobDatabaseTest;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.runner.conditions.JobRunnerTaint;
import ch.ubique.backend.job.model.runner.conditions.JobRunnerTaints;
import ch.ubique.backend.job.service.inputs.JobRunInputManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Set;

/**
 * Exercises {@code t_job_run_queue}'s taint-matching query directly, since {@link
 * ch.ubique.backend.job.service.executor.JobRunQueueService}'s two polls
 * (checkForRunnableAndSchedule / checkForRunnableDedicatedAndSchedule) rely entirely on it and
 * have no dedicated coverage of their own.
 */
class JobQueuedRunDatabaseTest extends BaseJobDatabaseTest {

    private static final int TEST_JOB_ID = 1;

    @Autowired JobDataService jobDataService;
    @MockitoBean JobRunInputManager jobRunInputManager;

    @Test
    void untaggedRunIsFoundByPlainPoll() {
        jobDataService.queueNewRun(
                TEST_JOB_ID, null, "1.0.0", new JobRunArguments(), Set.of(), true);

        final var found = jobDataService.findFirstQueuedByNoOtherTaints(10, Set.of());
        assertTrue(
                found.stream().anyMatch(r -> r.jobId() == TEST_JOB_ID),
                "an untagged queued run should be visible to a poller with no taints");
    }

    @Test
    void untaggedRunIsAlsoFoundByDedicatedPoll() {
        // This demonstrates a real bug: checkForRunnableDedicatedAndSchedule() polls with
        // instanceTaints + "dedicated-k8s", and the underlying query is a subset match (row
        // taints subseteq poller taints) - so a row with NO taints at all also satisfies that
        // query, even though it was never tagged for dedicated/k8s dispatch. A plain CURRENT-
        // runner job can therefore be claimed by the dedicated poll and get routed to
        // KubernetesJobRunner purely by which @Scheduled poll wins the race.
        jobDataService.queueNewRun(
                TEST_JOB_ID, null, "1.0.0", new JobRunArguments(), Set.of(), true);

        final var dedicatedPollTaints = Set.of(JobRunnerTaints.DEDICATED_K8S_TAINT.value());
        final var found =
                jobDataService.findFirstQueuedByNoOtherTaints(10, dedicatedPollTaints);

        assertTrue(
                found.stream().anyMatch(r -> r.jobId() == TEST_JOB_ID),
                "BUG: an untagged run should NOT be claimable by the dedicated-only poll, but"
                        + " the subset-match query finds it anyway");
    }

    @Test
    void dedicatedTaggedRunIsNotFoundByPlainPoll() {
        jobDataService.queueNewRun(
                TEST_JOB_ID,
                null,
                "1.0.0",
                new JobRunArguments(),
                Set.of(JobRunnerTaints.DEDICATED_K8S_TAINT),
                true);

        final var found = jobDataService.findFirstQueuedByNoOtherTaints(10, Set.of());
        assertTrue(
                found.stream().noneMatch(r -> r.jobId() == TEST_JOB_ID),
                "a run tagged dedicated-k8s must not be claimable by the plain (no-taint) poll");
    }

    @Test
    void dedicatedTaggedRunIsFoundByDedicatedPoll() {
        jobDataService.queueNewRun(
                TEST_JOB_ID,
                null,
                "1.0.0",
                new JobRunArguments(),
                Set.of(JobRunnerTaints.DEDICATED_K8S_TAINT),
                true);

        final var dedicatedPollTaints = Set.of(JobRunnerTaints.DEDICATED_K8S_TAINT.value());
        final var found =
                jobDataService.findFirstQueuedByNoOtherTaints(10, dedicatedPollTaints);
        assertTrue(found.stream().anyMatch(r -> r.jobId() == TEST_JOB_ID));
    }

    @Test
    void runRequiringAnUnadvertisedTaintIsNeverFound() {
        jobDataService.queueNewRun(
                TEST_JOB_ID,
                null,
                "1.0.0",
                new JobRunArguments(),
                Set.of(new JobRunnerTaint("gpu")),
                true);

        final var found = jobDataService.findFirstQueuedByNoOtherTaints(10, Set.of());
        assertTrue(
                found.stream().noneMatch(r -> r.jobId() == TEST_JOB_ID),
                "a run requiring a taint the poller doesn't advertise must not be claimable");
    }
}
