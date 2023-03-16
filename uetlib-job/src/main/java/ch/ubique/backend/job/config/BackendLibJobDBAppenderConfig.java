package ch.ubique.backend.job.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.db.DBAppender;
import ch.qos.logback.core.db.DataSourceConnectionSource;
import ch.ubique.backend.job.logging.JobAppender;

import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class BackendLibJobDBAppenderConfig {

    @Bean
    public JobAppender jobAppender(final DataSource dataSource) {
        final var loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        final var connectionSource = new DataSourceConnectionSource();
        connectionSource.setDataSource(dataSource);
        connectionSource.setContext(loggerContext);
        connectionSource.start();

        final var dbAppender = new DBAppender();
        dbAppender.setName("jobs-db-appender");
        dbAppender.setConnectionSource(connectionSource);

        final var jobAppender = new JobAppender(dbAppender);
        jobAppender.start();

        final var logger = loggerContext.getLogger("ROOT");
        logger.addAppender(jobAppender);

        return jobAppender;
    }
}
