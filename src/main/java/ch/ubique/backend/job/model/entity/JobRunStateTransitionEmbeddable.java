package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.JobRunState;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

@Embeddable
public class JobRunStateTransitionEmbeddable {

    @Column(
            name = "pk_job_run_state_transition_id",
            nullable = false,
            insertable = false,
            updatable = false)
    private @Nullable Integer id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = Integer.MAX_VALUE)
    private @Nullable JobRunState state;

    @NotNull
    @PastOrPresent
    @Column(name = "transitioned_at", nullable = false, updatable = false)
    private @Nullable Instant transitionedAt;

    protected JobRunStateTransitionEmbeddable() {}

    public JobRunStateTransitionEmbeddable(final JobRunState state, final Instant transitionedAt) {
        this.state = state;
        this.transitionedAt = transitionedAt;
    }

    public JobRunState state() {
        return Objects.requireNonNull(state);
    }
}
