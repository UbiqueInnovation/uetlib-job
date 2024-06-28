package ch.ubique.backend.job.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Profile("postgres")
@TestConfiguration
public class PostgresqlTestcontainerConfig extends BaseDatabaseTestConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresDBContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));
    }
}
