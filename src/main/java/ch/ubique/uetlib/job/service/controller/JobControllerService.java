package ch.ubique.uetlib.job.service.controller;

import static ch.ubique.uetlib.core.utils.MapCollectors.toMap;

import ch.ubique.uetlib.core.exception.ResourceNotFoundException;
import ch.ubique.uetlib.job.Job;
import ch.ubique.uetlib.job.data.service.JobDataService;
import ch.ubique.uetlib.job.exception.ManualTriggerNotAllowedException;
import ch.ubique.uetlib.job.model.JobClassAndId;
import ch.ubique.uetlib.job.model.JobRun;
import ch.ubique.uetlib.job.model.JobRunArguments;
import ch.ubique.uetlib.job.model.JobRunState;
import ch.ubique.uetlib.job.model.client.JobInfo;
import ch.ubique.uetlib.job.model.client.JobInfosContainer;
import ch.ubique.uetlib.job.model.client.JobPropertyInfo;
import ch.ubique.uetlib.job.model.client.JobRunInfo;
import ch.ubique.uetlib.job.model.client.JobRunInfoContainer;
import ch.ubique.uetlib.job.model.client.JobRunInfosContainer;
import ch.ubique.uetlib.job.model.client.info.JobSystemCronInfo;
import ch.ubique.uetlib.job.model.client.info.JobSystemInfo;
import ch.ubique.uetlib.job.model.client.info.JobSystemInfoResponse;
import ch.ubique.uetlib.job.model.client.triggers.JobTrigger;
import ch.ubique.uetlib.job.model.client.triggers.JobTriggers;
import ch.ubique.uetlib.job.model.executer.JobSubmissionResult;
import ch.ubique.uetlib.job.model.executer.JobSubmissionStatus;
import ch.ubique.uetlib.job.model.executer.UnsuccessfulSubmissionResult;
import ch.ubique.uetlib.job.model.properties.JobProperty;
import ch.ubique.uetlib.job.model.properties.JobPropertyDefinitions;
import ch.ubique.uetlib.job.model.properties.enums.JobManualTriggerMode;
import ch.ubique.uetlib.job.model.response.SuccessResponse;
import ch.ubique.uetlib.job.model.response.files.JobRunFileListResponse;
import ch.ubique.uetlib.job.model.triggers.Trigger;
import ch.ubique.uetlib.job.service.JobService;
import ch.ubique.uetlib.job.service.executor.JobExecutorService;
import ch.ubique.uetlib.job.service.executor.JobManualTriggerService;
import ch.ubique.uetlib.job.service.info.JobRunnerInfoService;
import ch.ubique.uetlib.job.service.inputs.JobRunInputManager;
import ch.ubique.uetlib.job.service.inputs.JobRunInputProvider;
import ch.ubique.uetlib.job.service.logs.LogService;
import ch.ubique.uetlib.job.service.triggers.CronTriggerService;
import ch.ubique.uetlib.job.service.triggers.TriggerCoordinatorService;
import com.pivovarit.function.ThrowingFunction;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Range;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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
    private final JobRunInputManager jobRunInputManager;
    private final LogService logService;

    private final JobRunnerInfoService jobRunnerInfoService;

    public JobControllerService(
            final JobDataService jobDataService,
            final JobService jobService,
            final JobExecutorService jobExecutorService,
            final CronTriggerService cronTriggerService,
            final TriggerCoordinatorService triggerCoordinatorService,
            final JobManualTriggerService jobManualTriggerService,
            final JobRunInputManager jobRunInputManager,
            final LogService logService,
            final JobRunnerInfoService jobRunnerInfoService) {
        this.jobDataService = jobDataService;
        this.jobService = jobService;
        this.jobExecutorService = jobExecutorService;
        this.cronTriggerService = cronTriggerService;
        this.triggerCoordinatorService = triggerCoordinatorService;
        this.jobManualTriggerService = jobManualTriggerService;
        this.jobRunInputManager = jobRunInputManager;
        this.logService = logService;
        this.jobRunnerInfoService = jobRunnerInfoService;
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

    private ArrayList<JobInfo> jobInfos(
            final Collection<JobClassAndId> jobs,
            final Map<Integer, List<Trigger>> existingDbTriggers)
            throws ClassNotFoundException {
        final var jobInfos = new ArrayList<JobInfo>(jobs.size());
        for (final var jobClassAndId : jobs) {
            final var jobInfo = jobInfo(jobClassAndId, existingDbTriggers);
            jobInfos.add(jobInfo);
        }
        return jobInfos;
    }

    private Collection<JobClassAndId> getJobClassAndIds(
            final Collection<String> include, final Collection<String> exclude) {
        final var all = jobService.getAllJobClassesWithId();
        if (CollectionUtils.isNotEmpty(include)) {
            // include jobs if include not empty
            final var includeJobIds = getJobIds(include);
            return all.stream().filter(e -> includeJobIds.contains(e.id())).toList();
        }
        if (CollectionUtils.isNotEmpty(exclude)) {
            // exclude jobs if exclude not empty
            final var excludeJobIds = getJobIds(exclude);
            return all.stream().filter(e -> !excludeJobIds.contains(e.id())).toList();
        }
        return all;
    }

    public JobInfosContainer getJobInfos() throws ClassNotFoundException {
        final var jobs = jobService.getAllJobClassesWithId();
        final var existingDbTriggers = jobDataService.getAllDbTriggersByJobId();
        return new JobInfosContainer(jobInfos(jobs, existingDbTriggers), findLatestJobRuns(jobs));
    }

    public Map<Integer, JobRunInfo> findLatestJobRuns(final Collection<JobClassAndId> jobs) {
        final var neededJobIds = jobs.stream().map(JobClassAndId::id).toList();
        final var latestRuns = jobDataService.findAllLatestRuns(neededJobIds);
        final var runIds = latestRuns.stream().map(JobRun::id).toList();
        final var runIdsWithFiles = jobDataService.jobRunIdsWithInputs(runIds);
        return latestRuns.stream()
                .collect(
                        Collectors.toMap(
                                JobRun::jobId,
                                r -> JobRunInfo.from(r, runIdsWithFiles.contains(r.id()))));
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
            final Collection<JobRunState> states,
            final Collection<String> includeJobNamesOrIds,
            final Collection<String> excludeJobNamesOrIds,
            @Nullable final Instant from,
            @Nullable final Instant to)
            throws ClassNotFoundException {
        final var jobs = jobService.getAllJobClassesWithId();
        final var dbTriggers = jobDataService.getAllDbTriggersByJobId();
        final var jobsById = jobInfos(jobs, dbTriggers).stream().collect(toMap(JobInfo::id));

        final var selectedJobIdList =
                getJobClassAndIds(includeJobNamesOrIds, excludeJobNamesOrIds).stream()
                        .map(JobClassAndId::id)
                        .toList();
        final var fromToRange = getFromToRangeOptional(from, to);
        final var jobRunsPage =
                jobDataService.findJobRuns(pageable, selectedJobIdList, states, fromToRange);
        final var runIds = jobRunsPage.getContent().stream().map(JobRun::id).toList();
        final var runIdsWithFiles = jobDataService.jobRunIdsWithInputs(runIds);
        final var jobRuns =
                jobRunsPage.stream()
                        .map(r -> JobRunInfo.from(r, runIdsWithFiles.contains(r.id())))
                        .toList();
        return new JobRunInfosContainer(
                jobRuns,
                jobsById,
                jobRunsPage.isFirst(),
                jobRunsPage.isLast(),
                jobRunsPage.getTotalPages());
    }

    private Optional<Range<Instant>> getFromToRangeOptional(
            final @Nullable Instant from, final @Nullable Instant to) {
        return Optional.ofNullable(from)
                .flatMap(fromI -> Optional.ofNullable(to).map(toI -> Range.of(fromI, toI)));
    }

    private List<Integer> getJobIds(final Collection<String> jobNamesOrIds) {
        return jobNamesOrIds.stream().map(this::getJobId).filter(Objects::nonNull).toList();
    }

    /**
     * @throws ClassNotFoundException If the job class could not be found for the given id or class
     */
    public JobSubmissionResult startJobManual(final String jobIdOrClassName, final boolean forceRun)
            throws ClassNotFoundException {
        final var jobClass = getJobClass(jobIdOrClassName);
        // Allowed manual execution?
        final var manualExecutionAllowed =
                jobService
                                .getProperties(jobService.instantiateJob(jobClass))
                                .get(JobPropertyDefinitions.JOB_MANUAL_TRIGGER_MODE)
                                .value()
                        == JobManualTriggerMode.NEW_RUN_ALLOWED;
        if (!manualExecutionAllowed) {
            return new UnsuccessfulSubmissionResult(JobSubmissionStatus.DISALLOWED);
        }
        return jobExecutorService.submit(jobClass, new JobRunArguments(forceRun));
    }

    public void replayJobRun(final String jobIdOrClassName)
            throws ClassNotFoundException, ManualTriggerNotAllowedException {
        final var jobClass = jobService.getJobClassOrThrow(jobIdOrClassName);
        final var lastJobRun = jobService.getLastJobRun(jobService.findJobIdOrThrow(jobClass));
        replayJobRun(jobIdOrClassName, lastJobRun.orElseThrow().id());
    }

    public void replayJobRun(final String jobIdOrClassName, final int jobRunId)
            throws ClassNotFoundException, ManualTriggerNotAllowedException {
        final var jobRun = jobService.getJobRun(jobRunId);
        final var job = jobService.findJobById(jobRun.jobId()).orElseThrow();
        final var jobClass = jobService.getJobClassOrThrow(job);
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
        for (final var jobClass : jobService.getAllJobClasses()) {
            startJobManual(jobClass.getCanonicalName(), forceRun);
        }
    }

    public JobRunInfoContainer findJobRun(final int jobRunId) throws ClassNotFoundException {
        final var jobRun =
                jobDataService
                        .findJobRunById(jobRunId)
                        .orElseThrow(
                                ResourceNotFoundException.supplierWithMessage(
                                        "No JobRun with runId {0} found", jobRunId));
        final var jobInfo =
                jobInfo(jobById(jobRun.jobId()), jobDataService.getAllDbTriggersByJobId());
        return JobRunInfoContainer.from(
                JobRunInfo.from(jobRun, jobDataService.hasJobRunInputs(jobRunId)), jobInfo);
    }

    public JobInfo jobInfo(
            final JobClassAndId jobClassAndId, final Map<Integer, List<Trigger>> existingDbTriggers)
            throws ClassNotFoundException {
        final var jobClass = jobService.getJobClassOrThrow(jobClassAndId);
        final var job = jobService.instantiateJob(jobClass);

        final var manualReplayMode =
                jobService.getProperties(job).get(JobPropertyDefinitions.JOB_MANUAL_TRIGGER_MODE);

        return new JobInfo(
                jobClassAndId.id(),
                job.getClass().getSimpleName(),
                job.getClass().getCanonicalName(),
                jobService.isEnabled(jobClass.getCanonicalName()),
                job.propertiesConfig().isFixed(),
                job.triggersConfig().isFixed(),
                triggerCoordinatorService.getTriggers(job, existingDbTriggers).size(),
                job.supportsForceRun(),
                manualReplayMode
                        .valueMaybe()
                        .or(JobPropertyDefinitions.JOB_MANUAL_TRIGGER_MODE::defaultValue)
                        .orElseThrow());
    }

    private JobClassAndId jobById(final int jobId) {
        return jobService.findJobByIdOrThrow(jobId);
    }

    public Collection<JobPropertyInfo> getJobProperties(final String jobIdOrClassName)
            throws ClassNotFoundException {
        final var properties =
                jobService
                        .getProperties(jobService.instantiateJob(getJobClass(jobIdOrClassName)))
                        .values()
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        JobProperty::key,
                                        JobPropertyInfo::from,
                                        (a, b) -> {
                                            logger.warn(
                                                    "Duplicate property key found: {}. Using the first one. {} vs {}",
                                                    a.key(),
                                                    a.value(),
                                                    b.value());
                                            return a;
                                        }));
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
                jobService.findJobIdOrThrow(getJobClass(jobIdOrClassName)),
                getPropertiesFromMap(updatedValues));
        return getJobProperties(jobIdOrClassName);
    }

    public JobTriggers getJobTriggers(final String jobIdOrClassName) throws ClassNotFoundException {
        final var existingDbTriggers = jobDataService.getAllDbTriggersByJobId();
        final var job = getJobInstance(jobIdOrClassName);
        return getJobTriggers(job, existingDbTriggers);
    }

    public JobTriggers updateJobTriggers(final String jobIdOrClassName, final JobTriggers triggers)
            throws ClassNotFoundException {
        final var existingDbTriggers = jobDataService.getAllDbTriggersByJobId();
        final var job = getJobInstance(jobIdOrClassName);

        final var triggerIds = triggers.triggers().stream().map(JobTrigger::id).toList();

        // Delete triggers that are currently existing, but not present anymore
        final var existing = getJobTriggers(job, existingDbTriggers);
        existing.triggers().stream()
                .filter(trigger -> !triggerIds.contains(trigger.id()))
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
        return getJobTriggers(job, existingDbTriggers);
    }

    private Trigger toTrigger(final JobTrigger trigger, final Job job) {
        return new Trigger(
                Objects.requireNonNull(trigger.id()),
                jobService.findJobIdOrThrow(job.getClass()),
                trigger.definition());
    }

    private JobTriggers getJobTriggers(
            final Job job, final Map<Integer, List<Trigger>> existingDbTriggers) {
        return new JobTriggers(
                job.triggersConfig().isFixed(),
                triggerCoordinatorService.getTriggers(job, existingDbTriggers).stream()
                        .map(JobTrigger::new)
                        .toList());
    }

    public JobSystemInfoResponse getInfo() {
        return new JobSystemInfoResponse(
                new JobSystemInfo(
                        new JobSystemCronInfo(cronTriggerService.getDefaultZoneId()),
                        jobRunnerInfoService.getRunnerGroupsInfo()));
    }

    private Job getJobInstance(final String jobIdOrClassName) throws ClassNotFoundException {
        return jobService.instantiateJob(getJobClass(jobIdOrClassName));
    }

    private Class<? extends Job> getJobClass(final String jobIdOrClassName)
            throws ClassNotFoundException {
        try {
            final var job =
                    jobService.findJobById(Integer.parseInt(jobIdOrClassName)).orElseThrow();
            return jobService.getJobClassOrThrow(job);
        } catch (final NumberFormatException ignored) {
            return jobService.getJobClassOrThrow(jobIdOrClassName);
        }
    }

    private @Nullable Integer getJobId(final String jobIdOrClassName) {
        try {
            return Integer.parseInt(jobIdOrClassName);
        } catch (final NumberFormatException ignored) {
            return jobService.findJobId(jobIdOrClassName).orElse(null);
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

    public SuccessResponse acknowledgeJobRun(final int jobRunId) {
        final var jobRun = jobDataService.findJobRunById(jobRunId).orElseThrow();
        if (!jobRun.latestState().isAcknowledgeable()) {
            return new SuccessResponse(false, "Job run state is not acknowledgeable");
        }
        jobDataService.updateJobRunState(jobRunId, JobRunState.ACKNOWLEDGED, Instant.now());
        return new SuccessResponse(true, "Job run acknowledged.");
    }

    public JobRunFileListResponse listFiles(final int jobRunId) {
        return new JobRunFileListResponse(jobRunInputManager.getDeclaredInputPaths(jobRunId));
    }

    public Optional<StreamingResponseBody> getAllFiles(final int jobRunId) {
        final var allPaths = jobRunInputManager.getDeclaredInputPaths(jobRunId);
        if (allPaths.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                out -> {
                    try (final var zipOutput = new ZipArchiveOutputStream(out)) {
                        for (final var path : allPaths) {
                            final var resource = getFile(jobRunId, path);
                            if (resource.isEmpty()) {
                                continue;
                            }
                            try (final var is = resource.get().getInputStream()) {
                                final var entry = new ZipArchiveEntry(path);
                                zipOutput.putArchiveEntry(entry);
                                IOUtils.copy(is, zipOutput);
                                zipOutput.closeArchiveEntry();
                            }
                        }
                        zipOutput.finish();
                    }
                });
    }

    public Optional<InputStreamResource> getFile(final int jobRunId, final String path) {
        return jobRunInputProvider(jobRunId)
                .flatMap(inputProvider -> getFile(jobRunId, path, inputProvider));
    }

    private Optional<InputStreamResource> getFile(
            final int jobRunId, final String path, final JobRunInputProvider inputProvider) {
        return jobRunInputManager.getInputDeclarations(jobRunId).stream()
                .filter(input -> input.path().equals(path))
                .findFirst()
                .flatMap(ThrowingFunction.lifted(inputProvider::getJobRunInput))
                .map(InputStreamResource::new);
    }

    private Optional<Job> instantiateJob(final int jobRunId) {
        return jobDataService
                .findJobRunById(jobRunId)
                .map(JobRun::jobId)
                .flatMap(jobService::findJobById)
                .flatMap(jobService::getJobClassMaybe)
                .map(jobService::instantiateJob);
    }

    private Optional<JobRunInputProvider> jobRunInputProvider(final int jobRunId) {
        return instantiateJob(jobRunId).map(this::jobRunInputProvider);
    }

    private JobRunInputProvider jobRunInputProvider(final Job job) {
        if (job instanceof final JobRunInputProvider jobRunInputProvider) {
            return jobRunInputProvider;
        }
        return jobRunInputManager;
    }

    public String logs(final int jobRunId) {
        return Objects.requireNonNullElse(logService.logs(jobRunId).log(), "No logs found.");
    }

    public void setEnabled(final String jobIdOrClassName, final boolean isEnabled)
            throws ClassNotFoundException {
        final var jobClass = getJobClass(jobIdOrClassName);
        jobService.setEnabled(jobClass, isEnabled);
    }
}
