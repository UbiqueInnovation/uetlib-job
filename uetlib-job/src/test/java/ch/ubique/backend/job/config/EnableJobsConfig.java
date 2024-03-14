package ch.ubique.backend.job.config;

import ch.ubique.backend.job.annotation.EnableJobs;

import org.springframework.context.annotation.Configuration;

@Configuration
@EnableJobs(basePackages = "ch.ubique.backend.job")
public class EnableJobsConfig {}
