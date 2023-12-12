package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.converters.JobRunArgumentsConverter;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "t_job_run")
public class JobRunEntity {

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

    @Column(name = "job_run_arguments")
    @Convert(converter = JobRunArgumentsConverter.class)
    private JobRunArguments jobRunArguments;

    public JobRunEntity() {}

    public JobRunEntity(final JobRun jobRun) {
        id = jobRun.id();
        jobId = jobRun.jobId();
        jobVersion = jobRun.jobVersion();
        state = jobRun.state();
        startedAt = jobRun.startedAt();
        finishedAt = jobRun.finishedAt();
    }

    public JobRunEntity(
            final int jobId,
            final int jobVersion,
            final JobRunState state,
            final Instant startedAt,
            final Instant finishedAt) {
        this.jobId = jobId;
        this.jobVersion = jobVersion;
        this.state = state;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public JobRun toJobRun() {
        return new JobRun(id, jobId, jobVersion, startedAt, finishedAt, state);
    }
}
