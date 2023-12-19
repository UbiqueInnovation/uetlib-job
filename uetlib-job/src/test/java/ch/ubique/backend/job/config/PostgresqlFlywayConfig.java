package ch.ubique.backend.job.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

@Profile("!mysql")
@Configuration
public class PostgresqlFlywayConfig {
    @Bean
    public Flyway flyway(final DataSource dataSource) {
        final var flyWay =
                Flyway.configure()
                        .dataSource(dataSource)
                        .locations("classpath:/db/migration/templates/postgresql")
                        .load();
        flyWay.migrate();
        return flyWay;
    }
}
