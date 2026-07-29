package ch.ubique.backend.job.test.config;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Profile("postgres")
@Configuration
public class PostgresqlTestcontainerConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresDBContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
    }
}
