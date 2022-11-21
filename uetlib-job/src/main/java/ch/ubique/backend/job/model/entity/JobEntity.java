package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.Job;

import javax.persistence.*;

@Table(name = "t_job")
@Entity
public class JobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_id", nullable = false)
    private Integer id;

    @Column(name = "clazz", nullable = false)
    private String clazz;

    @Column(name = "cron_expression", nullable = false, length = 20)
    private String cronExpression;

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
