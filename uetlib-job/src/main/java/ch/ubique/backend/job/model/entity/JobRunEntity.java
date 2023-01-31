package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "t_job_run")
public class JobRunEntity implements JobRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_run_id", nullable = false)
    private Integer id;

    @Column(name = "fk_job_id", nullable = false)
    private Integer jobId;

    @Column(name = "job_version", nullable = false)
    private Integer jobVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private JobRunState state;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Override
    public Instant getFinishedAt() {
        return finishedAt;
    }

    @Override
    public void setFinishedAt(final Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    @Override
    public Instant getStartedAt() {
        return startedAt;
    }

    @Override
    public void setStartedAt(final Instant startedAt) {
        this.startedAt = startedAt;
    }

    @Override
    public JobRunState getState() {
        return state;
    }

    @Override
    public void setState(final JobRunState state) {
        this.state = state;
    }

    @Override
    public Integer getJobId() {
        return jobId;
    }

    @Override
    public void setJobId(final Integer jobId) {
        this.jobId = jobId;
    }

    @Override
    public Integer getJobVersion() {
        return jobVersion;
    }

    @Override
    public void setJobVersion(Integer jobVersion) {
        this.jobVersion = jobVersion;
    }

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(final Integer id) {
        this.id = id;
    }
}
