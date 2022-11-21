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

    /**
     * This cron expression will be set if no cron expression is present in the database.
     * Returns null by default.
     * If this method returns blank, no initial cron expression will be set.
     */
    default String initialCronExpression() {
        return null;
    }

    /**
     * This cron expression will always be used and overrides the cron expression specified in the database.
     * Returns null by default.
     * If this method returns blank, the cron expression in the database will not be overwritten.
     */
    default String fixedCronExpression() {
        return null;
    }

    /**
     * The cron expression specified in the database. Will be used, if {@link Job#fixedCronExpression()} is blank.
     */
    String getDatabaseCronExpression();

    default String getCronExpression() {
        final var fixedCron = fixedCronExpression();
        if (StringUtils.isNotBlank(fixedCron)) {
            return fixedCron;
        }

        final var databaseCron = getDatabaseCronExpression();
        if (StringUtils.isNotBlank(databaseCron)) {
            return databaseCron;
        }

        final var initialCron = initialCronExpression();
        if (StringUtils.isNotBlank(initialCron)) {
            return initialCron;
        }

        return "";
    }

    void setDatabaseCronExpression(String cronExpression);

    default boolean isCronActive() {
        return StringUtils.isNotBlank(getCronExpression()) && isCronExpressionValid();
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

    default boolean isCronFixed() {
        return fixedCronExpression() != null;
    }
}
