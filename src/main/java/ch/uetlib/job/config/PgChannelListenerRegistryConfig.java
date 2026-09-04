package ch.uetlib.job.config;

import ch.uetlib.job.model.spring.JobQueueDataSourceProvider;
import ch.uetlib.job.model.spring.PgListenJobTriggerDataSourceProvider;
import ch.uetlib.job.service.postgres.PgChannelListenerRegistry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Two separate {@link PgChannelListenerRegistry} beans, since job triggers and the queue may listen
 * on different datasources.
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
