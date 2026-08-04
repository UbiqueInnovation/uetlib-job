package ch.uetlib.job.config;

import ch.uetlib.job.model.spring.JobQueueDataSourceProvider;
import ch.uetlib.job.model.spring.PgListenJobTriggerDataSourceProvider;
import ch.uetlib.job.service.postgres.PgChannelListenerRegistry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Produces the two {@link PgChannelListenerRegistry} beans as distinct, independently-datasourced
 * instances - never a single shared one - since {@link PgListenJobTriggerDataSourceProvider}
 * (job-defined {@code PG_LISTEN} triggers, application datasource) and {@link
 * JobQueueDataSourceProvider} (the job library's own internal queue notifications) can point at
 * entirely different databases in a multi-datasource application. Consumers select the one they
 * need by bean name.
 */
@Configuration
@Profile("!job-run")
public class PgChannelListenerRegistryConfig {

    public static final String JOB_TRIGGER_REGISTRY = "jobTriggerPgChannelListenerRegistry";
    public static final String JOB_QUEUE_REGISTRY = "jobQueuePgChannelListenerRegistry";

    @Bean(JOB_TRIGGER_REGISTRY)
    public PgChannelListenerRegistry jobTriggerPgChannelListenerRegistry(
            final PgListenJobTriggerDataSourceProvider pgListenJobTriggerDataSourceProvider) {
        return new PgChannelListenerRegistry(
                pgListenJobTriggerDataSourceProvider.getDataSourceProperties());
    }

    @Bean(JOB_QUEUE_REGISTRY)
    public PgChannelListenerRegistry jobQueuePgChannelListenerRegistry(
            final JobQueueDataSourceProvider jobQueueDataSourceProvider) {
        return new PgChannelListenerRegistry(jobQueueDataSourceProvider.getDataSourceProperties());
    }
}
