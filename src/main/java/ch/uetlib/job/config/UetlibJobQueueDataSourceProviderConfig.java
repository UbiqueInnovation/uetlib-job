package ch.uetlib.job.config;

import ch.uetlib.job.model.spring.JobQueueDataSourceProvider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defaults to the ambient datasource; apps whose job tables live elsewhere supply their own {@link
 * JobQueueDataSourceProvider}.
 */
@Configuration
public class UetlibJobQueueDataSourceProviderConfig {

    @Bean
    @ConditionalOnMissingBean
    public JobQueueDataSourceProvider jobQueueDataSourceProvider(
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
