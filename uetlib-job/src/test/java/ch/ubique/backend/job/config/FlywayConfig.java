package ch.ubique.backend.job.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class FlywayConfig {
    @Bean
    Flyway flyway(
            final DataSource dataSource,
            @Value("${spring.flyway.locations:classpath:/db/migration/templates/{vendor}")
                    final String flywayLocations) {
        final var flyWay =
                Flyway.configure().dataSource(dataSource).locations(flywayLocations).load();
        flyWay.migrate();
        return flyWay;
    }
}
