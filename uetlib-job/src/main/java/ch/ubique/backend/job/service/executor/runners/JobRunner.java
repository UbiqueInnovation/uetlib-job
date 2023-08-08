package ch.ubique.backend.job.service.executor.runners;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRunContext;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.properties.enums.JobRunnerKey;

public interface JobRunner {

    JobRunnerKey key();

    default boolean isAvailable() {
        return true;
    }

    JobRunState submit(Job job, JobRunContext context);
}
