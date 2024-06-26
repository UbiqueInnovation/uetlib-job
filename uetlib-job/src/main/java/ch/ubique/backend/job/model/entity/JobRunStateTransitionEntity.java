package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.JobRunStateTransition;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.time.Instant;

@Entity
@Table(name = "t_job_run_state_transition")
public class JobRunStateTransitionEntity {

    @Id
    @Column(
            name = "pk_job_run_state_transition_id",
            nullable = false,
            insertable = false,
            updatable = false)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fk_job_run_id", nullable = false)
    private JobRunEntity jobRun;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = Integer.MAX_VALUE)
    private JobRunState state;

    @NotNull
    @PastOrPresent
    @Column(name = "transitioned_at", nullable = false, updatable = false)
    private Instant transitionedAt;

    protected JobRunStateTransitionEntity() {}

    public JobRunStateTransition toJobRunStateTransition() {
        return new JobRunStateTransition(id, jobRun.toJobRun(), state, transitionedAt);
    }
}
