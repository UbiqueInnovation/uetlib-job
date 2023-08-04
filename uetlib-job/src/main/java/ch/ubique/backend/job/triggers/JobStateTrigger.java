package ch.ubique.backend.job.triggers;

import ch.ubique.backend.job.BaseJob;
import ch.ubique.backend.job.model.JobRunState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JobStateTrigger(
        @NotNull Class<? extends BaseJob> jobClass, @NotNull JobRunState state) implements Trigger {

    @Override
    @NotBlank
    public String type() {
        return "job_state";
    }
}
