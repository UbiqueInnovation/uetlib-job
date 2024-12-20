package ch.ubique.backend.job.service.controller;

import static java.util.function.Function.identity;

import ch.ubique.backend.core.exception.ResourceNotFoundException;
import ch.ubique.backend.core.io.InputStreamProvider;
import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.exception.ManualTriggerNotAllowedException;
import ch.ubique.backend.job.files.JobFile;
import ch.ubique.backend.job.files.JobWithFiles;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.client.*;
import ch.ubique.backend.job.model.client.JobInfo;
import ch.ubique.backend.job.model.client.info.*;
import ch.ubique.backend.job.model.client.triggers.JobTrigger;
import ch.ubique.backend.job.model.client.triggers.JobTriggers;
import ch.ubique.backend.job.model.properties.JobProperty;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.properties.enums.JobManualTriggerMode;
import ch.ubique.backend.job.model.response.SuccessResponse;
import ch.ubique.backend.job.model.response.files.JobRunFileListResponse;
import ch.ubique.backend.job.model.triggers.CronTrigger;
import ch.ubique.backend.job.model.triggers.Trigger;
import ch.ubique.backend.job.model.triggers.TriggerType;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.service.executor.JobExecutorService;
import ch.ubique.backend.job.service.executor.JobManualTriggerService;
import ch.ubique.backend.job.service.triggers.CronTriggerService;
import ch.ubique.backend.job.service.triggers.TriggerCoordinatorService;

import com.pivovarit.function.ThrowingFunction;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Profile("!job-run")
public class JobControllerService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JobService jobService;

    private final JobExecutorService jobExecutorService;

    private final CronTriggerService cronTriggerService;

    private final TriggerCoordinatorService triggerCoordinatorService;
    private final JobDataService jobDataService;
    private final JobManualTriggerService jobManualTriggerService;

    public JobControllerService(
            final JobDataService jobDataService,
            final JobService jobService,
            final JobExecutorService jobExecutorService,
            final CronTriggerService cronTriggerService,
            final TriggerCoordinatorService triggerCoordinatorService,
            final JobManualTriggerService jobManualTriggerService) {
        this.jobDataService = jobDataService;
        this.jobService = jobService;
        this.jobExecutorService = jobExecutorService;
        this.cronTriggerService = cronTriggerService;
        this.triggerCoordinatorService = triggerCoordinatorService;
        this.jobManualTriggerService = jobManualTriggerService;
    }

    private static List<JobProperty<?>> getPropertiesFromMap(final Map<String, String> properties) {
        final var list = new ArrayList<JobProperty<?>>(properties.size());
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
        for (final var jobClassAndId : jobs) {
            final var jobInfo = jobInfo(jobClassAndId);
            jobInfos.add(jobInfo);
        }
        return jobInfos;
    }

    public Collection<JobInfo> findAllJobs() throws ClassNotFoundException {
        return findJobs(Set.of(), Set.of());
    }

    public List<JobInfo> findJobs(
            final Collection<String> includeJobNamesOrIds,
            final Collection<String> excludeJobNamesOrIds)
            throws ClassNotFoundException {
        final List<JobClassAndId> jobs;
        if (CollectionUtils.isNotEmpty(includeJobNamesOrIds)) {
            // include jobs if includeJobNamesOrIds not empty
            final var includeJobIds = getJobIds(includeJobNamesOrIds);
            jobs = jobDataService.findJobsInclude(includeJobIds);
        } else if (CollectionUtils.isNotEmpty(excludeJobNamesOrIds)) {
            // exclude jobs if excludeJobNamesOrIds not empty
            final var excludeJobIds = getJobIds(excludeJobNamesOrIds);
            jobs = jobDataService.findJobsExclude(excludeJobIds);
        } else {
            // default include all
            jobs = jobDataService.findJobs();
        }
        return jobInfos(jobs);
    }

    public JobInfosContainer findJobs(final Pageable pageable) throws ClassNotFoundException {
        final var jobsPage = jobDataService.findJobsPage(pageable);
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
                .collect(Collectors.toMap(JobRunInfo::jobId, identity()));
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
     * @param excludeJobNamesOrIds This parameter is essentially ignored for the DB query if {@code
     *     jobIds} is not {@code null}.
     * @return The job runs matching the specified criteria.
     */
    public JobRunInfosContainer findJobRuns(
            final Pageable pageable,
            final JobRunState state,
            @NotNull final Collection<String> includeJobNamesOrIds,
            @NotNull final Collection<String> excludeJobNamesOrIds,
            @Nullable final Instant from,
            @Nullable final Instant to)
            throws ClassNotFoundException {
        final var jobsById =
                findAllJobs().stream().collect(Collectors.toMap(JobInfo::id, identity()));

        final var selectedJobIdList =
                getJobIdIncludeList(includeJobNamesOrIds, excludeJobNamesOrIds);
        final var fromToRange = getFromToRangeOptional(from, to);
        final var jobRunsPage =
                jobDataService.findJobRuns(pageable, selectedJobIdList, state, fromToRange);
        final var jobRuns = jobRunsPage.stream().map(JobRunInfo::from).toList();
        return new JobRunInfosContainer(
                jobRuns,
                jobsById,
                jobRunsPage.isFirst(),
                jobRunsPage.isLast(),
                jobRunsPage.getTotalPages());
    }

    private List<Integer> getJobIdIncludeList(
            final Collection<String> includeJobNamesOrIds,
            final Collection<String> excludeJobNamesOrIds)
            throws ClassNotFoundException {
        final var includeJobIds = CollectionUtils.isNotEmpty(includeJobNamesOrIds);
        final var excludeJobIds = CollectionUtils.isNotEmpty(excludeJobNamesOrIds);
        if (includeJobIds) {
            return findJobs(includeJobNamesOrIds, List.of()).stream().map(JobInfo::id).toList();
        }
        if (excludeJobIds) {
            return findJobs(List.of(), excludeJobNamesOrIds).stream().map(JobInfo::id).toList();
        }
        return List.of();
    }

    private Optional<Range<Instant>> getFromToRangeOptional(final Instant from, final Instant to) {
        return Optional.ofNullable(from)
                .flatMap(fromI -> Optional.ofNullable(to).map(toI -> Range.of(fromI, toI)));
    }

    private List<Integer> getJobIds(final Collection<String> jobNamesOrIds) {
        return jobNamesOrIds.stream()
                .map(ThrowingFunction.lifted(this::getJobId))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * @throws ClassNotFoundException If the job class could not be found for the given id or class
     * @throws ManualTriggerNotAllowedException If the job does not allow manual execution
     */
    public void startJobManual(final String jobIdOrClassName, final boolean forceRun)
            throws ClassNotFoundException, ManualTriggerNotAllowedException {
        final var jobClass = getJobClass(jobIdOrClassName);
        // Allowed manual execution?
        final var manualExecutionAllowed =
                jobService
                                .getProperties(jobService.instantiateJob(jobClass))
                                .get(JobPropertyDefinitions.JOB_MANUAL_TRIGGER_MODE)
                                .value()
                        == JobManualTriggerMode.NEW_RUN_ALLOWED;
        if (!manualExecutionAllowed) {
            throw new ManualTriggerNotAllowedException(
                    String.format("Job %s does not allow manual execution", jobClass));
        }
        jobExecutorService.submit(jobClass, new JobRunArguments(forceRun));
    }

    public void replayJobRun(final String jobIdOrClassName)
            throws ClassNotFoundException, ManualTriggerNotAllowedException {
        final var jobClass = jobService.getJobClass(jobIdOrClassName);
        final var lastJobRun = jobService.getLastJobRun(jobService.getJobId(jobClass));
        replayJobRun(jobIdOrClassName, lastJobRun.orElseThrow().id());
    }

    public void replayJobRun(final String jobIdOrClassName, final int jobRunId)
            throws ClassNotFoundException, ManualTriggerNotAllowedException {
        final var jobRun = jobService.getJobRun(jobRunId);
        final var jobClass = jobService.getJobClass(jobRun.jobId());
        if (!jobClass.equals(getJobClass(jobIdOrClassName))) {
            throw new IllegalArgumentException(
                    MessageFormat.format(
                            "job class ''{0}'' of job run ''{1}'' does not match given"
                                    + " jobIdOrClassName ''{2}''",
                            jobClass, jobRunId, jobIdOrClassName));
        }
        final var replayExecutionAllowed =
                jobService
                                .getProperties(jobService.instantiateJob(jobClass))
                                .get(JobPropertyDefinitions.JOB_MANUAL_TRIGGER_MODE)
                                .value()
                        == JobManualTriggerMode.REPLAY_RUN_ALLOWED;
        if (!replayExecutionAllowed) {
            throw new ManualTriggerNotAllowedException(
                    String.format("Job %s does not allow replay execution", jobClass));
        }
        jobManualTriggerService.replayJobRun(jobRunId);
    }

    public void startAllJobs(final boolean forceRun) throws ClassNotFoundException {

        final var jobs = jobDataService.findJobs();
        for (final var job : jobs) {
            try {
                startJobManual(job.canonicalClassName(), forceRun);
            } catch (final ManualTriggerNotAllowedException e) {
                // Ignore jobs that do not allow manual execution
            }
        }
    }

    public JobRunInfoContainer findJobRun(final int jobRunId) throws ClassNotFoundException {
        final var jobRun =
                jobDataService
                        .findJobRunById(jobRunId)
                        .orElseThrow(
                                ResourceNotFoundException.supplierWithMessage(
                                        "No JobRun with runId {0} found", jobRunId));
        final var jobInfo = jobInfo(jobById(jobRun.jobId()));
        return JobRunInfoContainer.from(jobRun, jobInfo);
    }

    public JobInfo jobInfo(final JobClassAndId jobClassAndId) throws ClassNotFoundException {
        final var jobClass = jobService.getJobClass(jobClassAndId.id());
        final var job = jobService.instantiateJob(jobClass);

        final var cronExpression =
                getCronTriggers(job)
                        .findAny()
                        .map(trigger -> (CronTrigger) trigger.definition())
                        .map(CronTrigger::cronExpression);
        final var manualReplayMode =
                jobService.getProperties(job).get(JobPropertyDefinitions.JOB_MANUAL_TRIGGER_MODE);

        return new JobInfo(
                jobService.getId(job),
                job.getClass().getSimpleName(),
                job.getClass().getCanonicalName(),
                cronExpression.orElse(null),
                cronExpression.isPresent(),
                cronExpression.map(CronExpression::isValidExpression).orElse(true),
                job.triggersConfig().isFixed(),
                job.propertiesConfig().isFixed(),
                job.supportsForceRun(),
                manualReplayMode
                        .valueMaybe()
                        .or(JobPropertyDefinitions.JOB_MANUAL_TRIGGER_MODE::defaultValue)
                        .orElseThrow());
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

    public Collection<JobPropertyInfo> getJobProperties(final String jobIdOrClassName)
            throws ClassNotFoundException {
        final var properties =
                jobService
                        .getProperties(jobService.instantiateJob(getJobClass(jobIdOrClassName)))
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
            final String jobIdOrClassName, final Map<String, String> updatedValues)
            throws ClassNotFoundException {
        jobDataService.saveProperties(
                jobService.getJobId(getJobClass(jobIdOrClassName)),
                getPropertiesFromMap(updatedValues));
        return getJobProperties(jobIdOrClassName);
    }

    public JobTriggers getJobTriggers(final String jobIdOrClassName) throws ClassNotFoundException {
        final var job = getJobInstance(jobIdOrClassName);
        return getJobTriggers(job);
    }

    public JobTriggers updateJobTriggers(final String jobIdOrClassName, final JobTriggers triggers)
            throws ClassNotFoundException {
        final var job = getJobInstance(jobIdOrClassName);

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

    private Job getJobInstance(final String jobIdOrClassName) throws ClassNotFoundException {
        return jobService.instantiateJob(getJobClass(jobIdOrClassName));
    }

    private Class<? extends Job> getJobClass(final String jobIdOrClassName)
            throws ClassNotFoundException {
        try {
            return jobService.getJobClass(Integer.parseInt(jobIdOrClassName));
        } catch (final NumberFormatException ignored) {
            return jobService.getJobClass(jobIdOrClassName);
        }
    }

    private int getJobId(final String jobIdOrClassName) throws ClassNotFoundException {
        try {
            return Integer.parseInt(jobIdOrClassName);
        } catch (final NumberFormatException ignored) {
            return jobService.getJobId(jobService.getJobClass(jobIdOrClassName));
        }
    }

    public SuccessResponse cancelJobRun(final int jobRunId) {
        logger.info("Cancel requested for job run {}", jobRunId);
        final var jobRunMaybe = jobDataService.findJobRunById(jobRunId);
        if (jobRunMaybe.isEmpty()) {
            return new SuccessResponse(false, "No job for given ID found");
        }
        final var success = jobService.cancel(jobRunMaybe.get());
        final var message = success ? "Job run cancelled." : "Job run could not be cancelled";
        logger.info(message);
        return new SuccessResponse(success, message);
    }

    public JobRunFileListResponse listFiles(final int jobRunId) {
        return new JobRunFileListResponse(getJobFiles(jobRunId).map(JobFile::path).toList());
    }

    public Optional<InputStreamResource> getFile(final int jobRunId, final String path) {
        return getJobFiles(jobRunId)
                .filter(file -> file.path().equals(path))
                .findFirst()
                .map(JobFile::inputStreamProvider)
                .flatMap(ThrowingFunction.lifted(InputStreamProvider::inputStream))
                .map(InputStreamResource::new);
    }

    private Stream<JobFile> getJobFiles(final int jobRunId) {
        return jobService.getJobRunMaybe(jobRunId).stream().flatMap(this::getJobFiles);
    }

    private Stream<JobFile> getJobFiles(final JobRun jobRun) {
        return instantiateJob(jobRun)
                .map(this::jobWithFiles)
                .map(job -> job.jobRunFiles(jobRun))
                .stream()
                .flatMap(Collection::stream);
    }

    private Optional<Job> instantiateJob(final JobRun jobRun) {
        return jobService.getJobClassMaybe(jobRun.jobId()).map(jobService::instantiateJob);
    }

    private JobWithFiles jobWithFiles(final Job job) {
        if (job instanceof final JobWithFiles jobWithFiles) {
            return jobWithFiles;
        }
        return null;
    }
}
