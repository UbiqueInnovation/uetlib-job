package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.Job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    public static JobEntity fromJob(final Job job) {
        final var jobEntity = new JobEntity();
        jobEntity.id = job.id();
        jobEntity.clazz = job.clazzName();
        jobEntity.cronExpression = job.databaseCronExpression();
        return jobEntity;
    }

    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public String getClazz() {
        return clazz;
    }

    public void setClazz(final String clazz) {
        this.clazz = clazz;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(final String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public Job toJob() {
        return new Job(id, clazz, cronExpression);
    }
}
