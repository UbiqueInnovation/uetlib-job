package ch.ubique.uetlib.job.model.entity;

import static java.util.Objects.requireNonNull;

import ch.ubique.uetlib.job.model.JobRunState;
import ch.ubique.uetlib.job.model.JobRunStateTransition;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Entity
@Table(name = "t_job_run_state_transition")
public class JobRunStateTransitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(
            name = "pk_job_run_state_transition_id",
            nullable = false,
            insertable = false,
            updatable = false)
    private @Nullable Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fk_job_run_id", nullable = false)
    private @Nullable JobRunEntity jobRun;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = Integer.MAX_VALUE)
    private @Nullable JobRunState state;

    @NotNull
    @PastOrPresent
    @Column(name = "transitioned_at", nullable = false, updatable = false)
    private @Nullable Instant transitionedAt;

    protected JobRunStateTransitionEntity() {}

    public JobRunStateTransitionEntity(
            final JobRunEntity jobRun, final JobRunState state, final Instant transitionedAt) {
        this.jobRun = jobRun;
        this.state = state;
        this.transitionedAt = transitionedAt;
    }

    public JobRunStateTransition toJobRunStateTransition() {
        return new JobRunStateTransition(
                requireNonNull(id),
                requireNonNull(jobRun).toJobRun(),
                requireNonNull(state),
                requireNonNull(transitionedAt));
    }

    public JobRunState state() {
        return requireNonNull(state);
    }
}
