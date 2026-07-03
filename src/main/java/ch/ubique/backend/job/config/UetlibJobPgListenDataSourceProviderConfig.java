package ch.ubique.backend.job.config;

import ch.ubique.backend.job.model.spring.PgListenDataSourceProvider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UetlibJobPgListenDataSourceProviderConfig {
    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public PgListenDataSourceProvider pgListenDataSourceProvider(
            final DataSourceProperties dataSourceProperties) {
        return () -> dataSourceProperties;
    }
}
