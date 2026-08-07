package ch.ubique.backend.job.model.spring;

import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;

/**
 * Supplies the datasource to {@code LISTEN} on for job-defined {@link
 * ch.ubique.backend.job.model.triggers.PgListenTrigger}s - i.e. wherever the application emits the
 * {@code NOTIFY}s a job wants to react to. In an application with more than one datasource, this
 * can be a different database than the one the job library's own tables live on; see {@link
 * JobQueueDataSourceProvider} for that one. Implement this as a Spring bean to override the default
 * (the application's ambient/primary datasource).
 */
public interface PgListenJobTriggerDataSourceProvider {
    DataSourceProperties getDataSourceProperties();
}
