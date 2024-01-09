package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.JobClassAndId;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Table(name = "t_job")
@Entity
public class JobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_id", nullable = false)
    private Integer id;

    @Column(name = "clazz", nullable = false)
    private String canonicalClassName;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobRunEntity> jobRuns = new ArrayList<>();

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobPropertyEntity> jobProperties = new ArrayList<>();

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobTriggerEntity> jobTriggers = new ArrayList<>();

    public JobEntity() {}

    public JobEntity(final String canonicalClassName) {
        this.canonicalClassName = canonicalClassName;
    }

    public JobEntity(final JobClassAndId jobClassAndId) {
        this.id = jobClassAndId.id();
        this.canonicalClassName = jobClassAndId.canonicalClassName();
    }

    public Integer getId() {
        return id;
    }

    public JobClassAndId toRecord() {
        return new JobClassAndId(id, canonicalClassName);
    }
}
