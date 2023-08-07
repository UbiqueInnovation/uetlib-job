package ch.ubique.backend.job.cleanup;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.RegisteredJob;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobClassAndId;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.JobPropertiesConfig;
import ch.ubique.backend.job.model.properties.JobPropertyDefinitions;
import ch.ubique.backend.job.model.triggers.CronTrigger;
import ch.ubique.backend.job.model.triggers.JobTriggersConfig;
import ch.ubique.backend.job.storage.JobRunStorage;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;

@RegisteredJob
public class CleanupJob implements Job {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final JobRunStorage jobRunStorage;
    private final JobDataService jobDataService;

    public CleanupJob(final JobRunStorage jobRunStorage, final JobDataService jobDataService) {
        this.jobRunStorage = jobRunStorage;
        this.jobDataService = jobDataService;
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
                JobPropertyDefinitions.CLEANUP_DAYS_TO_KEEP.createProperty(1),
                JobPropertyDefinitions.RUN_TIME_LIMIT.createProperty(Duration.ofMinutes(15)),
                JobPropertyDefinitions.LAST_RUN_MAX_AGE.createProperty(
                        Duration.ofDays(1).plusHours(1)));
    }

    @NotNull
    @Override
    public JobRunState run(final JobRunContext context) {

        final var cleanJobRunsState = cleanJobRuns();
        if (cleanJobRunsState != JobRunState.DONE) {
            return cleanJobRunsState;
        }

        final var clearUnknownJobPropertiesState = clearUnknownJobProperties();
        return clearUnknownJobPropertiesState;
    }

    @NotNull
    private JobRunState clearUnknownJobProperties() {
        jobDataService.clearUnknownJobProperties();
        return JobRunState.DONE;
    }

    @NotNull
    private JobRunState cleanJobRuns() {
        for (final var job : jobDataService.findJobs()) {
            // Get cleanup properties
            final var daysToKeep =
                    jobDataService
                            .getProperty(job.id(), JobPropertyDefinitions.CLEANUP_DAYS_TO_KEEP)
                            .value();
            final var chunkSize =
                    jobDataService
                            .getProperty(job.id(), JobPropertyDefinitions.CLEANUP_CHUNK_SIZE)
                            .value();

            final var cleanableRuns =
                    jobDataService.findCleanableRuns(job.id(), daysToKeep, chunkSize);
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

            logger.info(
                    "Cleaned runs for job [job: {}; num_deletions: {}]", job, cleanableRuns.size());
        }

        return JobRunState.DONE;
    }

    /** Attempts to delete all stored input files of a job run */
    private void cleanInputFiles(final JobClassAndId job, final JobRun run) throws IOException {
        // Clean input files
        final var jobRunInputs = jobDataService.findJobRunInputs(run.id());
        for (final var input : jobRunInputs) {
            final var fileDidExist =
                    jobRunStorage.deleteJobRunInput(job.name(), run.readableId(), input.getName());
            if (fileDidExist) {
                logger.info(
                        "Deleted run input file [jobId: {}; runId {}; inputName: {}]",
                        job.id(),
                        run.id(),
                        input.getName());
            } else {
                logger.info(
                        "Failed to delete run input file: doesn't exist [jobId: {}; runId: {};"
                                + " inputName: {}]",
                        job.id(),
                        run.id(),
                        input.getName());
            }
        }

        // Finally, clean input file directories
        final var dirDidExist =
                jobRunStorage.deleteJobRunInputDirectory(job.name(), run.readableId());
        if (dirDidExist) {
            logger.info(
                    "Deleted run input file directory [jobId: {}; runId: {}]", job.id(), run.id());
        } else if (!jobRunInputs.isEmpty()) {
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
