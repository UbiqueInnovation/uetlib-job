package ch.ubique.backend.job.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.db.DataSourceConnectionSource;
import ch.ubique.backend.job.logging.JobDBAppender;

import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class UetlibJobDBAppenderConfig {

    @Bean
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    JobDBAppender jobAppender(final DataSource dataSource) {
        final var loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        final var connectionSource = new DataSourceConnectionSource();
        connectionSource.setDataSource(dataSource);
        connectionSource.setContext(loggerContext);
        connectionSource.start();

        final var jobDBAppender = new JobDBAppender();
        jobDBAppender.setName("jobs-db-appender");
        jobDBAppender.setConnectionSource(connectionSource);
        jobDBAppender.start();

        final var logger = loggerContext.getLogger("ROOT");
        logger.addAppender(jobDBAppender);

        return jobDBAppender;
    }
}
