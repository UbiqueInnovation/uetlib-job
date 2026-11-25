package ch.uetlib.job.cleanup;

import ch.uetlib.job.model.JobClassAndId;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.storage.JobRunStorage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class JobRunInputCleaner {

    private static final Logger logger = LoggerFactory.getLogger(JobRunInputCleaner.class);

    private final JobRunStorage jobRunStorage;

    public JobRunInputCleaner(final JobRunStorage jobRunStorage) {
        this.jobRunStorage = jobRunStorage;
    }

    /** Attempts to delete all stored input files of a job run */
    @ConcurrencyLimit(limitString = "${ch.uetlib.job.cleanup.run-input-concurrency:32}")
    public void cleanInputFiles(final JobClassAndId job, final JobRun run) {
        try {
            // Clean all files in directory of job run
            final var dirDidExist =
                    jobRunStorage.deleteJobRunDirectory(job.name(), run.readableId());
            if (dirDidExist) {
                logger.debug(
                        "Deleted run input file directory [jobId: {}; runId: {}]",
                        job.id(),
                        run.id());
            } else {
                logger.debug(
                        "Failed to delete run input file directory: doesn't exist [jobId: {}; runId:"
                                + " {}]",
                        job.id(),
                        run.id());
            }
        } catch (final IOException e) {
            logger.info(
                    "Failed to delete run input files for job {} and run {}",
                    job.id(),
                    run.id(),
                    e);
        }
    }
}
