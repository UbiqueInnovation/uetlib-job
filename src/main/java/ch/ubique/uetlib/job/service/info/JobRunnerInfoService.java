package ch.ubique.uetlib.job.service.info;

import ch.ubique.uetlib.job.Job;
import ch.ubique.uetlib.job.model.client.info.JobSystemRunnerGroupInfo;
import ch.ubique.uetlib.job.model.properties.JobPropertyDefinitions;
import ch.ubique.uetlib.job.model.runner.RunnerGroup;
import ch.ubique.uetlib.job.service.JobService;
import ch.ubique.uetlib.job.service.executor.runners.CurrentJobRunner;
import ch.ubique.uetlib.job.service.executor.runners.CurrentRunnerItem;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Service for retrieving information about job runner groups and their associated thread pool
 * executors. Provides detailed statistics for each runner group, including thread pool metrics and
 * allowed job classes.
 */
@Service
@Profile("!job-run")
public class JobRunnerInfoService {

    private final CurrentJobRunner currentJobRunner;
    private final JobService jobService;

    public JobRunnerInfoService(
            final CurrentJobRunner currentJobRunner, final JobService jobService) {
        this.currentJobRunner = currentJobRunner;
        this.jobService = jobService;
    }

    public List<JobSystemRunnerGroupInfo> getRunnerGroupsInfo() {
        final var configuredJobsByGroup = getConfiguredJobsByGroup();

        return currentJobRunner.getExecutorServices().entrySet().stream()
                .map(entry -> createGroupInfo(entry, configuredJobsByGroup))
                .sorted(Comparator.comparing(JobSystemRunnerGroupInfo::identifier))
                .toList();
    }

    private JobSystemRunnerGroupInfo createGroupInfo(
            final java.util.Map.Entry<RunnerGroup, ThreadPoolExecutor> entry,
            final Map<String, List<String>> configuredJobsByGroup) {
        final var group = entry.getKey();
        final var executor = entry.getValue();
        final var stats = collectExecutorStats(executor);

        final var queued =
                java.util.Objects.requireNonNullElse(
                        currentJobRunner.getQueuedJobRuns().get(entry.getKey()),
                        java.util.Collections.<CurrentRunnerItem>emptyList());
        final var active =
                java.util.Objects.requireNonNullElse(
                        currentJobRunner.getActiveJobRuns().get(entry.getKey()),
                        java.util.Collections.<CurrentRunnerItem>emptyList());

        return new JobSystemRunnerGroupInfo(
                group.identifier(),
                group.threadPoolSize(),
                getAllowedJobs(group),
                configuredJobsByGroup.getOrDefault(group.identifier(), List.of()),
                stats.busyThreads(),
                stats.queueLength(),
                queued,
                active,
                stats.completedTaskCount(),
                stats.largestPoolSize(),
                stats.currentPoolSize());
    }

    private ExecutorStats collectExecutorStats(final ThreadPoolExecutor executor) {
        return new ExecutorStats(
                executor.getActiveCount(),
                executor.getQueue().size(),
                executor.getCompletedTaskCount(),
                executor.getLargestPoolSize(),
                executor.getPoolSize());
    }

    private List<String> getAllowedJobs(final RunnerGroup group) {
        return group.allowedJobs().stream().map(Class::getCanonicalName).sorted().toList();
    }

    private Map<String, List<String>> getConfiguredJobsByGroup() {
        return jobService.getAllJobs().stream()
                .collect(
                        Collectors.groupingBy(
                                job ->
                                        java.util.Objects.requireNonNullElse(
                                                        jobService
                                                                .getProperties(job)
                                                                .get(
                                                                        JobPropertyDefinitions
                                                                                .RUNNER_GROUP)
                                                                .value(),
                                                        JobPropertyDefinitions.RUNNER_GROUP
                                                                .defaultValue()
                                                                .orElseThrow())
                                                .identifier(),
                                Collectors.mapping(
                                        Job::getClass,
                                        Collectors.mapping(
                                                Class::getCanonicalName, Collectors.toList()))))
                .entrySet()
                .stream()
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue().stream().sorted().toList()));
    }

    private record ExecutorStats(
            int busyThreads,
            int queueLength,
            long completedTaskCount,
            int largestPoolSize,
            int currentPoolSize) {}
}
