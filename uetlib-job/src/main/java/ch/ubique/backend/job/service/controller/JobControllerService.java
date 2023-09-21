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

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Profile("!job-run")
public class JobControllerService {
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

    @NotNull
    private ArrayList<JobInfo> jobInfos(final List<JobClassAndId> jobs)
            throws ClassNotFoundException {
        final var jobInfos = new ArrayList<JobInfo>(jobs.size());
        for (final var job : jobs) {
            jobInfos.add(jobInfo(jobService.instantiateJob(jobService.getJobClass(job.id()))));
        }
        return jobInfos;
    }

    public List<JobInfo> findJobs() throws ClassNotFoundException {
        final var jobs = jobDataService.findJobs();
        return jobInfos(jobs);
    }

    public JobInfosContainer findJobs(final int page, final int perPage)
            throws ClassNotFoundException {
        final var jobsPage = jobDataService.findJobsPage(page, perPage);
        final var jobs = jobsPage.toList();
        final var latestRuns = findLatestJobRuns(jobs);
        final var jobInfos = jobInfos(jobs);
        return new JobInfosContainer(
                jobInfos,
                latestRuns,
                jobsPage.isFirst(),
                jobsPage.isLast(),
                jobsPage.getTotalPages());
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

    /**
     * Search for the job runs of specified jobs. For filtering, there are four parameters:
     *
     * <ol>
     *   <li>{@code jobIds}: If not {@code null}, only job runs of the specified jobIds are
     *       returned. {@code excludeJobNamesOrIds} is ignored in this case. If this is {@code
     *       null}, fall back to:
     *   <li>{@code includeJobNamesOrIds}: If {@code jobIds} is {@code null}, only job runs of jobs
     *       with names in {@code includeJobNamesOrIds} are returned. If it is an empty list, fall
     *       back to:
     *   <li>{@code excludeJobNamesOrIds}: If {@code jobIds} is {@code null}, only job runs of jobs
     *       with names not in {@code excludeJobNamesOrIds} are returned.
     * </ol>
     *
     * <ul>
     *   <li>{@code state}: If not {@code null}, only job runs with the specified state are
     *       returned.
     * </ul>
     *
     * @param page
     * @param perPage
     * @param jobIds May specify a number of jobIds to filter for. If {@code null}, {@code
     *     excludeJobNamesOrIds} is used for filtering.
     * @param state
     * @param excludeJobNamesOrIds This parameter is essentially ignored for the DB query if {@code
     *     jobIds} is not {@code null}.
     * @return The job runs matching the specified criteria.
     * @throws ClassNotFoundException
     */
    public JobRunInfosContainer findJobRuns(
            final int page,
            final int perPage,
            @Nullable final Collection<Integer> jobIds,
            final JobRunState state,
            @NotNull final Collection<String> includeJobNamesOrIds,
            @NotNull final Collection<String> excludeJobNamesOrIds)
            throws ClassNotFoundException {
        final var jobs =
                findJobs().stream().collect(Collectors.toMap(JobInfo::id, Function.identity()));

        final var jobNames =
                jobs.entrySet().stream()
                        .collect(
                                Collectors.toMap(
                                        Map.Entry::getKey, entry -> entry.getValue().name()));
        final var useInclude = jobIds == null && !includeJobNamesOrIds.isEmpty();
        final var useExclude = jobIds == null && !useInclude;
        final var excludeJobIds =
                !useExclude ? Collections.emptyList() : getJobIds(excludeJobNamesOrIds);
        final var includeJobIds =
                !useInclude ? Collections.emptyList() : getJobIds(includeJobNamesOrIds);

        final Predicate<Map.Entry<Integer, String>> includeJob =
                entry ->
                        (useInclude
                                ? (includeJobNamesOrIds.contains(entry.getValue())
                                        || includeJobIds.contains(entry.getKey()))
                                : !excludeJobNamesOrIds.contains(entry.getValue())
                                        && !excludeJobIds.contains(entry.getKey()));

        final var requestedJobIds =
                jobIds == null
                        ? jobNames.entrySet().stream()
                                .filter(includeJob)
                                .map(Map.Entry::getKey)
                                .toList()
                        : jobIds;
        final var jobRunsPage = jobDataService.findJobRuns(page, perPage, requestedJobIds, state);
        final var jobRuns = jobRunsPage.stream().map(JobRunInfo::from).toList();
        return new JobRunInfosContainer(
                jobRuns,
                jobs,
                jobNames,
                jobRunsPage.isFirst(),
                jobRunsPage.isLast(),
                jobRunsPage.getTotalPages());
    }

    private List<Integer> getJobIds(final Collection<String> jobNamesOrIds) {
        return jobNamesOrIds.stream()
                .map(
                        n -> {
                            try {
                                return Integer.parseInt(n);
                            } catch (final NumberFormatException ignored) {
                                return null;
                            }
                        })
                .filter(Objects::nonNull)
                .toList();
    }

    public JobRunInfosContainer findJobRunsIncluding(
            final int page, final int perPage, final String idOrClassName, final JobRunState state)
            throws ClassNotFoundException {
        return findJobRuns(
                page, perPage, null, state, List.of(idOrClassName), Collections.emptyList());
    }

    public JobRunInfosContainer findJobRunsExcluding(
            final int page, final int perPage, final String idOrClassName, final JobRunState state)
            throws ClassNotFoundException {
        return findJobRuns(
                page, perPage, null, state, Collections.emptyList(), List.of(idOrClassName));
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

        // Delete triggers that are currently existing, but not present anymore
        final var existing = getJobTriggers(job);
        existing.triggers().stream()
                .filter(trigger -> !triggers.triggers().contains(trigger))
                .map(trigger -> toTrigger(trigger, job))
                .forEach(triggerCoordinatorService::deleteTrigger);

        // Create or update triggers
        for (final var trigger : triggers.triggers()) {
            if (trigger.id() == null) {
                triggerCoordinatorService.createTrigger(job.getClass(), trigger.definition());
            } else {
                triggerCoordinatorService.updateTrigger(job.getClass(), toTrigger(trigger, job));
            }
        }
        return getJobTriggers(job);
    }

    private Trigger toTrigger(final JobTrigger trigger, final Job job) {
        return new Trigger(
                Objects.requireNonNull(trigger.id()),
                jobService.getJobId(job.getClass()),
                trigger.definition());
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
