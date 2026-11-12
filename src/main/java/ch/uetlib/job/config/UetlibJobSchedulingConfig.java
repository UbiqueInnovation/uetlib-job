package ch.uetlib.job.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} for the whole library, unconditionally: every instance polls the queue
 * and runs the watchers. Not on {@code --job} processes.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Profile("!job-run")
public class UetlibJobSchedulingConfig {}
