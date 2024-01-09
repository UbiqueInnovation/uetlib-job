package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.core.file.ByteFile;
import ch.ubique.backend.job.model.JobRunInput;

import jakarta.persistence.*;

@Entity
@Table(name = "t_job_run_input")
public class JobRunInputEntity implements JobRunInput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_run_input_id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_job_run_id", nullable = false)
    private JobRunEntity jobRun;

    @Column(name = "input_name", nullable = false)
    private String name;

    @Column(name = "input_hash", nullable = false, length = 128)
    private String hash;

    public static JobRunInputEntity from(final JobRunEntity jobRun, final ByteFile file) {
        final var input = new JobRunInputEntity();
        input.setJobRun(jobRun);
        input.setName(file.getFileName());
        input.setHash(file.getFileHash());
        return input;
    }

    @Override
    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public JobRunEntity getJobRun() {
        return jobRun;
    }

    public void setJobRun(final JobRunEntity jobRun) {
        this.jobRun = jobRun;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public String getHash() {
        return hash;
    }

    public void setHash(final String hash) {
        this.hash = hash;
    }
}
