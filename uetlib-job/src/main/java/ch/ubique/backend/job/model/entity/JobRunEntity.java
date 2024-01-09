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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_job_id", nullable = false)
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

    public JobRunEntity(final JobRun jobRun, final JobEntity job) {
        this.id = jobRun.id();
        this.job = job;
        this.jobVersion = jobRun.jobVersion();
        this.state = jobRun.state();
        this.startedAt = jobRun.startedAt();
        this.finishedAt = jobRun.finishedAt();
        this.jobRunArguments = jobRun.jobRunArguments();
    }

    public JobRunEntity(
            final JobEntity job,
            final int jobVersion,
            final JobRunState state,
            final Instant startedAt,
            final Instant finishedAt,
            final JobRunArguments jobRunArguments) {
        this.job = job;
        this.jobVersion = jobVersion;
        this.state = state;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.jobRunArguments = jobRunArguments;
    }

    public JobRun toJobRun() {
        return new JobRun(
                id, job.getId(), jobVersion, startedAt, finishedAt, state, jobRunArguments);
    }
}
