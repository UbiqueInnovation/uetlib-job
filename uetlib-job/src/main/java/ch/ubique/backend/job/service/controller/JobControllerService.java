package ch.ubique.backend.job.service.controller;

import ch.ubique.backend.core.exception.ResourceNotFoundException;
import ch.ubique.backend.job.BaseInputJob;
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
import ch.ubique.backend.job.model.triggers.CronTrigger;
import ch.ubique.backend.job.model.triggers.Trigger;
import ch.ubique.backend.job.model.triggers.TriggerType;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.service.triggers.CronTriggerService;
import ch.ubique.backend.job.service.triggers.TriggerCoordinatorService;

import com.pivovarit.function.ThrowingFunction;

import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class JobControllerService {
    private static final int PAGE_SIZE = 30;
    private final JobService jobService;

    private final CronTriggerService cronTriggerService;

    private final TriggerCoordinatorService triggerCoordinatorService;
    private final Scheduler scheduler;
    private final JobDataService jobDataService;

    public JobControllerService(
            final JobDataService jobDataService,
            final JobService jobService,
            final CronTriggerService cronTriggerService,
            final TriggerCoordinatorService triggerCoordinatorService,
            final Scheduler scheduler) {
        this.jobDataService = jobDataService;
        this.jobService = jobService;
        this.cronTriggerService = cronTriggerService;
        this.triggerCoordinatorService = triggerCoordinatorService;
        this.scheduler = scheduler;
    }

    public JobInfosContainer findJobs() throws ClassNotFoundException {
        final var jobs = jobDataService.findJobs();
        final var latestRuns = findLatestJobRuns(jobs);
        final var jobInfos = new ArrayList<JobInfo>(jobs.size());
        for (final var job : jobs) {
            jobInfos.add(jobInfo(jobService.instantiateJob(jobService.getJobClass(job.id()))));
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

    public JobInfo jobInfo(final Job job) {
        final var cronExpression =
                getCronTriggers(job)
                        .findAny()
                        .map(trigger -> (CronTrigger) trigger.definition())
                        .map(CronTrigger::cronExpression);

        return new JobInfo(
                jobService.getId(job),
                job.getClass().getSimpleName(),
                job.getClass().getCanonicalName(),
                cronExpression.orElse(null),
                cronExpression.isPresent(),
                cronExpression.map(CronExpression::isValidExpression).orElse(true),
                job.triggersConfig().isFixed(),
                job.propertiesConfig().isFixed(),
                job instanceof BaseInputJob<?>);
    }

    private Stream<Trigger> getCronTriggers(final Job job) {
        return triggerCoordinatorService.getTriggers(job).stream()
                .filter(trigger -> trigger.definition().type() == TriggerType.CRON);
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
                        new JobSystemCronInfo(cronTriggerService.getDefaultZoneId()),
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

    public JobInfo updateCronExpression(final int jobId, final String cronExpression)
            throws ClassNotFoundException {
        final var jobClass = jobService.getJobClass(jobId);
        final var job = jobService.instantiateJob(jobClass);
        getCronTriggers(job).forEach(triggerCoordinatorService::removeTrigger);
        if (StringUtils.isNotBlank(cronExpression)
                && CronExpression.isValidExpression(cronExpression)) {
            triggerCoordinatorService.createTrigger(jobClass, new CronTrigger(cronExpression));
        }

        return jobInfo(job);
    }
}
