package ch.ubique.backend.job.triggers;

import ch.ubique.backend.job.Job;
import ch.ubique.backend.job.model.JobRunState;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JobStateTrigger(
        @NotNull Class<? extends Job> jobClass, @NotNull JobRunState state) implements Trigger {

    @Override
    @NotBlank
    public String type() {
        return "job_state";
    }
}
