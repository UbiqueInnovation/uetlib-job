package ch.ubique.backend.job.service.executor;

import static java.util.Objects.requireNonNull;

import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobQueuedRun;
import ch.ubique.backend.job.model.executer.SuccessfulSubmissionResult;
import ch.ubique.backend.job.model.executer.UnsuccessfulSubmissionResult;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;
import ch.ubique.backend.job.model.queue.JobRunQueueState;
import ch.ubique.backend.job.model.runner.conditions.JobRunnerTaint;
import ch.ubique.backend.job.model.runner.conditions.JobRunnerTaints;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.service.info.JobRunnerInfoService;

import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

    private final AtomicBoolean checkForRunnableInProgress = new AtomicBoolean(false);
    private final AtomicBoolean checkForRunnableDedicatedInProgress = new AtomicBoolean(false);

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
        runExclusively(
                checkForRunnableInProgress,
                "checkForRunnableAndSchedule",
                () -> processQueue(instanceTaints, this::claimAndExecuteQueuedRun));
    }

    @Scheduled(cron = "*/5 * * * * ?")
    public void checkForRunnableDedicatedAndSchedule() {
        runExclusively(
                checkForRunnableDedicatedInProgress,
                "checkForRunnableDedicatedAndSchedule",
                () -> processQueue(getDedicatedTaints(), this::claimAndExecuteDedicatedQueuedRun));
    }

    /**
     * Skips {@code task} instead of running it concurrently with itself: {@code @Scheduled(cron =
     * ...)} fires on a fixed schedule regardless of whether the previous invocation has finished,
     * so a slow poll (or a scheduler with more than one thread) could otherwise overlap with
     * itself.
     */
    private void runExclusively(
            final AtomicBoolean inProgress, final String taskName, final Runnable task) {
        if (!inProgress.compareAndSet(false, true)) {
            logger.trace("Skipping {} - previous invocation is still running.", taskName);
            return;
        }
        try {
            task.run();
        } finally {
            inProgress.set(false);
        }
    }

    /**
     * Processes a queue of job runs, claiming and executing those that match the specified taints.
     * Oldest first, once a page has a schedulable run, the rest of the page is processed and no
     * more pages are loaded.
     */
    private void processQueue(final Set<String> taints, final Consumer<JobQueuedRun> claimer) {
        var pageNumber = 0;
        while (true) {
            final var page =
                    jobService.findQueuedRunsPage(PageRequest.of(pageNumber, maxRunnablePerCheck));
            final var matches =
                    page.getContent().stream().filter(run -> matchesTaints(run, taints)).toList();
            if (!matches.isEmpty()) {
                matches.forEach(claimer);
                return;
            }
            if (!page.hasNext()) {
                logger.trace(
                        "No more job runs to run on this runner (instanceName={}, taints={})",
                        instanceName,
                        taints);
                return;
            }
            pageNumber++;
        }
    }

    /**
     * Fast path for {@link PgQueueInsertionListenService}: instead of re-running the full paged
     * poll, fetches exactly the notified rows and claims/executes only those that actually match
     * this instance's taints. Newly-inserted rows this instance doesn't match are ignored here -
     * they're still picked up normally by {@link #checkForRunnableAndSchedule} / {@link
     * #checkForRunnableDedicatedAndSchedule} on whichever instance they do match.
     */
    public void checkForRunnable(final List<Pair<Integer, Integer>> jobIdToQueueRunId) {
        final var newInserts =
                jobService.findQueuedByIds(jobIdToQueueRunId.stream().map(Pair::getRight).toList());
        newInserts.forEach(this::claimIfMatchesThisRunner);
    }

    private void claimIfMatchesThisRunner(final JobQueuedRun jobQueuedRun) {
        if (jobQueuedRun.queueState() != JobRunQueueState.QUEUED) {
            return;
        }
        if (matchesTaints(jobQueuedRun, instanceTaints)) {
            claimAndExecuteQueuedRun(jobQueuedRun);
        } else if (matchesTaints(jobQueuedRun, getDedicatedTaints())) {
            claimAndExecuteDedicatedQueuedRun(jobQueuedRun);
        }
    }

    /** The single place taint subset-matching (row's taints ⊆ {@code taints}) is implemented. */
    private static boolean matchesTaints(
            final JobQueuedRun jobQueuedRun, final Set<String> taints) {
        return taints.containsAll(
                jobQueuedRun.taints().stream()
                        .map(JobRunnerTaint::value)
                        .collect(Collectors.toSet()));
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
                jobService.releaseQueuedRun(jobQueuedRun);
                switch (status) {
                    case SUBMITTED ->
                            logger.warn(
                                    "UnsuccessfulSubmissionResult should not receive submitted, but has been released again.");
                    case DISALLOWED ->
                            logger.warn(
                                    "Failed to submit job with status: {}, released for retry.",
                                    status);
                    case FAILED -> logger.info("Failed to submit job, released for retry.");
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

    private HashSet<String> getDedicatedTaints() {
        final var dedicatedTaints = new HashSet<>(instanceTaints);
        dedicatedTaints.add(JobRunnerTaints.DEDICATED_K8S_TAINT.value());
        return dedicatedTaints;
    }
}
