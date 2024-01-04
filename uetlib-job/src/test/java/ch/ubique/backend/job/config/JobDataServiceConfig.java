package ch.ubique.backend.job.config;

import ch.ubique.backend.job.data.service.JobDataService;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackageClasses = JobDataService.class)
public class JobDataServiceConfig {}
