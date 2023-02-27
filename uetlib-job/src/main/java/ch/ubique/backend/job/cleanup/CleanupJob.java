package ch.ubique.backend.job.cleanup;

import ch.ubique.backend.job.BaseJob;
import ch.ubique.backend.job.RegisteredJob;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.model.JobRunState;
import org.springframework.context.ApplicationContext;

@RegisteredJob
public class CleanupJob extends BaseJob {
    private final JobDataService jobDataService;
    public CleanupJob(ApplicationContext context, JobDataService jobDataService) {
        super(context);
        this.jobDataService = jobDataService;
    }

    // TODO: add Cleanup properties
    /*
    params:
        - how many runs of a job to keep min (or time range, ...)
     */

    @Override
    protected JobRunState internalRun() throws Exception {
        // TODO: Cleanup input files
        /*
        - for all jobs:
            - we don't care abt 'older' runs (See issue comments) --> cleanup
            - cleanup: delete input files, then delete run from db
         */
        // TODO: Run cleanup
        return JobRunState.DONE;
    }
}
