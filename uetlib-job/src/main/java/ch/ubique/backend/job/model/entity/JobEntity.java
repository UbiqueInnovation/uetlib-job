package ch.ubique.backend.job.model.entity;

import ch.ubique.backend.job.model.Job;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Table(name = "t_job")
@Entity
public class JobEntity implements Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_job_id", nullable = false)
    private Integer id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "clazz", nullable = false)
    private String clazz;

    @Column(name = "cron_expression", nullable = false, length = 20)
    private String cronExpression;

    public static JobEntity fromJob(Job job) {
        if (job instanceof JobEntity jobEntity) {
            return jobEntity;
        }

        final var jobEntity = new JobEntity();
        jobEntity.id = job.getId();
        jobEntity.name = job.getName();
        jobEntity.clazz = job.getClazz();
        jobEntity.cronExpression = job.getCronExpression();
        return jobEntity;
    }

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
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
    public String getClazz() {
        return clazz;
    }

    @Override
    public void setClazz(String clazz) {
        this.clazz = clazz;
    }

    @Override
    public String getCronExpression() {
        return cronExpression;
    }

    @Override
    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }
}
