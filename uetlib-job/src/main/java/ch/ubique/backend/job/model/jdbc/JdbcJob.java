package ch.ubique.backend.job.model.jdbc;

import ch.ubique.backend.job.model.Job;

public class JdbcJob implements Job {

    private Integer id;

    private String name;

    private String clazz;

    private String cronExpression;

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
    public String getDatabaseCronExpression() {
        return cronExpression;
    }

    @Override
    public void setDatabaseCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }
}
