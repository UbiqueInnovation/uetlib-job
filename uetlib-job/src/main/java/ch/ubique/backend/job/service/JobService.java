package ch.ubique.backend.job.service;

import static java.util.Optional.ofNullable;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.RegisteredJob;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.properties.JobProperties;
import ch.ubique.backend.job.model.properties.JobProperty;
import ch.ubique.backend.job.model.properties.JobPropertyDefinition;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.triggers.CronTrigger;
import ch.ubique.backend.job.model.triggers.StartupTrigger;
import ch.ubique.backend.job.model.triggers.Trigger;
import ch.ubique.backend.job.scheduler.SchedulerService;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JobService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JobDataService jobDataService;
    private final SchedulerService schedulerService;
    private final JobCreator jobCreator;
    private final ClassLoaderService classLoaderService;
    private final ZoneId defaultZoneId;

    public JobService(
            final JobDataService jobDataService,
            final SchedulerService schedulerService,
            final JobCreator jobCreator,
            final ClassLoaderService classLoaderService,
            @Value("${ch.ubique.backend.job.cron.timezone:UTC}") final String defaultZoneId)
            throws ClassNotFoundException {
        this.jobDataService = jobDataService;
        this.schedulerService = schedulerService;
        this.jobCreator = jobCreator;
        this.classLoaderService = classLoaderService;
        this.defaultZoneId = ZoneId.of(defaultZoneId);

        registerAllJobs();
        evaluateJobTriggers();
        jobDataService.setBusyRunsToUnfinished();
    }

    private void registerAllJobs() {
        final var reflections = new Reflections();
        for (final var cls : reflections.getTypesAnnotatedWith(RegisteredJob.class)) {
            try {
                final var jobClass = cls.asSubclass(Job.class);
                jobDataService.registerJob(jobClass);
                logger.info("Registered {}.", jobClass);
            } catch (final ClassCastException e) {
                throw new ClassCastException(
                        "Class annotated with "
                                + RegisteredJob.class
                                + " must implement "
                                + Job.class);
            }
        }

        // Try if all database entries still have a job associated with them. Remove them if not
        for (final var job : jobDataService.findJobs()) {
            try {
                getJobClass(job.canonicalClassName());
            } catch (final ClassNotFoundException e) {
                logger.warn("Could not find class for {}. Removing database entry.", job);
                jobDataService.delete(job);
            }
        }
    }

    public Class<? extends Job> getJobClass(final String className) throws ClassNotFoundException {
        return classLoaderService.getClassForName(className, Job.class);
    }

    public void evaluateJobTriggers() throws ClassNotFoundException {
        for (final var job : jobDataService.findJobs()) {
            final var instance = instantiateJob(getJobClass(job.canonicalClassName()));
            final var triggers = getTriggers(instance);
            for (final var trigger : triggers) {
                if (trigger instanceof StartupTrigger) {
                    schedulerService.runJobNowManual(instance, false);
                }
                if (trigger instanceof final CronTrigger cronTrigger) {
                    schedulerService.scheduleJob(
                            instance,
                            cronTrigger.cronExpression(),
                            ofNullable(cronTrigger.timeZone()).orElse(defaultZoneId));
                }
            }
        }
    }

    /**
     * This method computes the effective job properties for a given job and works as following:
     * <li>If the job has fixed properties -> return them
     * <li>Otherwise: for each job property definition
     *
     *     <ul>
     *       <li>If this definition has a db value -> return this value
     *       <li>Otherwise return initial value (if present) or empty
     *     </ul>
     */
    public JobProperties getProperties(final Job job) {
        if (job.propertiesConfig().isFixed()) {
            return new JobProperties(job.propertiesConfig().properties());
        }

        // The code below merges db properties with initial properties.
        final var db =
                jobDataService.getProperties(getId(job)).stream()
                        .collect(Collectors.toMap(JobProperty::key, Function.identity()));

        final var initial =
                job.propertiesConfig().properties().stream()
                        .collect(Collectors.toMap(JobProperty::key, Function.identity()));

        return new JobProperties(
                JobPropertyDefinitions.values().stream()
                        .map(JobPropertyDefinition::key)
                        .map(key -> ofNullable(db.get(key)).or(() -> ofNullable(initial.get(key))))
                        .flatMap(Optional::stream)
                        .toList());
    }

    public Job instantiateJob(final Class<? extends Job> jobClass) {
        return jobCreator.createNewJobInstance(jobClass);
    }

    /**
     * This method computes the effective job triggers and works as following:
     * <li>If the job has fixed triggers -> return them
     * <li>Else-If there is at least one trigger in the database -> return triggers from database
     * <li>Else: return initial (potentially empty) list of triggers
     */
    public Collection<Trigger> getTriggers(final Job job) {
        if (job.triggersConfig().isFixed()) {
            return job.triggersConfig().triggers();
        }

        final var dbTriggers = jobDataService.getTriggers(getId(job));

        if (!dbTriggers.isEmpty()) {
            return dbTriggers;
        }

        // Return initial triggers
        return job.triggersConfig().triggers();
    }

    public int getJobIdByCanonicalClassName(final String jobClassName) {
        return jobDataService.findJobByCanonicalClassName(jobClassName).orElseThrow().id();
    }

    public int getId(final Job job) {
        return jobDataService
                .findJobByCanonicalClassName(job.getClass().getCanonicalName())
                .orElseThrow()
                .id();
    }

    public JobClassAndId getJobClassWithId(final Class<? extends Job> jobClass) {
        return jobDataService
                .findJobByCanonicalClassName(jobClass.getCanonicalName())
                .orElseThrow();
    }

    public void startJob(final Class<? extends Job> jobClass, final boolean forceRun) {
        schedulerService.runJobNowManual(instantiateJob(jobClass), forceRun);
    }

    public ZoneId getDefaultZoneId() {
        return defaultZoneId;
    }

    public JobRun createNewJobRun(@NotNull final Job job) {
        final var jobId = getJobIdByCanonicalClassName(job.getClass().getCanonicalName());
        return jobDataService.createJobRun(jobId, job.version());
    }

    public JobRun getOrCreateJobRun(@NotNull final Job job, @Nullable final Integer runId) {
        return ofNullable(runId)
                .flatMap(jobDataService::findJobRunById)
                .orElseGet(() -> createNewJobRun(job));
    }

    public void updateJobRun(@NotNull final JobRun jobRun) {
        jobDataService.saveJobRun(jobRun);
    }
}
