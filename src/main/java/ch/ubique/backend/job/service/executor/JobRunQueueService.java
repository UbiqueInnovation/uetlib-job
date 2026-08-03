package ch.ubique.backend.job.service.executor;

import static java.util.Objects.requireNonNull;

import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobQueuedRun;
import ch.ubique.backend.job.model.executer.SuccessfulSubmissionResult;
import ch.ubique.backend.job.model.executer.UnsuccessfulSubmissionResult;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.model.runner.conditions.JobRunnerTaints;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.service.info.JobRunnerInfoService;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Profile("!job-run")
public class JobRunQueueService {
    private static final Logger logger = LoggerFactory.getLogger(JobRunQueueService.class);

    private final JobService jobService;
    private final JobRunnerInfoService jobRunnerServiceInfo;
    private final JobExecutorService jobExecutorService;

    private final String instanceName;
    private final Set<String> instanceTaints;
    private final int maxRunnablePerCheck;

    public JobRunQueueService(
            final JobService jobService,
            final JobRunnerInfoService jobRunnerServiceInfo,
            final JobExecutorService jobExecutorService,
            @Value("${ch.ubique.backend.job.runner.instance.name}") final String instanceName,
            @Value("${ch.ubique.backend.job.runner.instance.taints:null}")
                    final @Nullable List<String> instanceTaints,
            @Value("${ch.ubique.backend.job.runner.max.runnable.per.check:8}")
                    final int maxRunnablePerCheck) {
        this.jobService = jobService;
        this.jobRunnerServiceInfo = jobRunnerServiceInfo;
        this.jobExecutorService = jobExecutorService;
        this.instanceName = instanceName;
        this.instanceTaints = Set.copyOf(Optional.ofNullable(instanceTaints).orElse(List.of()));
        this.maxRunnablePerCheck = maxRunnablePerCheck;
    }

    @Scheduled(cron = "*/5 * * * * ?")
    public void checkForRunnableAndSchedule() {
        final var runnableOnThisRunner =
                jobService.findQueuedByTaints(maxRunnablePerCheck, instanceTaints);
        if (runnableOnThisRunner.isEmpty()) {
            logger.trace(
                    "No more job runs to run on this runner (instanceName={}, instanceTaints={})",
                    instanceName,
                    instanceTaints);
            return;
        }
        runnableOnThisRunner.forEach(this::claimAndExecuteQueuedRun);
    }

    @Scheduled(cron = "*/5 * * * * ?")
    public void checkForRunnableDedicatedAndSchedule() {
        final var dedicatedTaints = new HashSet<>(instanceTaints);
        dedicatedTaints.add(JobRunnerTaints.DEDICATED_K8S_TAINT.value());
        final var runnableOnDedicatedFromThisRunner =
                jobService.findQueuedByTaints(maxRunnablePerCheck, dedicatedTaints);
        if (runnableOnDedicatedFromThisRunner.isEmpty()) {
            logger.trace("No more job runs to schedule on dedicated from this runner");
            return;
        }
        runnableOnDedicatedFromThisRunner.forEach(this::claimAndExecuteDedicatedQueuedRun);
    }

    private void claimAndExecuteQueuedRun(final JobQueuedRun jobQueuedRun) {
        claimAndExecuteQueuedRun(jobQueuedRun, false);
    }

    private void claimAndExecuteDedicatedQueuedRun(final JobQueuedRun jobQueuedRun) {
        if (!jobService.hasRunner(JobRunnerKey.DEDICATED_K8S_JOB)) {
            logger.info(
                    "Cannot execute {} here, because there is no dedicated runner type for {} available.",
                    JobRunnerKey.DEDICATED_K8S_JOB,
                    jobQueuedRun);
            return;
        }
        claimAndExecuteQueuedRun(jobQueuedRun, true);
    }

    private void claimAndExecuteQueuedRun(
            final JobQueuedRun jobQueuedRun, final boolean isDedicated) {
        final var jobClass = jobService.findJobById(jobQueuedRun.jobId()).orElseThrow();
        if (!isDedicated && !hasCapacity(jobClass)) {
            return;
        }
        if (!jobService.claimQueuedRun(jobQueuedRun, instanceName)) {
            logger.debug(
                    "Failed to claim queued run {}, appears to already have been claimed.",
                    jobQueuedRun);
            return;
        }
        switch (jobExecutorService.submit(jobQueuedRun, isDedicated)) {
            // TODO: Java 25 unnamed patterns
            case SuccessfulSubmissionResult(
                            final var ignored,
                            final var ignored1,
                            final var runId) ->
                    jobService.setRunId(jobQueuedRun, runId);
            case UnsuccessfulSubmissionResult(final var status) -> {
                switch (status) {
                    case SUBMITTED ->
                            logger.warn(
                                    "UnsuccessfulSubmissionResult should not receive submitted");
                    case DISALLOWED ->
                            logger.warn(
                                    "Failed to submit job with status: {}, this is unlikely to solve itself",
                                    status);
                    case FAILED -> logger.info("Failed to submit job, will retry.");
                }
            }
        }
    }

    private boolean hasCapacity(final JobClassAndId jobClass) {
        final var runnerGroup =
                requireNonNull(
                        jobService
                                .getProperties(jobClass)
                                .get(JobPropertyDefinitions.RUNNER_GROUP)
                                .value());
        return jobRunnerServiceInfo.getRunnerGroupsInfo().stream()
                .filter(g -> g.identifier().equals(runnerGroup.identifier()))
                .findAny()
                .map(g -> g.busyThreads() < g.currentPoolSize())
                .orElse(true);
    }
}
