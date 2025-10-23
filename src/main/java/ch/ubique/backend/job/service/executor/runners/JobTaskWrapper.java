package ch.ubique.backend.job.service.executor.runners;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.service.JobService;

public class JobTaskWrapper implements Runnable {
    private final Job job;
    private final JobRunContext jobRunContext;
    private final JobService jobService;

    public JobTaskWrapper(Job job, JobRunContext jobRunContext, JobService jobService) {
        this.job = job;
        this.jobRunContext = jobRunContext;
        this.jobService = jobService;
    }

    @Override
    public void run() {
        JobRunnerHelper.run(job, jobService, jobRunContext);
    }

    public Job getJob() {
        return job;
    }

    public JobRunContext getJobRunContext() {
        return jobRunContext;
    }

    @Override
    public String toString() {
        return String.format(
                "JobTask[job=%s, jobRunId=%d]",
                job.getClass().getCanonicalName(), jobRunContext.jobRun().id());
    }
}
