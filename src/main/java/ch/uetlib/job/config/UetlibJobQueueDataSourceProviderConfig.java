package ch.uetlib.job.config;

import ch.uetlib.job.model.spring.JobQueueDataSourceProvider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defaults to whichever {@link JdbcConnectionDetails} is ambient - correct for the common
 * single-datasource case, where the job library's own tables live on the application's primary
 * datasource. An application with multiple datasources, where the job library's tables live
 * elsewhere, must supply its own {@link JobQueueDataSourceProvider} bean.
 */
@Configuration
public class UetlibJobQueueDataSourceProviderConfig {

    @Bean
    @ConditionalOnMissingBean
    public JobQueueDataSourceProvider jobQueueDataSourceProvider(
            final JdbcConnectionDetails jdbcConnectionDetails) {
        final var properties = new DataSourceProperties();
        properties.setUrl(jdbcConnectionDetails.getJdbcUrl());
        properties.setUsername(jdbcConnectionDetails.getUsername());
        properties.setPassword(jdbcConnectionDetails.getPassword());
        properties.setDriverClassName(jdbcConnectionDetails.getDriverClassName());
        return () -> properties;
    }
}
