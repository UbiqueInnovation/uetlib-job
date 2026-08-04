package ch.uetlib.job.config;

import ch.uetlib.job.model.spring.PgListenJobTriggerDataSourceProvider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UetlibJobPgListenDataSourceProviderConfig {

    /**
     * Built from {@link JdbcConnectionDetails} rather than autowiring {@link DataSourceProperties}
     * directly: {@code DataSourceProperties} is only populated from {@code spring.datasource.*}
     * properties, which stay empty when the datasource comes from a Testcontainers
     * {@code @ServiceConnection} (or any other {@link JdbcConnectionDetails} bean) instead - in
     * that case {@code DataSourceProperties#determineUrl()} has nothing to fall back to and throws.
     * {@code JdbcConnectionDetails} is the abstraction Spring Boot itself resolves uniformly for
     * both cases, so building the properties from it works everywhere.
     */
    @Bean
    @ConditionalOnMissingBean
    public PgListenJobTriggerDataSourceProvider pgListenDataSourceProvider(
            @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
                    final JdbcConnectionDetails jdbcConnectionDetails) {
        final var properties = new DataSourceProperties();
        properties.setUrl(jdbcConnectionDetails.getJdbcUrl());
        properties.setUsername(jdbcConnectionDetails.getUsername());
        properties.setPassword(jdbcConnectionDetails.getPassword());
        properties.setDriverClassName(jdbcConnectionDetails.getDriverClassName());
        return () -> properties;
    }
}
