package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.JobRun;
import ch.ubique.backend.job.model.JobRunArguments;
import ch.ubique.backend.job.model.JobRunState;
import ch.ubique.backend.job.model.converters.JobRunArgumentsToStringConverter;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "t_job_run")
public class JobRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_run_id", nullable = false)
    private Integer id;

    @Column(name = "fk_job_id", nullable = false)
    private Integer jobId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_job_id", nullable = false, updatable = false, insertable = false)
    private JobEntity job;

    @Column(name = "job_version", nullable = false)
    private Integer jobVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private JobRunState state;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JobRunArgumentsToStringConverter.class)
    @Column(name = "job_run_arguments", nullable = false)
    private JobRunArguments jobRunArguments;

    @OneToMany(mappedBy = "jobRun", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobRunInputEntity> jobRunInputs = new ArrayList<>();

    public JobRunEntity() {}

    public JobRunEntity(final JobRun jobRun) {
        id = jobRun.id();
        jobId = jobRun.jobId();
        jobVersion = jobRun.jobVersion();
        state = jobRun.state();
        startedAt = jobRun.startedAt();
        finishedAt = jobRun.finishedAt();
        jobRunArguments = jobRun.jobRunArguments();
    }

    public JobRunEntity(
            final int jobId,
            final int jobVersion,
            final JobRunState state,
            final Instant startedAt,
            final Instant finishedAt,
            final JobRunArguments jobRunArguments) {
        this.jobId = jobId;
        this.jobVersion = jobVersion;
        this.state = state;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.jobRunArguments = jobRunArguments;
    }

    public JobRun toJobRun() {
        return new JobRun(id, jobId, jobVersion, startedAt, finishedAt, state, jobRunArguments);
    }
}
