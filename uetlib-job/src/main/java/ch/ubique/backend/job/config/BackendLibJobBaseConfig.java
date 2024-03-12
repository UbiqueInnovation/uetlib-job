package ch.ubique.backend.job.config;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.service.core.ClassLoaderService;
import ch.ubique.backend.job.service.triggers.FileTriggerWatcher;
import ch.ubique.backend.monitoring.service.MonitoringService;

import jakarta.validation.ValidationException;

import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

@Configuration
@EnableRetry
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class BackendLibJobBaseConfig {

    @Bean
    @ConditionalOnMissingBean
    RestTemplate restTemplate(final RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder.build();
    }

    @Bean("jobService")
    @Profile("!job-run")
    JobService jobService(
            final JobDataService jobDataService,
            final ApplicationContext applicationContext,
            final ClassLoaderService classLoaderService) {
        final var jobService =
                new JobService(jobDataService, applicationContext, classLoaderService);
        jobService.init();
        return jobService;
    }

    @Bean("jobService")
    @Profile("job-run")
    JobService jobServiceForJobRun(
            final JobDataService jobDataService,
            final ApplicationContext applicationContext,
            final ClassLoaderService classLoaderService) {
        return new JobService(jobDataService, applicationContext, classLoaderService);
    }

    @Bean
    @Profile("!job-run")
    MonitoringService monitoringService(
            final RestTemplate restTemplate,
            @Value("${ch.ubique.backend.job.interface.heartbeat.enabled:true}")
                    final boolean heartbeatEnabled,
            @Value("${ch.ubique.backend.job.interface.heartbeat.endpoint:#{null}}")
                    final String heartbeatEndpoint) {
        if (heartbeatEnabled && Strings.isBlank(heartbeatEndpoint)) {
            throw new ValidationException(
                    "property 'ch.ubique.backend.job.interface.heartbeat.endpoint' must be set if"
                            + " 'ch.ubique.backend.job.interface.heartbeat.enabled' is not explicitly"
                            + " disabled");
        }
        return new MonitoringService(restTemplate, heartbeatEnabled, heartbeatEndpoint);
    }

    @Bean
    FileTriggerWatcher fileTriggerWatcher() throws IOException {
        return new FileTriggerWatcher();
    }
}
