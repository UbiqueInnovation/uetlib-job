package ch.ubique.backend.job.service.info;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.client.info.JobSystemRunnerGroupInfo;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.runner.RunnerGroup;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.service.executor.runners.CurrentJobRunner;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * Service for retrieving information about job runner groups and their associated thread pool
 * executors. Provides detailed statistics for each runner group, including thread pool metrics and
 * allowed job classes.
 */
@Service
public class JobRunnerInfoService {

    private final CurrentJobRunner currentJobRunner;
    private final JobService jobService;

    public JobRunnerInfoService(CurrentJobRunner currentJobRunner, JobService jobService) {
        this.currentJobRunner = currentJobRunner;
        this.jobService = jobService;
    }

    public List<JobSystemRunnerGroupInfo> getRunnerGroupsInfo() {
        Map<String, List<String>> configuredJobsByGroup = getConfiguredJobsByGroup();

        return currentJobRunner.getExecutorServices().entrySet().stream()
                .map(entry -> createGroupInfo(entry, configuredJobsByGroup))
                .sorted(Comparator.comparing(JobSystemRunnerGroupInfo::identifier))
                .toList();
    }

    private JobSystemRunnerGroupInfo createGroupInfo(
            final java.util.Map.Entry<RunnerGroup, ThreadPoolExecutor> entry,
            final Map<String, List<String>> configuredJobsByGroup) {
        final RunnerGroup group = entry.getKey();
        final ThreadPoolExecutor executor = entry.getValue();
        final ExecutorStats stats = collectExecutorStats(executor);

        return new JobSystemRunnerGroupInfo(
                group.identifier(),
                group.threadPoolSize(),
                getAllowedJobs(group),
                configuredJobsByGroup.getOrDefault(group.identifier(), List.of()),
                stats.busyThreads(),
                stats.queueLength(),
                currentJobRunner.getQueuedJobRuns().get(entry.getKey()),
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
                                        jobService
                                                .getProperties(job)
                                                .get(JobPropertyDefinitions.RUNNER_GROUP)
                                                .value()
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
