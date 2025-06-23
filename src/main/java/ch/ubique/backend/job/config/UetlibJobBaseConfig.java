package ch.ubique.backend.job.config;

import ch.ubique.backend.job.annotation.RegisteredJobDefinitions;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.service.core.ClassLoaderService;
import ch.ubique.backend.job.service.register.JobRegistrationService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableRetry
public class UetlibJobBaseConfig {

    @Bean
    @ConditionalOnMissingBean
    RestTemplate restTemplate(final RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder.build();
    }

    /** bean registeredJobDefinitions is registered programmatically in RegisteredJobRegistrar */
    @Bean
    @Profile("!job-run")
    JobRegistrationService jobRegistrationService(
            final JobDataService jobDataService,
            final ClassLoaderService classLoaderService,
            final RegisteredJobDefinitions registeredJobDefinitions) {
        final var jobRegistrationService =
                new JobRegistrationService(jobDataService, classLoaderService);
        jobRegistrationService.registerAllJobs(registeredJobDefinitions);
        return jobRegistrationService;
    }
}
