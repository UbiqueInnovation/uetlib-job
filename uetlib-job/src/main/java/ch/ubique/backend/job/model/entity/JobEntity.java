package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.Job;
import ch.ubique.backend.job.model.JobPropertyKey;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import java.util.EnumMap;
import java.util.Map;

@Table(name = "t_job")
@Entity
public class JobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_id", nullable = false)
    private Integer id;

    @Column(name = "clazz", nullable = false)
    private String clazz;

    @Column(name = "cron_expression", length = 20)
    private String cronExpression;

    @ElementCollection
    @CollectionTable(name = "t_job_properties", joinColumns = @JoinColumn(name = "fk_job_id"))
    @MapKeyColumn(name = "key")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "value")
    private Map<JobPropertyKey, String> properties = new EnumMap<>(JobPropertyKey.class);

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getClazz() {
        return clazz;
    }

    public void setClazz(String clazz) {
        this.clazz = clazz;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public Job toJob() {
        return new Job(id, clazz, cronExpression);
    }

    public static JobEntity fromJob(Job job) {
        final var jobEntity = new JobEntity();
        jobEntity.id = job.id();
        jobEntity.clazz = job.clazzName();
        jobEntity.cronExpression = job.databaseCronExpression();
        return jobEntity;
    }
}
