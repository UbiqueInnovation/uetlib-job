package ch.uetlib.job.model.spring;

import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;

/**
 * Supplies the datasource to {@code LISTEN} on for the job library's own internal queue
 * notification channel ({@code new_job_queued_run}), i.e. wherever {@code t_job_run_queue} is
 * persisted. Distinct from {@link PgListenDataSourceProvider}: in an application with more than one
 * datasource, job-defined {@code PG_LISTEN} triggers may listen on an entirely different database
 * than the one the job library's own tables live on, so overriding one must never affect the other.
 * Implement this as a Spring bean to override the default (the application's ambient/primary
 * datasource).
 */
public interface JobQueueDataSourceProvider {
    DataSourceProperties getDataSourceProperties();
}
