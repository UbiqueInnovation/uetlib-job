package ch.ubique.backend.job.service.executor.runners;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;

import jakarta.validation.constraints.NotNull;

public interface JobRunner {

    @NotNull
    JobRunnerKey key();

    boolean submit(@NotNull Job job, @NotNull JobRunContext jobRunContext);

    void cancel(@NotNull JobRun jobRun);

    JobRunState isRunning(@NotNull JobRun jobRun);
}
