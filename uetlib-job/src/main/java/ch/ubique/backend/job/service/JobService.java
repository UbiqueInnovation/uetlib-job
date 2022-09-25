package ch.ubique.backend.job.service;

import ch.ubique.backend.core.exception.ResourceNotFoundException;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.client.JobInfosContainer;
import ch.ubique.backend.job.model.client.JobRunInfo;
import ch.ubique.backend.job.model.client.JobRunInfoContainer;
import ch.ubique.backend.job.model.client.JobRunInfosContainer;
import ch.ubique.backend.job.scheduler.JobScheduler;

import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JobService {

    private static final int PAGE_SIZE = 30;

    private final JobDataService jobDataService;
    private final JobScheduler jobScheduler;

    public JobService(final JobDataService jobDataService, final JobScheduler jobScheduler) {
        this.jobDataService = jobDataService;
        this.jobScheduler = jobScheduler;
    }

    public JobInfosContainer findJobs() {
        final var jobs = jobDataService.findJobs();
        final var latestRuns = findLatestJobRuns(jobs);
        return JobInfosContainer.from(jobs, latestRuns);
    }

    public Map<Integer, JobRunInfo> findLatestJobRuns(final List<Job> jobs) {
        return jobs.stream()
                .map(
                        job ->
                                jobDataService
                                        .findLatestJobRun(job.getId())
                                        .map(JobRunInfo::from)
                                        .orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(JobRunInfo::jobId, Function.identity()));
    }

    public JobRunInfosContainer findJobRuns(
            final int page, final Integer jobId, final JobRunState state) {
        final var jobNames =
                jobDataService.findJobs().stream()
                        .collect(Collectors.toUnmodifiableMap(Job::getId, Job::getName));
        final var jobRunsPage = jobDataService.findJobRuns(page, PAGE_SIZE, jobId, state);
        final var jobRuns = jobRunsPage.stream().map(JobRunInfo::from).toList();
        return new JobRunInfosContainer(
                jobRuns,
                jobNames,
                jobRunsPage.isFirst(),
                jobRunsPage.isLast(),
                jobRunsPage.getTotalPages());
    }

    public void updateCronExpression(final int id, final String cronExpression)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
                    InstantiationException, IllegalAccessException {
        final var job = jobDataService.updateCronExpression(id, cronExpression);
        jobScheduler.update(job);
    }

    public void startJob(final int id, final boolean forceRun)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
                    InstantiationException, IllegalAccessException {
        final var job =
                jobDataService
                        .findJobById(id)
                        .orElseThrow(
                                ResourceNotFoundException.throwWithMessage(
                                        "No Job with id {0} found", id));
        jobScheduler.runNow(job, forceRun);
    }

    public JobRunInfoContainer findJobRun(final int jobRunId) {
        final var jobRun =
                jobDataService
                        .findJobRunById(jobRunId)
                        .orElseThrow(
                                ResourceNotFoundException.throwWithMessage(
                                        "No JobRun with runId {0} found", jobRunId));
        final var jobName =
                jobDataService
                        .findJobById(jobRun.getJobId())
                        .map(Job::getName)
                        .orElseThrow(
                                ResourceNotFoundException.throwWithMessage(
                                        "No Job with id {0} found", jobRun.getJobId()));
        return JobRunInfoContainer.from(jobRun, jobName);
    }
}
