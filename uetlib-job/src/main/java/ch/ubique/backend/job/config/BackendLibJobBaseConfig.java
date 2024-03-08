package ch.ubique.backend.job.config;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.service.core.ClassLoaderService;
import ch.ubique.backend.job.service.triggers.FileTriggerWatcher;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Bean
    JobService jobService(
            final JobDataService jobDataService,
            final ApplicationContext applicationContext,
            final ClassLoaderService classLoaderService) {
        final var jobService =
                new JobService(jobDataService, applicationContext, classLoaderService);
        jobService.init();
        return jobService;
    }

    @Bean
    FileTriggerWatcher fileTriggerWatcher() throws IOException {
        return new FileTriggerWatcher();
    }
}
