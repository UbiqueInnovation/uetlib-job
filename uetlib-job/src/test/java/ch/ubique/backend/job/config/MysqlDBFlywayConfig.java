package ch.ubique.backend.job.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class MysqlDBFlywayConfig {
    @Bean
    public Flyway flyway(final DataSource dataSource) {
        final var flyWay =
                Flyway.configure()
                        .dataSource(dataSource)
                        .locations("classpath:/db/migration/templates/mysql")
                        .load();
        flyWay.migrate();
        return flyWay;
    }
}
