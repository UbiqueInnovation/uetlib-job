package ch.ubique.backend.job.service.controller;

import ch.ubique.backend.core.exception.ResourceNotFoundException;
import ch.ubique.backend.job.BaseInputJob;
import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.client.*;
import ch.ubique.backend.job.model.client.JobInfo;
import ch.ubique.backend.job.model.client.info.*;
import ch.ubique.backend.job.model.client.triggers.JobTrigger;
import ch.ubique.backend.job.model.client.triggers.JobTriggers;
import ch.ubique.backend.job.model.properties.JobProperty;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.triggers.CronTrigger;
import ch.ubique.backend.job.model.triggers.Trigger;
import ch.ubique.backend.job.model.triggers.TriggerType;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.service.executor.JobExecutorService;
import ch.ubique.backend.job.service.triggers.CronTriggerService;
import ch.ubique.backend.job.service.triggers.TriggerCoordinatorService;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Profile("!job-run")
public class JobControllerService {
    private static final int PAGE_SIZE = 30;
    private final JobService jobService;

    private final JobExecutorService jobExecutorService;

    private final CronTriggerService cronTriggerService;

    private final TriggerCoordinatorService triggerCoordinatorService;
    private final JobDataService jobDataService;

    public JobControllerService(
            final JobDataService jobDataService,
            final JobService jobService,
            final JobExecutorService jobExecutorService,
            final CronTriggerService cronTriggerService,
            final TriggerCoordinatorService triggerCoordinatorService) {
        this.jobDataService = jobDataService;
        this.jobService = jobService;
        this.jobExecutorService = jobExecutorService;
        this.cronTriggerService = cronTriggerService;
        this.triggerCoordinatorService = triggerCoordinatorService;
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

    public JobInfosContainer findAllJobs() throws ClassNotFoundException {
        final var jobs = jobDataService.findJobs();
        final var latestRuns = findLatestJobRuns(jobs);
        return new JobInfosContainer(findJobs(), latestRuns);
    }

    public List<JobInfo> findJobs() throws ClassNotFoundException {
        final var jobs = jobDataService.findJobs();
        final var jobInfos = new ArrayList<JobInfo>(jobs.size());
        for (final var job : jobs) {
            jobInfos.add(jobInfo(jobService.instantiateJob(jobService.getJobClass(job.id()))));
        }
        return jobInfos;
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
            final int page, final Integer jobId, final JobRunState state) throws ClassNotFoundException {
        final var jobs = findJobs().stream().collect(Collectors.toMap(JobInfo::id, Function.identity()));
        final var jobNames = jobs.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().name()));
        final var jobRunsPage = jobDataService.findJobRuns(page, PAGE_SIZE, jobId, state);
        final var jobRuns = jobRunsPage.stream().map(JobRunInfo::from).toList();
        return new JobRunInfosContainer(
                jobRuns,
                jobs,
                jobNames,
                jobRunsPage.isFirst(),
                jobRunsPage.isLast(),
                jobRunsPage.getTotalPages());
    }

    public void startJob(final String idOrClassName, final boolean forceRun)
            throws ClassNotFoundException {
        jobExecutorService.submit(getJobClass(idOrClassName), new JobRunArguments(forceRun));
    }

    public void startAllJobs(final boolean forceRun) throws ClassNotFoundException {

        final var jobs = jobDataService.findJobs();
        for (final var job : jobs) {
            startJob(job.canonicalClassName(), forceRun);
        }
    }

    public JobRunInfoContainer findJobRun(final int jobRunId) {
        final var jobRun =
                jobDataService
                        .findJobRunById(jobRunId)
                        .orElseThrow(
                                ResourceNotFoundException.supplierWithMessage(
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
                        ResourceNotFoundException.supplierWithMessage(
                                "No Job with id {0} found", jobId));
    }

    public Collection<JobPropertyInfo> getJobProperties(final String idOrClassName)
            throws ClassNotFoundException {
        final var properties =
                jobService
                        .getProperties(jobService.instantiateJob(getJobClass(idOrClassName)))
                        .values()
                        .stream()
                        .collect(Collectors.toMap(JobProperty::key, JobPropertyInfo::from));
        return JobPropertyDefinitions.values().stream()
                .map(
                        definition ->
                                properties.getOrDefault(
                                        definition.key(), JobPropertyInfo.from(definition)))
                .toList();
    }

    public Collection<JobPropertyInfo> updateJobProperties(
            final String idOrClassName, final Map<String, String> updatedValues)
            throws ClassNotFoundException {
        jobDataService.saveProperties(
                jobService.getJobId(getJobClass(idOrClassName)),
                getPropertiesFromMap(updatedValues));
        return getJobProperties(idOrClassName);
    }

    public JobTriggers getJobTriggers(final String idOrClassName) throws ClassNotFoundException {
        final var job = getJobInstance(idOrClassName);
        return getJobTriggers(job);
    }

    public JobTriggers updateJobTriggers(final String idOrClassName, final JobTriggers triggers)
            throws ClassNotFoundException {
        final var job = getJobInstance(idOrClassName);
        for (final var trigger : triggers.triggers()) {
            if (trigger.id() == null) {
                triggerCoordinatorService.createTrigger(job.getClass(), trigger.definition());
            } else {
                triggerCoordinatorService.updateTrigger(
                        job.getClass(),
                        new Trigger(
                                trigger.id(),
                                jobService.getJobId(job.getClass()),
                                trigger.definition()));
            }
        }
        return getJobTriggers(job);
    }

    private JobTriggers getJobTriggers(final Job job) {
        return new JobTriggers(
                job.triggersConfig().isFixed(),
                triggerCoordinatorService.getTriggers(job).stream().map(JobTrigger::new).toList());
    }

    public JobSystemInfoResponse getInfo() {
        return new JobSystemInfoResponse(
                new JobSystemInfo(
                        new JobSystemCronInfo(cronTriggerService.getDefaultZoneId()),
                        new JobSystemSchedulerInfo(getGroups())));
    }

    private List<JobSystemSchedulerGroupInfo> getGroups() {
        return List.of();
    }

    /**
     * @deprecated since v1.5, use {@link #updateJobTriggers}
     */
    @Deprecated(since = "1.5", forRemoval = true)
    public JobInfo updateCronExpression(final int jobId, final String cronExpression)
            throws ClassNotFoundException {
        final var jobClass = jobService.getJobClass(jobId);
        final var job = jobService.instantiateJob(jobClass);
        getCronTriggers(job).forEach(triggerCoordinatorService::deleteTrigger);
        if (StringUtils.isNotBlank(cronExpression)
            && CronExpression.isValidExpression(cronExpression)) {
            triggerCoordinatorService.createTrigger(jobClass, new CronTrigger(cronExpression));
        }

        return jobInfo(job);
    }

    private Job getJobInstance(final String idOrClassName) throws ClassNotFoundException {
        return jobService.instantiateJob(getJobClass(idOrClassName));
    }

    private Class<? extends Job> getJobClass(final String idOrClassName)
            throws ClassNotFoundException {
        try {
            return jobService.getJobClass(Integer.parseInt(idOrClassName));
        } catch (final NumberFormatException ignored) {
            return jobService.getJobClass(idOrClassName);
        }
    }
}
