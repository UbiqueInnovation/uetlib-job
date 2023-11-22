package ch.ubique.backend.job.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration
public class PostgresqlTestContainerConfig {
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresDBContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                .withReuse(true);
    }
}
