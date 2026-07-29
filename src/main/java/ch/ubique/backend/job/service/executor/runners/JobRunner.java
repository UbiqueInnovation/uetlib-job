package ch.ubique.backend.job.service.executor.runners;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;

import org.jspecify.annotations.Nullable;

public interface JobRunner {

    JobRunnerKey key();

    boolean submit(Job job, JobRunContext jobRunContext);

    void cancel(JobRun jobRun);

    @Nullable JobRunState resolveJobRunState(JobRun jobRun);
}
