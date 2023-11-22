package ch.ubique.backend.job.config;

import ch.ubique.backend.job.data.repository.JobPropertyRepository;
import ch.ubique.backend.job.data.repository.JobRepository;
import ch.ubique.backend.job.data.repository.JobRunInputRepository;
import ch.ubique.backend.job.data.repository.JobRunRepository;
import ch.ubique.backend.job.data.repository.JobTriggerRepository;
import ch.ubique.backend.job.data.repository.LoggingEventRepository;
import ch.ubique.backend.job.data.service.JobDataService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JobDataServiceConfig {

    @Bean
    public JobDataService jobDataService(
            final JobRepository jobRepository,
            final JobTriggerRepository jobTriggerRepository,
            final JobPropertyRepository jobPropertyRepository,
            final JobRunRepository jobRunRepository,
            final JobRunInputRepository jobRunInputRepository,
            final LoggingEventRepository loggingEventRepository) {
        return new JobDataService(
                jobRepository,
                jobTriggerRepository,
                jobPropertyRepository,
                jobRunRepository,
                jobRunInputRepository,
                loggingEventRepository);
    }
}
