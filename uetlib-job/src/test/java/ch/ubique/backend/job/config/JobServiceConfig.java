package ch.ubique.backend.job.config;

import ch.ubique.backend.job.annotation.EnableJobs;
import ch.ubique.backend.job.data.service.JobDataService;
import ch.ubique.backend.job.service.JobService;
import ch.ubique.backend.job.service.core.ClassLoaderService;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootConfiguration
@ComponentScan({"ch.ubique.backend.job.data.service", "ch.ubique.backend.job.service"})
@EnableJobs("ch.ubique.backend.job.testjob")
public class JobServiceConfig {
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
    TaskScheduler taskScheduler() {
        return new ThreadPoolTaskScheduler();
    }
}
