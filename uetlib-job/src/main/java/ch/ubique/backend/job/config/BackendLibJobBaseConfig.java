package ch.ubique.backend.job.config;

import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.definitions.RegisteredJobDefinitions;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.service.core.ClassLoaderService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableRetry
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
            final ClassLoaderService classLoaderService,
            // bean registeredJobDefinitions is registered programmatically
            @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
                    final RegisteredJobDefinitions registeredJobDefinitions) {
        final var jobService =
                new JobService(
                        jobDataService,
                        applicationContext,
                        classLoaderService,
                        registeredJobDefinitions);
        jobService.init();
        return jobService;
    }

    @Bean("jobService")
    @Profile("job-run")
    JobService jobServiceForJobRun(
            final JobDataService jobDataService,
            final ApplicationContext applicationContext,
            final ClassLoaderService classLoaderService,
            // bean registeredJobDefinitions is registered programmatically
            @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
                    final RegisteredJobDefinitions registeredJobDefinitions) {
        return new JobService(
                jobDataService, applicationContext, classLoaderService, registeredJobDefinitions);
    }
}
