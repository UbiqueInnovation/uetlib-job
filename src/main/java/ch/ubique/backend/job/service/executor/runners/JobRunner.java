package ch.ubique.backend.job.service.executor.runners;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface JobRunner {

    @NonNull JobRunnerKey key();

    boolean submit(@NonNull Job job, @NonNull JobRunContext jobRunContext);

    void cancel(@NonNull JobRun jobRun);

    @Nullable JobRunState resolveJobRunState(@NonNull JobRun jobRun);
}
