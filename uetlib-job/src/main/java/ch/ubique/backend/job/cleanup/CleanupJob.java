package ch.ubique.backend.job.cleanup;

import ch.ubique.backend.job.BaseJob;
import ch.ubique.backend.job.RegisteredJob;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.storage.JobRunStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

@RegisteredJob
public class CleanupJob extends BaseJob {

    private static final Logger logger = LoggerFactory.getLogger(CleanupJob.class);

    public CleanupJob(ApplicationContext context) {
        super(context);
    }

    // TODO: add Cleanup properties
    // TODO: add daysToKeep property [TOPO-2358]
    // TODO: add maxChunkSize property [TOPO-2358]

    @Override
    protected JobRunState internalRun() throws Exception {

        for (var job : jobDataService.findJobs()) {
            // TODO: delete input files [TOPO-2358]
            // TODO: set daysToKeep parameter based on properties [TOPO-2358]
            // TODO: delete jobs in batches of max maxChunkSize size [TOPO-2358]
            var numDeletions = jobDataService.cleanupJobRuns(job.id(), 0);
            logger.info(
                    "Cleaned runs for job [jobId: {}; num_deletions: {}]", job.id(), numDeletions);
        }

        return JobRunState.DONE;
    }
}
