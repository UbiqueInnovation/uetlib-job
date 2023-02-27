package ch.ubique.backend.job.cleanup;

import ch.ubique.backend.job.BaseJob;
import ch.ubique.backend.job.RegisteredJob;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.storage.JobRunStorage;
import org.springframework.context.ApplicationContext;

@RegisteredJob
public class CleanupJob extends BaseJob {
    private final JobRunStorage jobRunStorage;

    public CleanupJob(ApplicationContext context, JobRunStorage jobRunStorage) {
        super(context);
        this.jobRunStorage = jobRunStorage;
    }

    // TODO: add Cleanup properties
    /*
    params:
        - how many runs of a job to keep min (or time range, ...)
        - chunk size: how many runs to delete in one run
          --> depends on job sizes and whether remote storage is used
     */

    @Override
    protected JobRunState internalRun() throws Exception {

        // How to delete remotely stored files with minimal delay?

        /*
        - for all jobs:
            - we don't care abt 'older' runs (See issue comments) --> cleanup
            - cleanup: delete input files, then delete run from db
         */
        for (var job : jobDataService.findJobs()) {
            // TODO: Find job runs to be deleted - (max_num_of_runs, timestamp_from_which_to_keep)
            var latest_done = jobDataService.findLatestJobRunByState(job.id(), JobRunState.DONE);
        }
        // TODO: Cleanup input files
        // TODO: Run cleanup
        // a deletion would cascade: how to get num of deleted entries?
        return JobRunState.DONE;
    }
}
