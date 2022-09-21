package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.JobRunInput;

import javax.persistence.*;

@Entity
@Table(name = "t_job_run_input")
public class JobRunInputEntity implements JobRunInput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_run_input_id", nullable = false)
    private Integer id;

    @Column(name = "fk_job_run_id", nullable = false)
    private Integer runId;

    @Column(name = "input_name", nullable = false)
    private String name;

    @Column(name = "input_hash", nullable = false, length = 128)
    private String hash;

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public Integer getRunId() {
        return runId;
    }

    @Override
    public void setRunId(Integer runId) {
        this.runId = runId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getHash() {
        return hash;
    }

    @Override
    public void setHash(String hash) {
        this.hash = hash;
    }
}
