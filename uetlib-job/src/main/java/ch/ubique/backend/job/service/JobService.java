package ch.ubique.backend.job.service;

import ch.ubique.backend.core.exception.ResourceNotFoundException;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.client.JobInfosContainer;
import ch.ubique.backend.job.model.client.JobInfosContainer.JobInfo;
import ch.ubique.backend.job.model.client.JobRunInfo;
import ch.ubique.backend.job.model.client.JobRunInfoContainer;
import ch.ubique.backend.job.model.client.JobRunInfosContainer;
import ch.ubique.backend.job.scheduler.JobScheduler;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class JobService {

    private static final int PAGE_SIZE = 30;

    private final JobDataService jobDataService;
    private final JobScheduler jobScheduler;

    private final JobCreator jobCreator;

    public JobService(
            final JobDataService jobDataService,
            final JobScheduler jobScheduler,
            final JobCreator jobCreator) {
        this.jobDataService = jobDataService;
        this.jobScheduler = jobScheduler;
        this.jobCreator = jobCreator;
    }

    public JobInfosContainer findJobs() throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        final var jobs = jobDataService.findJobs();
        final var latestRuns = findLatestJobRuns(jobs);
        final var jobInfos = new ArrayList<JobInfo>(jobs.size());
        for (final var job : jobs) {
            jobInfos.add(jobInfo(job));
        }
        return new JobInfosContainer(jobInfos, latestRuns);
    }

    public Map<Integer, JobRunInfo> findLatestJobRuns(final List<Job> jobs) {
        return jobs.stream()
                .map(
                        job ->
                                jobDataService
                                        .findLatestJobRun(job.id())
                                        .map(JobRunInfo::from)
                                        .orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(JobRunInfo::jobId, Function.identity()));
    }

    public JobRunInfosContainer findJobRuns(
            final int page, final Integer jobId, final JobRunState state) {
        final var jobNames =
                jobDataService.findJobs().stream()
                        .collect(Collectors.toUnmodifiableMap(Job::id, Job::name));
        final var jobRunsPage = jobDataService.findJobRuns(page, PAGE_SIZE, jobId, state);
        final var jobRuns = jobRunsPage.stream().map(JobRunInfo::from).toList();
        return new JobRunInfosContainer(
                jobRuns,
                jobNames,
                jobRunsPage.isFirst(),
                jobRunsPage.isLast(),
                jobRunsPage.getTotalPages());
    }

    public void updateCronExpression(final int jobId, final String cronExpression)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
                    InstantiationException, IllegalAccessException {
        final var job = jobDataService.updateCronExpression(jobId, cronExpression);
        jobScheduler.update(job);
    }

    public void startJob(final int jobId, final boolean forceRun)
            throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
                    InstantiationException, IllegalAccessException {
        final var job =
                jobDataService
                        .findJobById(jobId)
                        .orElseThrow(
                                ResourceNotFoundException.throwWithMessage(
                                        "No Job with id {0} found", jobId));
        jobScheduler.runNow(job, forceRun);
    }

    public JobRunInfoContainer findJobRun(final int jobRunId) {
        final var jobRun =
                jobDataService
                        .findJobRunById(jobRunId)
                        .orElseThrow(
                                ResourceNotFoundException.throwWithMessage(
                                        "No JobRun with runId {0} found", jobRunId));
        final var jobName = jobById(jobRun.getJobId()).name();
        return JobRunInfoContainer.from(jobRun, jobName);
    }

    public JobInfo findJobById(final int jobId) throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        final var job = jobById(jobId);
        return jobInfo(job);
    }

    public JobInfo jobInfo(final Job job) throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        return JobInfo.from(job, jobCreator.createNewJobInstance(job));
    }

    private Job jobById(final int jobId) {
        return jobDataService
                .findJobById(jobId)
                .orElseThrow(
                        ResourceNotFoundException.throwWithMessage(
                                "No Job with id {0} found", jobId));
    }
}
