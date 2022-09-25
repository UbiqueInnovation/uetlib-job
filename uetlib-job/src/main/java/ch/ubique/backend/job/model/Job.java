package ch.ubique.backend.job.model;

import ch.ubique.backend.job.BaseInputJob;

import org.apache.commons.lang3.StringUtils;
import org.quartz.CronExpression;

public interface Job {

    Integer getId();

    void setId(Integer id);

    String getName();

    void setName(String name);

    String getClazz();

    void setClazz(String clazz);

    String getCronExpression();

    void setCronExpression(String cronExpression);

    default boolean isCronActive() {
        return !StringUtils.isBlank(getCronExpression()) && isCronExpressionValid();
    }

    default boolean isCronExpressionValid() {
        return StringUtils.isBlank(getCronExpression())
                || CronExpression.isValidExpression(getCronExpression());
    }

    default boolean isForceRunnable() {
        try {
            return BaseInputJob.class.isAssignableFrom(Class.forName(getClazz()));
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
