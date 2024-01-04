package ch.ubique.backend.job.config;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Profile("postgres")
@Configuration
public class PostgresqlTestcontainerConfig {
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresDBContainer() {
        //noinspection resource
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16")).withReuse(true);
    }
}
