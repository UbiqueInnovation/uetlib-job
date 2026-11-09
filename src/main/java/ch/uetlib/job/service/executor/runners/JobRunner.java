package ch.uetlib.job.service.executor.runners;

import ch.uetlib.job.Job;
import ch.uetlib.job.model.JobRun;
import ch.uetlib.job.model.JobRunContext;
import ch.uetlib.job.model.JobRunState;
import ch.uetlib.job.model.properties.enums.JobRunnerKey;

import org.jspecify.annotations.Nullable;

public interface JobRunner {

    JobRunnerKey key();

    boolean submit(Job job, JobRunContext jobRunContext);

    void cancel(JobRun jobRun);

    @Nullable JobRunState resolveJobRunState(JobRun jobRun);
}
