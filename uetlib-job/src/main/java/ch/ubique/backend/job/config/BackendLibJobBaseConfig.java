package ch.ubique.backend.job.config;

import ch.ubique.backend.monitoring.service.MonitoringService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class BackendLibJobBaseConfig {

    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate(final RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder.build();
    }

    @Bean
    public MonitoringService monitoringService(
            final RestTemplate restTemplate,
            final @Value("${job.interface.heartBeatEndpoint:#{null}}") String heartBeatEndpoint) {
        return new MonitoringService(restTemplate, heartBeatEndpoint);
    }
}
