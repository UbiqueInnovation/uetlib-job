package ch.uetlib.job.model.spring;

import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;

/**
 * Datasource to {@code LISTEN} on for {@code PG_LISTEN} triggers. Implement as a bean to override
 * the ambient default.
 */
public interface PgListenJobTriggerDataSourceProvider {
    DataSourceProperties getDataSourceProperties();
}
