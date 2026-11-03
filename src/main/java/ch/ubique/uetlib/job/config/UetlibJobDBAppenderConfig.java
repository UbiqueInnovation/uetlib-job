package ch.ubique.uetlib.job.config;

import ch.qos.logback.classic.LoggerContext;
import ch.ubique.uetlib.job.data.service.JobDataService;
import ch.ubique.uetlib.job.logging.JobDBAppender;
import ch.ubique.uetlib.job.service.executor.runners.DedicatedJobRunner;
import ch.ubique.uetlib.job.service.logs.LogFormatterService;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UetlibJobDBAppenderConfig {

    @Bean
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    JobDBAppender jobAppender(
            final JobDataService jobDataService,
            final LogFormatterService logFormatterService,
            @Autowired(required = false) final DedicatedJobRunner dedicatedJobRunner) {
        final var loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        final var jobDBAppender =
                new JobDBAppender(jobDataService, logFormatterService, dedicatedJobRunner);

        jobDBAppender.setName("jobs-db-appender");
        jobDBAppender.start();

        final var logger = loggerContext.getLogger("ROOT");
        logger.addAppender(jobDBAppender);

        return jobDBAppender;
    }
}
