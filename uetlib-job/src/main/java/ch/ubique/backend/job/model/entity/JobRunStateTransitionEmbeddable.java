package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.JobRunState;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.time.Instant;

@Embeddable
public class JobRunStateTransitionEmbeddable {

    @Column(
            name = "pk_job_run_state_transition_id",
            nullable = false,
            insertable = false,
            updatable = false)
    private Integer id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = Integer.MAX_VALUE)
    private JobRunState state;

    @NotNull
    @PastOrPresent
    @Column(name = "transitioned_at", nullable = false, updatable = false)
    private Instant transitionedAt;

    @Column(name = "transitioned_by", nullable = true, updatable = false)
    private String transitionedBy;

    protected JobRunStateTransitionEmbeddable() {}

    public JobRunStateTransitionEmbeddable(
            final @NotNull JobRunState state,
            final @NotNull Instant transitionedAt,
            final String transitionedBy) {
        this.state = state;
        this.transitionedAt = transitionedAt;
        this.transitionedBy = transitionedBy;
    }

    public @NotNull JobRunState state() {
        return state;
    }
}
