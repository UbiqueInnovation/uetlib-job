package ch.ubique.backend.job.config;

import ch.ubique.backend.job.annotation.RegisteredJobDefinitions;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.service.core.ClassLoaderService;
import ch.ubique.backend.job.service.register.JobRegistrationService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.resilience.annotation.EnableResilientMethods;

@Configuration
@EnableResilientMethods
public class UetlibJobBaseConfig {

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
