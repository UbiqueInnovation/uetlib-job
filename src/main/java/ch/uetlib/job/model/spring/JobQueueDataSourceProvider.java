package ch.uetlib.job.model.spring;

import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;

/**
 * Datasource to {@code LISTEN} on for queue inserts, i.e. where {@code t_job_run_queue} lives.
 * Implement as a bean to override the ambient default.
 */
public interface JobQueueDataSourceProvider {
    DataSourceProperties getDataSourceProperties();
}
