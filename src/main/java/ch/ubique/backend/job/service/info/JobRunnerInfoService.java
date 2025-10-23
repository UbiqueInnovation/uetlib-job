package ch.ubique.backend.job.service.info;

import ch.ubique.backend.job.model.client.info.JobSystemRunnerGroupInfo;
import ch.ubique.backend.job.model.runner.RunnerGroup;
import ch.ubique.backend.job.service.executor.runners.CurrentJobRunner;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
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

    public JobRunnerInfoService(CurrentJobRunner currentJobRunner) {
        this.currentJobRunner = currentJobRunner;
    }

    public List<JobSystemRunnerGroupInfo> getRunnerGroupsInfo() {
        return currentJobRunner.getExecutorServices().entrySet().stream()
                .map(this::createGroupInfo)
                .sorted(Comparator.comparing(JobSystemRunnerGroupInfo::identifier))
                .collect(Collectors.toList());
    }

    private JobSystemRunnerGroupInfo createGroupInfo(
            final java.util.Map.Entry<RunnerGroup, ThreadPoolExecutor> entry) {
        final RunnerGroup group = entry.getKey();
        final ThreadPoolExecutor executor = entry.getValue();
        final ExecutorStats stats = collectExecutorStats(executor);

        return new JobSystemRunnerGroupInfo(
                group.identifier(),
                group.threadPoolSize(),
                getAllowedJobs(group),
                stats.busyThreads(),
                stats.queueLength(),
                null,
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
        return group.allowedJobs().stream().map(Class::getSimpleName).sorted().toList();
    }

    private record ExecutorStats(
            int busyThreads,
            int queueLength,
            long completedTaskCount,
            int largestPoolSize,
            int currentPoolSize) {}
}
