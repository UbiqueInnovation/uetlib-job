package ch.ubique.backend.job.triggers;

import ch.ubique.backend.job.BaseJob;
import ch.ubique.backend.job.model.JobRunState;

public record JobStateTrigger(Class<? extends BaseJob> jobClass, JobRunState state)
        implements Trigger {

    @Override
    public String type() {
        return "job_state";
    }
}
