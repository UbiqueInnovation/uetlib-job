package ch.ubique.backend.job.service;

import ch.ubique.backend.core.exception.ResourceNotFoundException;
import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.client.*;
import ch.ubique.backend.job.model.client.JobInfosContainer.JobInfo;
import ch.ubique.backend.job.model.client.info.*;
import ch.ubique.backend.job.model.properties.JobProperty;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;

import com.pivovarit.function.ThrowingFunction;

import org.apache.commons.collections4.SetUtils;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JobControllerService {
    private static final int PAGE_SIZE = 30;
    private final JobService jobService;

    private final TriggerService triggerService;
    private final Scheduler scheduler;
    private final JobDataService jobDataService;

    public JobControllerService(
            final JobDataService jobDataService,
            final JobService jobService,
            final Scheduler scheduler) {
        this.jobDataService = jobDataService;
        this.jobService = jobService;
        this.scheduler = scheduler;
    }

    public JobInfosContainer findJobs() throws ClassNotFoundException {
        final var jobs = jobDataService.findJobs();
        final var latestRuns = findLatestJobRuns(jobs);
        final var jobInfos = new ArrayList<JobInfo>(jobs.size());
        for (final var job : jobs) {
            jobInfos.add(jobInfo(job));
        }
        return new JobInfosContainer(jobInfos, latestRuns);
    }

    public Map<Integer, JobRunInfo> findLatestJobRuns(final List<JobClassAndId> jobs) {
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
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        JobClassAndId::id, JobClassAndId::name));
        final var jobRunsPage = jobDataService.findJobRuns(page, PAGE_SIZE, jobId, state);
        final var jobRuns = jobRunsPage.stream().map(JobRunInfo::from).toList();
        return new JobRunInfosContainer(
                jobRuns,
                jobNames,
                jobRunsPage.isFirst(),
                jobRunsPage.isLast(),
                jobRunsPage.getTotalPages());
    }

    public void startJob(final int jobId, final boolean forceRun) throws ClassNotFoundException {
        jobService.startJob(jobService.getJobClass(jobId), new JobRunArguments(forceRun));
    }

    public void startJob(final String className, final boolean forceRun)
            throws ClassNotFoundException {
        jobService.startJob(jobService.getJobClass(className), new JobRunArguments(forceRun));
    }

    public void startJob(final Class<? extends Job> jobClass, final boolean forceRun)
            throws ClassNotFoundException {
        startJob(jobClass.getCanonicalName(), forceRun);
    }

    public void startAllJobs(final boolean forceRun) throws ClassNotFoundException {

        final var jobs = jobDataService.findJobs();
        for (final var job : jobs) {
            startJob(job.id(), forceRun);
        }
    }

    public JobRunInfoContainer findJobRun(final int jobRunId) {
        final var jobRun =
                jobDataService
                        .findJobRunById(jobRunId)
                        .orElseThrow(
                                ResourceNotFoundException.throwWithMessage(
                                        "No JobRun with runId {0} found", jobRunId));
        final var jobName = jobById(jobRun.jobId()).name();
        return JobRunInfoContainer.from(jobRun, jobName);
    }

    public JobInfo findJobById(final int jobId) throws ClassNotFoundException {
        final var job = jobById(jobId);
        return jobInfo(job);
    }

    public JobInfo jobInfo(final JobClassAndId job) throws ClassNotFoundException {
        // TODO implement
        return null;
    }

    private JobClassAndId jobById(final int jobId) {
        return jobDataService
                .findJobById(jobId)
                .orElseThrow(
                        ResourceNotFoundException.throwWithMessage(
                                "No Job with id {0} found", jobId));
    }

    public Collection<JobPropertyInfo> getJobProperties(final int id) {
        final var properties =
                jobDataService.getProperties(id).stream()
                        .collect(Collectors.toMap(JobProperty::key, JobPropertyInfo::from));
        return JobPropertyDefinitions.values().stream()
                .map(
                        definition ->
                                properties.getOrDefault(
                                        definition.key(), JobPropertyInfo.from(definition)))
                .toList();
    }

    public Collection<JobPropertyInfo> updateJobProperties(
            final int id, final Map<String, String> updatedValues) {
        jobDataService.saveProperties(id, getPropertiesFromMap(updatedValues));
        return getJobProperties(id);
    }

    private static List<JobProperty<?>> getPropertiesFromMap(final Map<String, String> properties) {
        final var list = new ArrayList<JobProperty<?>>(properties.values().size());
        for (final var entry : properties.entrySet()) {
            list.add(
                    JobPropertyDefinitions.get(entry.getKey())
                            .createPropertyFromString(entry.getValue()));
        }
        return list;
    }

    public JobSystemInfoResponse getInfo() throws SchedulerException {
        return new JobSystemInfoResponse(
                new JobSystemInfo(
                        new JobSystemCronInfo(triggerService.getDefaultZoneId()),
                        new JobSystemSchedulerInfo(getMetaData(), getGroups())));
    }

    private List<JobSystemSchedulerGroupInfo> getGroups() throws SchedulerException {
        return scheduler.getJobGroupNames().stream()
                .map(ThrowingFunction.unchecked(this::groupInfo))
                .toList();
    }

    private JobSystemSchedulerMetaData getMetaData() throws SchedulerException {
        final var metaData = scheduler.getMetaData();
        return new JobSystemSchedulerMetaData(
                metaData.getThreadPoolSize(),
                metaData.getNumberOfJobsExecuted(),
                metaData.getSchedulerClass().getCanonicalName(),
                metaData.getThreadPoolClass().getCanonicalName(),
                metaData.getVersion(),
                metaData.getRunningSince().toInstant());
    }

    private JobSystemSchedulerGroupInfo groupInfo(final String groupName)
            throws SchedulerException {

        final var jobKeys = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName));
        final var currentlyExecutingJobs = scheduler.getCurrentlyExecutingJobs();

        final var currentlyExecutingJobKeys =
                currentlyExecutingJobs.stream()
                        .map(context -> context.getJobDetail().getKey())
                        .collect(Collectors.toUnmodifiableSet());

        final var jobKeysNotCurrentlyExecuting =
                SetUtils.difference(jobKeys, currentlyExecutingJobKeys);

        final var now = Instant.now();
        final var queueSize =
                jobKeysNotCurrentlyExecuting.stream()
                        .map(ThrowingFunction.unchecked(scheduler::getTriggersOfJob))
                        .flatMap(Collection::stream)
                        .filter(trigger -> trigger.getNextFireTime().toInstant().isBefore(now))
                        .count();

        return new JobSystemSchedulerGroupInfo(
                groupName, jobKeys.size(), currentlyExecutingJobs.size(), queueSize);
    }
}
