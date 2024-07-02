package ch.ubique.backend.job.cleanup;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.RegisteredJob;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.JobProperties;
import ch.ubique.backend.job.model.properties.JobPropertiesConfig;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.runner.RunnerGroup;
import ch.ubique.backend.job.model.triggers.CronTrigger;
import ch.ubique.backend.job.model.triggers.JobTriggersConfig;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.storage.JobRunStorage;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

@RegisteredJob
public class CleanupJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(CleanupJob.class);

    private final JobRunStorage jobRunStorage;
    private final JobService jobService;
    private final JobDataService jobDataService;

    public CleanupJob(
            final JobRunStorage jobRunStorage,
            final JobService jobService,
            final JobDataService jobDataService) {
        this.jobRunStorage = jobRunStorage;
        this.jobService = jobService;
        this.jobDataService = jobDataService;
    }

    public static RunnerGroup runnerGroup() {
        return new RunnerGroup("cleanup", 1, Set.of(CleanupJob.class));
    }

    @NotNull
    @Override
    public JobTriggersConfig triggersConfig() {
        return JobTriggersConfig.fixed(new CronTrigger("0 0 2 * * ?")); // Every day at 2am
    }

    @NotNull
    @Override
    public JobPropertiesConfig propertiesConfig() {
        return JobPropertiesConfig.fixed(
                JobPropertyDefinitions.RUNNER_GROUP.createProperty(runnerGroup()),
                JobPropertyDefinitions.CLEANUP_DAYS_TO_KEEP.createProperty(1),
                JobPropertyDefinitions.RUN_TIME_LIMIT.createProperty(Duration.ofMinutes(15)),
                JobPropertyDefinitions.LAST_RUN_MAX_AGE.createProperty(
                        Duration.ofDays(1).plusHours(1)));
    }

    @NotNull
    @Override
    public JobRunState run(final JobRunContext jobRunContext) {

        final var cleanJobRunsState = cleanJobRuns();
        if (cleanJobRunsState != JobRunState.DONE) {
            return cleanJobRunsState;
        }

        return clearUnknownJobProperties();
    }

    @NotNull
    private JobRunState clearUnknownJobProperties() {
        jobDataService.clearUnknownJobProperties();
        return JobRunState.DONE;
    }

    @NotNull
    private JobRunState cleanJobRuns() {
        for (final var job : jobDataService.findJobs()) {
            final JobProperties jobProperties;
            try {
                jobProperties = jobService.getProperties(job);
            } catch (final ClassNotFoundException e) {
                logger.info("Failed to instantiate job {}, not need to clean: ", job, e);
                continue;
            }

            // Get cleanup properties
            final var daysToKeep =
                    jobProperties.get(JobPropertyDefinitions.CLEANUP_DAYS_TO_KEEP).value();
            final var chunkSize =
                    jobProperties.get(JobPropertyDefinitions.CLEANUP_CHUNK_SIZE).value();

            final var cleanableRuns =
                    jobDataService.findCleanableRuns(job.id(), daysToKeep, chunkSize);

            logger.info("Cleaning {} runs for job {}", cleanableRuns.size(), job);

            try {
                for (final var run : cleanableRuns) {
                    cleanInputFiles(job, run);
                    cleanLogs(run);
                }
            } catch (final IOException e) {
                logger.error("Failed to delete run input file", e);
                return JobRunState.ERROR;
            }

            // Delete job runs - cascades to delete job run input files, too
            jobDataService.deleteJobRunByIds(cleanableRuns.stream().map(JobRun::id).toList());

            // Delete job database submissions that are already submitted - cascades to delete job run input files, too
            jobDataService.deleteJobRunDatabaseSubmissionsByJobId(job.id());

            logger.info("Cleaned {} runs for job {}", cleanableRuns.size(), job);
        }

        return JobRunState.DONE;
    }

    /** Attempts to delete all stored input files of a job run */
    private void cleanInputFiles(final JobClassAndId job, final JobRun run) throws IOException {
        // Clean all files in directory of job run
        final var dirDidExist = jobRunStorage.deleteJobRunDirectory(job.name(), run.readableId());
        if (dirDidExist) {
            logger.debug(
                    "Deleted run input file directory [jobId: {}; runId: {}]", job.id(), run.id());
        } else {
            logger.debug(
                    "Failed to delete run input file directory: doesn't exist [jobId: {}; runId:"
                            + " {}]",
                    job.id(),
                    run.id());
        }
    }

    /** Deletes all logs for the specified run */
    private void cleanLogs(final JobRun run) {
        jobDataService.deleteLogsForJobRunId(run.id());
    }
}
