package ch.ubique.backend.job.config;

import ch.ubique.backend.monitoring.service.MonitoringService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

@Configuration
@Profile("!job-run")
@ConditionalOnProperty("ch.ubique.backend.job.interface.heartbeat.endpoint")
public class BackendLibJobMonitoringServiceConfig {

    @Bean
    MonitoringService monitoringService(
            final RestTemplate restTemplate,
            @Value("${ch.ubique.backend.job.interface.heartbeat.endpoint}")
                    final String heartbeatEndpoint) {
        return new MonitoringService(restTemplate, heartbeatEndpoint);
    }
}
