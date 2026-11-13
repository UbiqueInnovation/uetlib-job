package ch.uetlib.job.config;

import ch.uetlib.job.model.spring.PgListenJobTriggerDataSourceProvider;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;

/**
 * Defaults to the ambient datasource; apps whose {@code PG_LISTEN} triggers listen elsewhere supply
 * their own {@link PgListenJobTriggerDataSourceProvider}.
 */
@AutoConfiguration
public class UetlibJobPgListenDataSourceProviderConfig {

    // Built from JdbcConnectionDetails, not by autowiring DataSourceProperties: the latter stays
    // empty when the datasource comes from a JdbcConnectionDetails bean (e.g. a Testcontainers
    // @ServiceConnection), and determineUrl() then throws.
    @Bean
    @ConditionalOnMissingBean
    public PgListenJobTriggerDataSourceProvider pgListenJobTriggerDataSourceProvider(
            final JdbcConnectionDetails jdbcConnectionDetails) {
        final var properties = new DataSourceProperties();
        properties.setUrl(jdbcConnectionDetails.getJdbcUrl());
        properties.setUsername(jdbcConnectionDetails.getUsername());
        properties.setPassword(jdbcConnectionDetails.getPassword());
        properties.setDriverClassName(jdbcConnectionDetails.getDriverClassName());
        return () -> properties;
    }
}
