package ch.ubique.backend.job.cleanup;

import ch.ubique.backend.job.BaseJob;
import ch.ubique.backend.job.RegisteredJob;
import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobPropertyKey;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.storage.JobRunStorage;
import java.io.IOException;
import org.springframework.context.ApplicationContext;

@RegisteredJob
public class CleanupJob extends BaseJob {

    private final JobRunStorage jobRunStorage;

    public CleanupJob(ApplicationContext context, JobRunStorage jobRunStorage) {
        super(context);
        this.jobRunStorage = jobRunStorage;
    }

    @Override
    public String initialCronExpression() {
        //  At 00:00:00am, on every Wednesday, every month
        return "0 0 0 ? * WED *";
    }

    @Override
    protected JobRunState internalRun() throws Exception {

        for (var job : jobDataService.findJobs()) {
            // Get cleanup properties
            final var daysToKeep =
                    Integer.parseUnsignedInt(
                            jobDataService.getProperty(
                                    job.id(), JobPropertyKey.CLEANUP_DAYS_TO_KEEP));
            final var chunkSize =
                    Integer.parseUnsignedInt(
                            jobDataService.getProperty(
                                    job.id(), JobPropertyKey.CLEANUP_CHUNK_SIZE));

            final var cleanableRuns =
                    jobDataService.findCleanableRuns(job.id(), daysToKeep, chunkSize);
            try {
                for (var run : cleanableRuns) {
                    cleanInputFiles(job, run);
                }
            } catch (IOException e) {
                logger.error("Failed to delete run input file", e);
                return JobRunState.ERROR;
            }

            // Delete job runs - cascades to delete job run input files, too
            jobDataService.deleteJobRunByIds(cleanableRuns.stream().map(JobRun::getId).toList());

            logger.info(
                    "Cleaned runs for job [jobId: {}; num_deletions: {}]",
                    job.id(),
                    cleanableRuns.size());
        }

        return JobRunState.DONE;
    }

    /** Attempts to delete all stored input files of a job run */
    private void cleanInputFiles(Job job, JobRun run) throws IOException {
        // Clean input files
        final var jobRunInputs = jobDataService.findJobRunInputs(run.getId());
        for (var input : jobRunInputs) {
            if (!jobRunStorage.deleteJobRunInput(job.name(), run.readableId(), input.getName())) {
                logger.warn(
                        "Failed to delete run input file: doesn't exist [jobId: {}; runId: {}; inputName: {}]",
                        job.id(),
                        run.getId(),
                        input.getName());
            }
        }

        // Finally, clean input file directories
        if (!jobRunInputs.isEmpty()
                && !jobRunStorage.deleteJobRunInputDirectory(job.name(), run.readableId())) {
            logger.warn(
                    "Failed to delete run input file directory: doesn't exist [jobId: {}; runId: {}]",
                    job.id(),
                    run.getId());
        }
    }
}
