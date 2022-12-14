package ch.ubique.backend.job.config;

import ch.ubique.backend.monitoring.service.MonitoringService;
import javax.validation.ValidationException;
import org.apache.logging.log4j.util.Strings;
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
            final @Value("${ch.ubique.backend.job.interface.heartbeat.enabled:true}") boolean
                            heartbeatEnabled,
            final @Value("${ch.ubique.backend.job.interface.heartbeat.endpoint:#{null}}") String
                            heartbeatEndpoint) {
        if (heartbeatEnabled && Strings.isBlank(heartbeatEndpoint)) {
            throw new ValidationException(
                    "property 'ch.ubique.backend.job.interface.heartbeat.endpoint' must be set if 'ch.ubique.backend.job.interface.heartbeat.enabled' is not explicitly disabled");
        }
        return new MonitoringService(restTemplate, heartbeatEnabled, heartbeatEndpoint);
    }
}
