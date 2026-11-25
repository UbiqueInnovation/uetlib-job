package ch.uetlib.job.cleanup;

import static java.util.Objects.requireNonNull;

import ch.uetlib.core.executor.VirtualThreadUtils;
import ch.uetlib.core.utils.StreamUtils;
import ch.uetlib.job.Job;
import ch.uetlib.job.RegisteredJob;
import ch.uetlib.job.data.service.JobDataService;
import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunContext;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.properties.JobProperties;
import ch.uetlib.job.model.properties.JobPropertiesConfig;
import ch.uetlib.job.model.properties.JobPropertyDefinitions;
import ch.uetlib.job.model.runner.RunnerGroup;
import ch.uetlib.job.model.triggers.CronTrigger;
import ch.uetlib.job.model.triggers.JobTriggersConfig;
import ch.uetlib.job.service.JobService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Set;

@SuppressWarnings("ClassCanBeRecord")
@RegisteredJob
public class CleanupJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(CleanupJob.class);

    private static final int JPA_BATCH_CHUNK_SIZE = 20_000;

    private final JobRunInputCleaner jobRunInputCleaner;
    private final JobService jobService;
    private final JobDataService jobDataService;

    public CleanupJob(
            final JobRunInputCleaner jobRunInputCleaner,
            final JobService jobService,
            final JobDataService jobDataService) {
        this.jobRunInputCleaner = jobRunInputCleaner;
        this.jobService = jobService;
        this.jobDataService = jobDataService;
    }

    public static RunnerGroup runnerGroup() {
        return new RunnerGroup("cleanup", 1, Set.of(CleanupJob.class));
    }

    @Override
    public JobTriggersConfig triggersConfig() {
        return JobTriggersConfig.fixed(new CronTrigger("0 0 2 * * ?")); // Every day at 2am
    }

    @Override
    public JobPropertiesConfig propertiesConfig() {
        return JobPropertiesConfig.fixed(
                JobPropertyDefinitions.RUNNER_GROUP.createProperty(runnerGroup()),
                JobPropertyDefinitions.CLEANUP_DAYS_TO_KEEP.createProperty(1),
                JobPropertyDefinitions.RUN_TIME_LIMIT.createProperty(Duration.ofMinutes(15)),
                JobPropertyDefinitions.LAST_RUN_MAX_AGE.createProperty(
                        Duration.ofDays(1).plusHours(1)));
    }

    @Override
    public JobRunState run(final JobRunContext jobRunContext) {
        clearUnknownJobProperties();
        return cleanJobRuns();
    }

    private void clearUnknownJobProperties() {
        jobDataService.clearUnknownJobProperties();
    }

    private JobRunState cleanJobRuns() {
        var hasWarnings = false;
        for (final var job : jobService.getAllJobClassesWithId()) {
            final var success = cleanJob(job);
            if (!success) {
                hasWarnings = true;
            }
        }

        if (hasWarnings) {
            logger.info("Got Warnings will cleaning. Please check your cleanup properties.");
            return JobRunState.WARNING;
        }

        return JobRunState.DONE;
    }

    /**
     * @return true if cleaning was possible without any warnings
     */
    private boolean cleanJob(final JobClassAndId job) {
        logger.info("Starting cleaning of {}.", job.canonicalClassName());

        final JobProperties jobProperties;
        try {
            jobProperties = jobService.getProperties(job);
        } catch (final NoSuchElementException e) {
            logger.info("Failed to instantiate job {}, no need to clean: ", job, e);
            return true;
        }

        // Get cleanup properties
        final var daysToKeep =
                requireNonNull(
                        jobProperties.get(JobPropertyDefinitions.CLEANUP_DAYS_TO_KEEP).value());
        final var daysToKeepSkipped =
                jobProperties.get(JobPropertyDefinitions.CLEANUP_DAYS_TO_KEEP_SKIPPED).valueMaybe();
        final int chunkSize =
                requireNonNull(
                        jobProperties.get(JobPropertyDefinitions.CLEANUP_CHUNK_SIZE).value());

        final var cleanableRuns =
                jobDataService.findCleanableRuns(
                        job.id(), daysToKeep, daysToKeepSkipped, chunkSize);

        logger.info("Cleaning input files for {} runs.", cleanableRuns.size());
        VirtualThreadUtils.forEach(
                run -> jobRunInputCleaner.cleanInputFiles(job, run), cleanableRuns);

        // Delete job runs - cascades to delete job run input files and logs too
        logger.info("Removing job runs from database. Delete cascades for logs and inputs too.");
        StreamUtils.chunked(cleanableRuns.stream().map(JobRun::id), JPA_BATCH_CHUNK_SIZE)
                .forEach(jobDataService::deleteJobRunByIds);

        // Delete job database submissions that are already submitted - cascades to delete job
        // run input files, too
        logger.info("Deleting database submissions from database.");
        jobDataService.deleteJobRunDatabaseSubmissionsByJobId(job.id());

        logger.info("Cleaned {} runs for job {}", cleanableRuns.size(), job);

        if (cleanableRuns.size() >= chunkSize) {
            logger.info("Chunk size used up for {}. There might still be more runs to clean.", job);
            return false;
        }
        return true;
    }
}
