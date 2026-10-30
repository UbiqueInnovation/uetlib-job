package ch.ubique.uetlib.job.service.executor.runners;

import ch.ubique.uetlib.job.Job;
import ch.ubique.uetlib.job.model.JobRun;
import ch.ubique.uetlib.job.model.JobRunContext;
import ch.ubique.uetlib.job.model.JobRunState;
import ch.ubique.uetlib.job.model.properties.enums.JobRunnerKey;
import org.jspecify.annotations.Nullable;

public interface JobRunner {

    JobRunnerKey key();

    boolean submit(Job job, JobRunContext jobRunContext);

    void cancel(JobRun jobRun);

    @Nullable JobRunState resolveJobRunState(JobRun jobRun);
}
