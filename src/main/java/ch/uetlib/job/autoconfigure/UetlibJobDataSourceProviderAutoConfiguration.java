package ch.uetlib.job.autoconfigure;

import ch.uetlib.job.model.spring.JobQueueDataSourceProvider;
import ch.uetlib.job.model.spring.PgListenJobTriggerDataSourceProvider;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;

/**
 * Defaults to the ambient datasource; apps whose job tables live elsewhere supply their own {@link
 * JobQueueDataSourceProvider}, apps whose {@code PG_LISTEN} triggers listen elsewhere their own
 * {@link PgListenJobTriggerDataSourceProvider}.
 */
@AutoConfiguration
public class UetlibJobDataSourceProviderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JobQueueDataSourceProvider jobQueueDataSourceProvider(
            @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
                    final JdbcConnectionDetails jdbcConnectionDetails) {
        final var properties = dataSourceProperties(jdbcConnectionDetails);
        return () -> properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public PgListenJobTriggerDataSourceProvider pgListenJobTriggerDataSourceProvider(
            @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
                    final JdbcConnectionDetails jdbcConnectionDetails) {
        final var properties = dataSourceProperties(jdbcConnectionDetails);
        return () -> properties;
    }

    // Built from JdbcConnectionDetails, not by autowiring DataSourceProperties: the latter stays
    // empty when the datasource comes from a JdbcConnectionDetails bean (e.g. a Testcontainers
    // @ServiceConnection), and determineUrl() then throws.
    private static DataSourceProperties dataSourceProperties(
            final JdbcConnectionDetails jdbcConnectionDetails) {
        final var properties = new DataSourceProperties();
        properties.setUrl(jdbcConnectionDetails.getJdbcUrl());
        properties.setUsername(jdbcConnectionDetails.getUsername());
        properties.setPassword(jdbcConnectionDetails.getPassword());
        properties.setDriverClassName(jdbcConnectionDetails.getDriverClassName());
        return properties;
    }
}
